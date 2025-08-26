package tk.netindev.zeronet.tunnel

import android.util.Log
import com.jcraft.jsch.*
import java.io.*
import java.util.concurrent.atomic.AtomicBoolean

class SSHConnection(
    private val websocketConnection: WebSocketConnection,
    private val host: String,
    private val username: String,
    private val password: String
) {
    private val TAG = "SSHConnection"
    
    private var jsch: JSch? = null
    private var session: Session? = null
    private val isConnected = AtomicBoolean(false)
    
    fun connect() {
        if (isConnected.get()) {
            Log.d(TAG, "SSH already connected")
            return
        }
        
        try {
            jsch = JSch()
            
            // Create custom socket factory that uses WebSocket
            val socketFactory = object : com.jcraft.jsch.SocketFactory {
                override fun createSocket(host: String?, port: Int): java.net.Socket {
                    return WebSocketSocket(websocketConnection)
                }
                
                override fun getInputStream(socket: java.net.Socket?): InputStream {
                    return (socket as? WebSocketSocket)?.getInputStream() 
                        ?: throw IllegalStateException("Invalid socket type")
                }
                
                override fun getOutputStream(socket: java.net.Socket?): OutputStream {
                    return (socket as? WebSocketSocket)?.getOutputStream() 
                        ?: throw IllegalStateException("Invalid socket type")
                }
            }
            
            session = jsch?.getSession(username, host, 22)
            session?.setPassword(password)
            session?.setConfig("StrictHostKeyChecking", "no")
            session?.setSocketFactory(socketFactory)
            
            Log.d(TAG, "Connecting to SSH server: $host")
            session?.connect(30000)
            
            if (session?.isConnected == true) {
                isConnected.set(true)
                Log.d(TAG, "SSH connection established successfully")
            } else {
                throw Exception("Failed to establish SSH connection")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect SSH", e)
            throw e
        }
    }
    
    fun disconnect() {
        if (isConnected.getAndSet(false)) {
            try {
                session?.disconnect()
                Log.d(TAG, "SSH connection disconnected")
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting SSH", e)
            }
        }
    }
    
    fun isConnected(): Boolean = isConnected.get()
    
    fun getSession(): Session? = session
    
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
        
        override fun read(): Int {
            if (buffer == null || bufferIndex >= buffer!!.size) {
                buffer = wsConnection.receive()
                bufferIndex = 0
                if (buffer == null) return -1
            }
            return buffer!![bufferIndex++].toInt() and 0xFF
        }
        
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (buffer == null || bufferIndex >= buffer!!.size) {
                buffer = wsConnection.receive()
                bufferIndex = 0
                if (buffer == null) return -1
            }
            
            val available = buffer!!.size - bufferIndex
            val toRead = minOf(len, available)
            System.arraycopy(buffer!!, bufferIndex, b, off, toRead)
            bufferIndex += toRead
            
            return toRead
        }
    }
    
    // Custom OutputStream that writes to WebSocket
    private inner class WebSocketOutputStream(private val wsConnection: WebSocketConnection) : OutputStream() {
        private val buffer = ByteArrayOutputStream()
        
        override fun write(b: Int) {
            buffer.write(b)
            if (buffer.size() >= 1024) {
                flush()
            }
        }
        
        override fun write(b: ByteArray, off: Int, len: Int) {
            buffer.write(b, off, len)
            if (buffer.size() >= 1024) {
                flush()
            }
        }
        
        override fun flush() {
            if (buffer.size() > 0) {
                wsConnection.send(buffer.toByteArray())
                buffer.reset()
            }
        }
    }
}
