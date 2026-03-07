package tk.netindev.zeronet.vpn.methods

import com.trilead.ssh2.ProxyData
import tk.netindev.zeronet.service.util.AppLog.d
import tk.netindev.zeronet.service.util.AppLog.i
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * ProxyData implementation that routes a trilead-ssh2 Connection through a SOCKS5 proxy.
 * Used to chain SSH through DNSTT's local SOCKS5 proxy.
 */
class Socks5ProxyData(
    private val proxyHost: String,
    private val proxyPort: Int
) : ProxyData {

    private var socket: Socket? = null

    @Throws(IOException::class)
    override fun openConnection(
        hostname: String?,
        port: Int,
        connectTimeout: Int,
        readTimeout: Int
    ): Socket {
        i(TAG, "Connecting to SOCKS5 proxy $proxyHost:$proxyPort for $hostname:$port")

        val sock = Socket()
        sock.connect(InetSocketAddress(proxyHost, proxyPort), connectTimeout)
        sock.soTimeout = readTimeout
        this.socket = sock

        val output = sock.getOutputStream()
        val input = sock.getInputStream()

        // SOCKS5 greeting: version 5, 1 auth method (no auth)
        output.write(byteArrayOf(0x05, 0x01, 0x00))
        output.flush()

        // Read auth response
        val authResponse = ByteArray(2)
        val authRead = readFully(input, authResponse, 2)
        d(TAG, "SOCKS5 auth response: read=$authRead, bytes=[${authResponse[0]}, ${authResponse[1]}]")
        if (authRead != 2 || authResponse[0] != 0x05.toByte() || authResponse[1] != 0x00.toByte()) {
            sock.close()
            throw IOException("SOCKS5 auth negotiation failed (read=$authRead, ver=${authResponse[0]}, method=${authResponse[1]})")
        }

        // SOCKS5 CONNECT request
        val host = hostname ?: throw IOException("hostname is null")
        val connectRequest = ByteArray(7 + host.length)
        connectRequest[0] = 0x05 // version
        connectRequest[1] = 0x01 // CONNECT
        connectRequest[2] = 0x00 // reserved
        connectRequest[3] = 0x03 // domain name address type
        connectRequest[4] = host.length.toByte()
        System.arraycopy(host.toByteArray(Charsets.US_ASCII), 0, connectRequest, 5, host.length)
        connectRequest[5 + host.length] = ((port shr 8) and 0xFF).toByte()
        connectRequest[6 + host.length] = (port and 0xFF).toByte()

        output.write(connectRequest)
        output.flush()

        // Read CONNECT response header (4 bytes: ver, status, rsv, atyp)
        val responseHeader = ByteArray(4)
        val headerRead = readFully(input, responseHeader, 4)
        d(TAG, "SOCKS5 CONNECT response: read=$headerRead, bytes=[${responseHeader[0]}, ${responseHeader[1]}, ${responseHeader[2]}, ${responseHeader[3]}]")
        if (headerRead != 4) {
            sock.close()
            throw IOException("SOCKS5 response too short (read=$headerRead)")
        }
        if (responseHeader[0] != 0x05.toByte()) {
            sock.close()
            throw IOException("SOCKS5 response has wrong version: ${responseHeader[0]}")
        }
        if (responseHeader[1] != 0x00.toByte()) {
            val errorMsg = when (responseHeader[1].toInt() and 0xFF) {
                0x01 -> "general SOCKS server failure"
                0x02 -> "connection not allowed by ruleset"
                0x03 -> "network unreachable"
                0x04 -> "host unreachable"
                0x05 -> "connection refused"
                0x06 -> "TTL expired"
                0x07 -> "command not supported"
                0x08 -> "address type not supported"
                else -> "unknown error"
            }
            sock.close()
            throw IOException("SOCKS5 CONNECT failed: $errorMsg (status=${responseHeader[1]})")
        }

        // Consume the rest of the response (bound address + port)
        when (responseHeader[3]) {
            0x01.toByte() -> readFully(input, ByteArray(6), 6)   // IPv4 (4) + port (2)
            0x03.toByte() -> {                                     // Domain
                val domainLen = input.read()
                if (domainLen < 0) throw IOException("SOCKS5 unexpected EOF reading domain length")
                readFully(input, ByteArray(domainLen + 2), domainLen + 2)
            }
            0x04.toByte() -> readFully(input, ByteArray(18), 18) // IPv6 (16) + port (2)
        }

        d(TAG, "SOCKS5 tunnel established to $hostname:$port")
        return sock
    }

    override fun close() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray, length: Int): Int {
        var totalRead = 0
        while (totalRead < length) {
            val read = input.read(buffer, totalRead, length - totalRead)
            if (read < 0) break
            totalRead += read
        }
        return totalRead
    }

    companion object {
        private const val TAG = "Socks5ProxyData"
    }
}
