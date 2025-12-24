package tk.netindev.zeronet.vpn.methods

import com.trilead.ssh2.ProxyData
import com.trilead.ssh2.crypto.Base64
import com.trilead.ssh2.sftp.Packet
import com.trilead.ssh2.transport.ClientServerHello
import com.trilead.ssh2.transport.TransportManager
import tk.netindev.zeronet.service.util.AppLog.i
import tk.netindev.zeronet.vpn.util.PayloadUtils
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

class HttpSSHProxy
    (
    proxyHost: String, proxyPort: Int, proxyUser: String?,
    proxyPass: String?, requestPayload: String?
) : ProxyData {
    private val proxyHost: String
    private val proxyPass: String?
    private val proxyPort: Int
    private val proxyUser: String?
    private val requestPayload: String?

    private var socket: Socket? = null

    init {
        require(proxyPort >= 0) { "proxyPort must be non-negative" }
        this.proxyHost = proxyHost
        this.proxyPort = proxyPort
        this.proxyUser = proxyUser
        this.proxyPass = proxyPass
        this.requestPayload = requestPayload
    }

    @Throws(IOException::class)
    override fun openConnection(
        hostname: String?, port: Int, connectTimeout: Int,
        readTimeout: Int
    ): Socket? {
        this.socket = Socket()

        val inetAddress = TransportManager.createInetAddress(this.proxyHost)

        this.socket!!.connect(InetSocketAddress(inetAddress, this.proxyPort), connectTimeout)
        this.socket!!.setSoTimeout(readTimeout)

        val requestPayload = this.getRequestPayload(hostname, port)

        val outputStream = this.socket!!.getOutputStream()

        if (!PayloadUtils.injectSplitPayload(requestPayload, outputStream)) {
            try {
                outputStream.write(requestPayload.toByteArray(StandardCharsets.ISO_8859_1))
            } catch (_: UnsupportedEncodingException) {
                outputStream.write(requestPayload.toByteArray())
            }
            outputStream.flush()
        }

        val buffer = ByteArray(1024)
        val inputStream = this.socket!!.getInputStream()

        var len = ClientServerHello.readLineRN(inputStream, buffer)

        String(buffer, 0, len, StandardCharsets.ISO_8859_1) // FIRST TIME READ!

        val firstLongRead = StringBuilder()
        while ((ClientServerHello.readLineRN(inputStream, buffer).also { len = it }) != 0) {
            firstLongRead.append("\n")
            firstLongRead.append(String(buffer, 0, len, StandardCharsets.ISO_8859_1))
        }

        i(TAG, "Trying to push socket forward first time")
        if (this.tryForward(firstLongRead.toString())) {
            return socket
        }

        val secondLongRead = StringBuilder()
        while ((ClientServerHello.readLineRN(inputStream, buffer).also { len = it }) != 0) {
            secondLongRead.append("\n")
            secondLongRead.append(String(buffer, 0, len, StandardCharsets.ISO_8859_1))
        }

        i(TAG, "Trying to push socket forward second time")
        if (this.tryForward(secondLongRead.toString())) {
            return socket
        }
        throw IllegalStateException("Proxy connection error, can't push socket forward")
    }

    private fun tryForward(string: String): Boolean {
        if (string.contains("101 Switching Protocols") || string.contains("Content-Length: ")) {
            return true
        }
        if (!string.startsWith("HTTP/")) {
            return false
        } else if (string.length >= 14 && string[8] == ' ' && string[12] == ' ') {
            try {
                val errorCode = string.substring(9, 12).toInt()
                return if (errorCode !in 0..999) {
                    false
                } else {
                    errorCode == Packet.SSH_FXP_EXTENDED
                }
            } catch (_: NumberFormatException) {
                return false
            }
        } else {
            return false
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
        const val TAG: String = "HttpSSHProxy"
    }
}
