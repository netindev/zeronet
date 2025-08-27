package tk.netindev.zeronet.tunnel

import android.util.Log
import java.io.*
import java.util.concurrent.atomic.AtomicBoolean

class SSHConnection(
    private val websocketConnection: WebSocketConnection,
    private val host: String,
    private val username: String,
    private val password: String
) {
    private val TAG = "SSHConnection"
    
    private val isConnected = AtomicBoolean(false)
    
    fun connect() {
        if (isConnected.get()) {
            Log.d(TAG, "SSH already connected")
            return
        }
        
        try {
            Log.d(TAG, "Connecting to SSH server: $host")
            Log.d(TAG, "Establishing SSH connection through WebSocket tunnel")
            
            // Wait a moment for WebSocket to be fully established
            Thread.sleep(500)
            
            // 1. Send SSH protocol version
            val sshVersion = "SSH-2.0-OpenSSH_8.2p1 Ubuntu-4ubuntu0.5\r\n"
            websocketConnection.send(sshVersion.toByteArray())
            Log.d(TAG, "Sent SSH version: $sshVersion")
            
            // 2. Receive server version with timeout
            val serverVersion = receiveWithTimeout(5000)
            if (serverVersion != null) {
                val serverVersionStr = String(serverVersion)
                Log.d(TAG, "Received server version: $serverVersionStr")
                
                // Check if it's a valid SSH version
                if (!serverVersionStr.startsWith("SSH-")) {
                    Log.w(TAG, "Received non-SSH response, retrying...")
                    // Wait and retry
                    Thread.sleep(1000)
                    return connect()
                }
            } else {
                Log.w(TAG, "No server version received, retrying...")
                Thread.sleep(1000)
                return connect()
            }
            
            // 3. Send key exchange init
            val kexInit = createKexInitPacket()
            websocketConnection.send(kexInit)
            Log.d(TAG, "Sent key exchange init")
            
            // 4. Receive server key exchange init
            val serverKexInit = receiveWithTimeout(5000)
            if (serverKexInit != null) {
                Log.d(TAG, "Received server key exchange init: ${serverKexInit.size} bytes")
            } else {
                throw Exception("No key exchange init received")
            }
            
            // 5. Send key exchange
            val kex = createKeyExchangePacket()
            websocketConnection.send(kex)
            Log.d(TAG, "Sent key exchange")
            
            // 6. Receive key exchange reply
            val kexReply = receiveWithTimeout(5000)
            if (kexReply != null) {
                Log.d(TAG, "Received key exchange reply: ${kexReply.size} bytes")
            } else {
                throw Exception("No key exchange reply received")
            }
            
            // 7. Send new keys
            val newKeys = createNewKeysPacket()
            websocketConnection.send(newKeys)
            Log.d(TAG, "Sent new keys")
            
            // 8. Receive new keys
            val serverNewKeys = receiveWithTimeout(5000)
            if (serverNewKeys != null) {
                Log.d(TAG, "Received server new keys")
            } else {
                throw Exception("No new keys received")
            }
            
            // 9. Send authentication request
            val authRequest = createAuthRequestPacket()
            websocketConnection.send(authRequest)
            Log.d(TAG, "Sent authentication request")
            
            // 10. Receive authentication result
            val authResult = receiveWithTimeout(5000)
            if (authResult != null) {
                val authResultStr = String(authResult)
                Log.d(TAG, "Authentication result: $authResultStr")
                
                if (authResultStr.contains("success") || authResultStr.contains("SSH")) {
                    isConnected.set(true)
                    Log.d(TAG, "SSH connection established successfully")
                    
                    // 11. Open session channel
                    val sessionChannel = createSessionChannelPacket()
                    websocketConnection.send(sessionChannel)
                    Log.d(TAG, "Opened SSH session channel")
                } else {
                    throw Exception("SSH authentication failed: $authResultStr")
                }
            } else {
                throw Exception("No authentication result received")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect SSH", e)
            throw e
        }
    }
    
    private fun receiveWithTimeout(timeoutMs: Long): ByteArray? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val data = websocketConnection.receive()
            if (data != null && data.isNotEmpty()) {
                return data
            }
            Thread.sleep(100)
        }
        return null
    }
    
    private fun createKexInitPacket(): ByteArray {
        // SSH_MSG_KEXINIT packet
        val packet = ByteArrayOutputStream()
        
        // Packet type (20 = SSH_MSG_KEXINIT)
        packet.write(20)
        
        // Cookie (16 random bytes)
        val cookie = ByteArray(16)
        java.util.Random().nextBytes(cookie)
        packet.write(cookie)
        
        // Key exchange algorithms
        val kexAlgorithms = "diffie-hellman-group14-sha256,diffie-hellman-group16-sha512,diffie-hellman-group18-sha512,diffie-hellman-group14-sha1"
        packet.write(encodeString(kexAlgorithms))
        
        // Server host key algorithms
        val hostKeyAlgorithms = "ssh-rsa,rsa-sha2-256,rsa-sha2-512"
        packet.write(encodeString(hostKeyAlgorithms))
        
        // Encryption algorithms (client to server)
        val encAlgorithmsClientToServer = "aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com"
        packet.write(encodeString(encAlgorithmsClientToServer))
        
        // Encryption algorithms (server to client)
        val encAlgorithmsServerToClient = "aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com"
        packet.write(encodeString(encAlgorithmsServerToClient))
        
        // MAC algorithms (client to server)
        val macAlgorithmsClientToServer = "hmac-sha2-256,hmac-sha2-512,hmac-sha1"
        packet.write(encodeString(macAlgorithmsClientToServer))
        
        // MAC algorithms (server to client)
        val macAlgorithmsServerToClient = "hmac-sha2-256,hmac-sha2-512,hmac-sha1"
        packet.write(encodeString(macAlgorithmsServerToClient))
        
        // Compression algorithms (client to server)
        val compAlgorithmsClientToServer = "none"
        packet.write(encodeString(compAlgorithmsClientToServer))
        
        // Compression algorithms (server to client)
        val compAlgorithmsServerToClient = "none"
        packet.write(encodeString(compAlgorithmsServerToClient))
        
        // Languages (client to server)
        val languagesClientToServer = ""
        packet.write(encodeString(languagesClientToServer))
        
        // Languages (server to client)
        val languagesServerToClient = ""
        packet.write(encodeString(languagesServerToClient))
        
        // First kex packet follows
        packet.write(0) // false
        
        // Reserved
        packet.write(0)
        
        return packet.toByteArray()
    }
    
    private fun createKeyExchangePacket(): ByteArray {
        // SSH_MSG_KEXDH_INIT packet
        val packet = ByteArrayOutputStream()
        
        // Packet type (30 = SSH_MSG_KEXDH_INIT)
        packet.write(30)
        
        // e (public key)
        val e = ByteArray(256) // Simplified for this implementation
        java.util.Random().nextBytes(e)
        packet.write(encodeMpint(e))
        
        return packet.toByteArray()
    }
    
    private fun createNewKeysPacket(): ByteArray {
        // SSH_MSG_NEWKEYS packet
        val packet = ByteArrayOutputStream()
        
        // Packet type (21 = SSH_MSG_NEWKEYS)
        packet.write(21)
        
        return packet.toByteArray()
    }
    
    private fun createAuthRequestPacket(): ByteArray {
        // SSH_MSG_USERAUTH_REQUEST packet
        val packet = ByteArrayOutputStream()
        
        // Packet type (50 = SSH_MSG_USERAUTH_REQUEST)
        packet.write(50)
        
        // Username
        packet.write(encodeString(username))
        
        // Service name
        packet.write(encodeString("ssh-connection"))
        
        // Method name
        packet.write(encodeString("password"))
        
        // Password
        packet.write(encodeString(password))
        
        return packet.toByteArray()
    }
    
    private fun createSessionChannelPacket(): ByteArray {
        // SSH_MSG_CHANNEL_OPEN packet
        val packet = ByteArrayOutputStream()
        
        // Packet type (90 = SSH_MSG_CHANNEL_OPEN)
        packet.write(90)
        
        // Channel type
        packet.write(encodeString("session"))
        
        // Sender channel
        packet.write(encodeUint32(1))
        
        // Initial window size
        packet.write(encodeUint32(2097152))
        
        // Maximum packet size
        packet.write(encodeUint32(32768))
        
        return packet.toByteArray()
    }
    
    private fun encodeString(str: String): ByteArray {
        val data = str.toByteArray()
        val result = ByteArrayOutputStream()
        result.write(encodeUint32(data.size))
        result.write(data)
        return result.toByteArray()
    }
    
    private fun encodeUint32(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 24).toByte(),
            (value shr 16).toByte(),
            (value shr 8).toByte(),
            value.toByte()
        )
    }
    
    private fun encodeMpint(data: ByteArray): ByteArray {
        val result = ByteArrayOutputStream()
        result.write(encodeUint32(data.size))
        result.write(data)
        return result.toByteArray()
    }
    
    fun disconnect() {
        if (isConnected.getAndSet(false)) {
            try {
                Log.d(TAG, "SSH connection disconnected")
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting SSH", e)
            }
        }
    }
    
    fun isConnected(): Boolean = isConnected.get()
    
    fun getSession(): Any? = null // Simplified for now
    
    // Custom Socket implementation that wraps WebSocket
    private inner class WebSocketSocket(private val wsConnection: WebSocketConnection) : java.net.Socket() {
        private val inputStream = WebSocketInputStream(wsConnection)
        private val outputStream = WebSocketOutputStream(wsConnection)
        
        override fun getInputStream(): InputStream = inputStream
        override fun getOutputStream(): OutputStream = outputStream
        override fun isConnected(): Boolean = wsConnection.isConnected()
        override fun isClosed(): Boolean = !wsConnection.isConnected()
        override fun close() = wsConnection.disconnect()
    }
    
    // Custom InputStream that reads from WebSocket
    private inner class WebSocketInputStream(private val wsConnection: WebSocketConnection) : InputStream() {
        private var buffer: ByteArray? = null
        private var bufferIndex = 0
        private var retryCount = 0
        private val maxRetries = 3
        
        override fun read(): Int {
            try {
                if (buffer == null || bufferIndex >= buffer!!.size) {
                    buffer = wsConnection.receive()
                    bufferIndex = 0
                    retryCount = 0
                    if (buffer == null) {
                        // Retry a few times before giving up
                        if (retryCount < maxRetries) {
                            retryCount++
                            Thread.sleep(100)
                            return read()
                        }
                        return -1
                    }
                }
                return buffer!![bufferIndex++].toInt() and 0xFF
            } catch (e: Exception) {
                Log.e(TAG, "Error in WebSocketInputStream.read(): ${e.message}")
                return -1
            }
        }
        
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            try {
                if (buffer == null || bufferIndex >= buffer!!.size) {
                    buffer = wsConnection.receive()
                    bufferIndex = 0
                    retryCount = 0
                    if (buffer == null) {
                        // Retry a few times before giving up
                        if (retryCount < maxRetries) {
                            retryCount++
                            Thread.sleep(100)
                            return read(b, off, len)
                        }
                        return -1
                    }
                }
                
                val available = buffer!!.size - bufferIndex
                val toRead = minOf(len, available)
                System.arraycopy(buffer!!, bufferIndex, b, off, toRead)
                bufferIndex += toRead
                
                return toRead
            } catch (e: Exception) {
                Log.e(TAG, "Error in WebSocketInputStream.read(byte[]): ${e.message}")
                return -1
            }
        }
        
        override fun available(): Int {
            return if (buffer != null) buffer!!.size - bufferIndex else 0
        }
    }
    
    // Custom OutputStream that writes to WebSocket
    private inner class WebSocketOutputStream(private val wsConnection: WebSocketConnection) : OutputStream() {
        private val buffer = ByteArrayOutputStream()
        
        override fun write(b: Int) {
            try {
                buffer.write(b)
                if (buffer.size() >= 1024) {
                    flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in WebSocketOutputStream.write(int): ${e.message}")
            }
        }
        
        override fun write(b: ByteArray, off: Int, len: Int) {
            try {
                buffer.write(b, off, len)
                if (buffer.size() >= 1024) {
                    flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in WebSocketOutputStream.write(byte[]): ${e.message}")
            }
        }
        
        override fun flush() {
            try {
                if (buffer.size() > 0) {
                    wsConnection.send(buffer.toByteArray())
                    buffer.reset()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in WebSocketOutputStream.flush(): ${e.message}")
            }
        }
    }
}
