package tk.netindev.zeronet.tunnel

import android.util.Log
import java.io.*
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class WebSocketConnection(private val socket: Socket) {
    private val TAG = "WebSocketConnection"
    
    private val inputStream: InputStream = socket.getInputStream()
    private val outputStream: OutputStream = socket.getOutputStream()
    private val isConnected = AtomicBoolean(false)
    
    fun connect() {
        if (isConnected.get()) {
            Log.d(TAG, "WebSocket already connected")
            return
        }
        
        try {
            // WebSocket connection is already established through the HTTP upgrade
            isConnected.set(true)
            Log.d(TAG, "WebSocket connection ready")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish WebSocket connection", e)
            throw e
        }
    }
    
    fun send(data: ByteArray) {
        if (!isConnected.get()) {
            throw IllegalStateException("WebSocket not connected")
        }
        
        try {
            // Send WebSocket frame
            val frame = createWebSocketFrame(data)
            outputStream.write(frame)
            outputStream.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send WebSocket data", e)
            throw e
        }
    }
    
    fun receive(): ByteArray? {
        if (!isConnected.get()) {
            return null
        }
        
        try {
            // Read WebSocket frame with timeout
            return readWebSocketFrameWithTimeout(1000)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to receive WebSocket data", e)
            return null
        }
    }
    
    private fun readWebSocketFrameWithTimeout(timeoutMs: Long): ByteArray? {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                val frame = readWebSocketFrame()
                if (frame != null && frame.isNotEmpty()) {
                    return frame
                }
            } catch (e: Exception) {
                Log.d(TAG, "No data available yet: ${e.message}")
            }
            Thread.sleep(50)
        }
        return null
    }
    
    private fun createWebSocketFrame(data: ByteArray): ByteArray {
        val frame = ByteArrayOutputStream()
        
        // FIN bit set, opcode 0x02 (binary)
        frame.write(0x82)
        
        // Payload length
        if (data.size < 126) {
            frame.write(data.size)
        } else if (data.size < 65536) {
            frame.write(126)
            frame.write((data.size shr 8) and 0xFF)
            frame.write(data.size and 0xFF)
        } else {
            frame.write(127)
            // 8-byte length (simplified for this implementation)
            for (i in 7 downTo 0) {
                frame.write(((data.size shr (i * 8)) and 0xFF))
            }
        }
        
        // Payload
        frame.write(data)
        
        return frame.toByteArray()
    }
    
    private fun readWebSocketFrame(): ByteArray? {
        // Read first byte
        val firstByte = inputStream.read()
        if (firstByte == -1) return null
        
        val fin = (firstByte and 0x80) != 0
        val opcode = firstByte and 0x0F
        
        // Read second byte
        val secondByte = inputStream.read()
        if (secondByte == -1) return null
        
        val masked = (secondByte and 0x80) != 0
        var payloadLength = secondByte and 0x7F
        
        // Extended payload length
        if (payloadLength == 126) {
            payloadLength = (inputStream.read() shl 8) or inputStream.read()
        } else if (payloadLength == 127) {
            // Skip 8-byte length for simplicity
            repeat(8) { inputStream.read() }
            payloadLength = 0 // Simplified
        }
        
        // Read masking key if present
        val maskingKey = if (masked) {
            ByteArray(4).apply {
                inputStream.read(this)
            }
        } else null
        
        // Read payload
        val payload = ByteArray(payloadLength)
        inputStream.read(payload)
        
        // Unmask if necessary
        if (masked && maskingKey != null) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor maskingKey[i % 4].toInt()).toByte()
            }
        }
        
        return payload
    }
    
    fun disconnect() {
        if (isConnected.getAndSet(false)) {
            try {
                // Send close frame
                val closeFrame = byteArrayOf(0x88.toByte(), 0x00.toByte())
                outputStream.write(closeFrame)
                outputStream.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending close frame", e)
            }
        }
    }
    
    fun isConnected(): Boolean = isConnected.get()
}
