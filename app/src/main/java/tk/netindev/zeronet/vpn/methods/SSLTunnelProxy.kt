package tk.netindev.zeronet.vpn.methods

import com.trilead.ssh2.ProxyData
import com.trilead.ssh2.crypto.Base64
import com.trilead.ssh2.transport.ClientServerHello
import com.trilead.ssh2.transport.TransportManager
import tk.netindev.zeronet.service.util.AppLog.d
import tk.netindev.zeronet.service.util.AppLog.i
import tk.netindev.zeronet.vpn.util.PayloadUtils
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * SSL/TLS tunnel proxy that wraps the connection in TLS with SNI support.
 *
 * The connection flow matches HTTP Injector's SSL/TLS mode:
 *
 * 1. TCP connect to the proxy/stunnel server
 * 2. TLS handshake **first** (with custom SNI for zero-rating)
 * 3. If a payload is configured, send it **inside** the encrypted TLS tunnel
 * 4. Read the proxy response **inside** TLS
 * 5. Return the SSL socket for SSH traffic
 *
 * The ISP sees encrypted TLS traffic to the SNI bug host (zero-rated).
 * The HTTP payload and SSH traffic are invisible inside the TLS layer.
 *
 * - **SSL Direct** (no payload): TCP → TLS (SNI) → SSH
 * - **SSL + Payload**: TCP → TLS (SNI) → HTTP payload → response → SSH
 */
class SSLTunnelProxy(
    proxyHost: String,
    proxyPort: Int,
    proxyUser: String?,
    proxyPass: String?,
    requestPayload: String?,
    sniHost: String?
) : ProxyData {
    private val proxyHost: String
    private val proxyPort: Int
    private val proxyUser: String?
    private val proxyPass: String?
    private val requestPayload: String?
    private val sniHost: String?

    private var socket: Socket? = null

    init {
        require(proxyPort >= 0) { "proxyPort must be non-negative" }
        this.proxyHost = proxyHost
        this.proxyPort = proxyPort
        this.proxyUser = proxyUser
        this.proxyPass = proxyPass
        this.requestPayload = requestPayload
        this.sniHost = sniHost
    }

    @Throws(IOException::class)
    override fun openConnection(
        hostname: String?, port: Int, connectTimeout: Int,
        readTimeout: Int
    ): Socket? {
        val plainSocket = Socket()

        val inetAddress = TransportManager.createInetAddress(this.proxyHost)

        i(TAG, "Connecting to ${this.proxyHost}:${this.proxyPort}")
        plainSocket.connect(InetSocketAddress(inetAddress, this.proxyPort), connectTimeout)
        plainSocket.soTimeout = readTimeout

        // Step 1: TLS handshake first (server expects TLS immediately on port 443)
        i(TAG, "Upgrading connection to TLS")
        val sslSocket = this.upgradeToSSL(plainSocket, hostname)

        // Step 2: If we have a payload, send it inside the TLS tunnel
        if (this.requestPayload != null && this.requestPayload.trim().isNotEmpty()) {
            i(TAG, "Sending HTTP payload inside TLS tunnel")
            this.sendPayloadAndReadResponse(sslSocket, hostname, port)
        }

        this.socket = sslSocket
        return sslSocket
    }

    /**
     * Wraps the plain TCP socket into an [SSLSocket] with SNI configured.
     */
    @Throws(IOException::class)
    private fun upgradeToSSL(plainSocket: Socket, hostname: String?): SSLSocket {
        val sslSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory

        // Determine the SNI hostname: prefer explicit sniHost, fall back to proxy host
        val effectiveSniHost = when {
            !this.sniHost.isNullOrBlank() -> this.sniHost
            !hostname.isNullOrBlank() -> hostname
            else -> this.proxyHost
        }

        val sslSocket = sslSocketFactory.createSocket(
            plainSocket,
            effectiveSniHost,
            plainSocket.port,
            true // autoClose the underlying socket
        ) as SSLSocket

        // Configure SNI
        val sslParams = sslSocket.sslParameters ?: SSLParameters()
        sslParams.serverNames = listOf(SNIHostName(effectiveSniHost))
        sslSocket.sslParameters = sslParams

        d(TAG, "TLS handshake with SNI: $effectiveSniHost")
        sslSocket.startHandshake()
        i(TAG, "TLS handshake completed, protocol: ${sslSocket.session.protocol}")

        return sslSocket
    }

    /**
     * Sends the HTTP payload and reads the proxy response, all through the
     * already-established TLS connection.
     *
     * Reads the status line, parses the HTTP status code, drains the remaining
     * response headers (up to the empty line), and validates the tunnel was
     * established. Does NOT read beyond the headers -- the next bytes on the
     * stream are the SSH server banner, which trilead needs to read itself.
     */
    @Throws(IOException::class)
    private fun sendPayloadAndReadResponse(sslSocket: SSLSocket, hostname: String?, port: Int) {
        val payload = this.getRequestPayload(hostname, port)
        val outputStream = sslSocket.getOutputStream()

        if (!PayloadUtils.injectSplitPayload(payload, outputStream)) {
            try {
                outputStream.write(payload.toByteArray(StandardCharsets.ISO_8859_1))
            } catch (_: UnsupportedEncodingException) {
                outputStream.write(payload.toByteArray())
            }
            outputStream.flush()
        }

        val buffer = ByteArray(1024)
        val inputStream = sslSocket.getInputStream()

        // Read the status line (e.g. "HTTP/1.1 101 Switching Protocols")
        val len = ClientServerHello.readLineRN(inputStream, buffer)
        val statusLine = String(buffer, 0, len, StandardCharsets.ISO_8859_1)
        i(TAG, "Proxy response: $statusLine")

        // Drain remaining response headers until the empty line (\r\n\r\n)
        // This consumes ONLY headers -- the SSH banner stays untouched.
        @Suppress("ControlFlowWithEmptyBody")
        while (ClientServerHello.readLineRN(inputStream, buffer) != 0) {
            // consume header lines
        }

        // Validate the HTTP status code
        val statusCode = parseHttpStatusCode(statusLine)
        if (statusCode in 100..299) {
            i(TAG, "Tunnel established (HTTP $statusCode)")
            return
        }

        throw IllegalStateException("Proxy tunnel failed inside TLS (HTTP $statusCode): $statusLine")
    }

    /**
     * Extracts the HTTP status code from a status line like "HTTP/1.1 200 OK".
     * Returns -1 if the line can't be parsed.
     */
    private fun parseHttpStatusCode(statusLine: String): Int {
        if (!statusLine.startsWith("HTTP/")) return -1
        val spaceIdx = statusLine.indexOf(' ')
        if (spaceIdx < 0 || spaceIdx + 4 > statusLine.length) return -1
        return try {
            statusLine.substring(spaceIdx + 1, spaceIdx + 4).toInt()
        } catch (_: NumberFormatException) {
            -1
        }
    }

    private fun getRequestPayload(hostname: String?, port: Int): String {
        if (this.requestPayload != null) {
            return PayloadUtils.formatCustomPayload(hostname!!, port, this.requestPayload)
        } else {
            val stringBuilder = StringBuilder()
            stringBuilder.append("CONNECT ")
            stringBuilder.append(hostname)
            stringBuilder.append(':')
            stringBuilder.append(port)
            stringBuilder.append(" HTTP/1.0\r\n")
            if (!(this.proxyUser == null || this.proxyPass == null)) {
                val encoded = Base64.encode(
                    (this.proxyUser + ":" + this.proxyPass).toByteArray(
                        StandardCharsets.ISO_8859_1
                    )
                )
                stringBuilder.append("Proxy-Authorization: Basic ")
                stringBuilder.append(encoded)
                stringBuilder.append("\r\n")
            }
            stringBuilder.append("\r\n")
            return stringBuilder.toString()
        }
    }

    override fun close() {
        if (this.socket == null) {
            return
        }
        try {
            this.socket!!.close()
        } catch (_: IOException) {
            // ignored
        }
    }

    companion object {
        const val TAG: String = "SSLTunnelProxy"
    }
}
