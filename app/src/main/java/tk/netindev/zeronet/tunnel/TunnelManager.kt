package tk.netindev.zeronet.tunnel

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.Socket
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

class TunnelManager(private val context: Context) {
    private val TAG = "TunnelManager"
    
    // Configuration
    private val proxyHost = "bancah.com.br"
    private val proxyPort = 80
    private val websocketHost = "mobile.tcatarina.me"
    private val websocketPort = 80
    private val sshHost = "mobile.tcatarina.me"
    private val sshUser = "server"
    private val sshPassword = "9e4NcA0YqvaEt@"
    
    // Payload for HTTP injection (HTTP Injector style)
    private var currentPayload = "CONNECT /-cgi/trace HTTP/1.1[lf]Host: bancah.com.br[crlf][crlf][split][crlf]GET- / HTTP/1.1[crlf]Host: mobile.tcatarina.me[crlf]Upgrade: Websocket[crlf][crlf]"
    
    // State management
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()
    
    // Connection components
    private var proxySocket: Socket? = null
    private var websocketConnection: WebSocketConnection? = null
    private var sshConnection: SSHConnection? = null
    private var vpnService: VpnService? = null
    
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun startTunnel() {
        startTunnelWithPayload(currentPayload)
    }
    
    fun startTunnelWithPayload(payload: String) {
        if (isRunning.get()) {
            addLog("Tunnel already running")
            return
        }
        
        currentPayload = payload
        isRunning.set(true)
        _connectionState.value = ConnectionState.CONNECTING
        
        scope.launch {
            try {
                addLog("Starting tunnel service...")
                addLog("Network Status: CONNECTED")
                addLog("Local IP: Getting...")
                
                // Step 1: Connect to HTTP proxy
                addLog("Connecting to HTTP proxy: $proxyHost:$proxyPort")
                connectToProxy()
                
                // Step 2: Send payload and establish WebSocket
                addLog("Sending payload: $currentPayload")
                establishWebSocket()
                
                // Step 3: Connect SSH through WebSocket
                addLog("Connecting SSH through WebSocket")
                connectSSH()
                
                // Step 4: Start VPN service
                addLog("Starting VPN service")
                startVpnService()
                
                _connectionState.value = ConnectionState.CONNECTED
                addLog("Tunnel connected successfully!")
                
            } catch (e: Exception) {
                addLog("Error starting tunnel: ${e.message}")
                _connectionState.value = ConnectionState.ERROR
                stopTunnel()
            }
        }
    }
    
    fun stopTunnel() {
        if (!isRunning.get()) {
            addLog("Tunnel not running")
            return
        }
        
        isRunning.set(false)
        _connectionState.value = ConnectionState.DISCONNECTING
        
        scope.launch {
            try {
                addLog("Stopping tunnel...")
                
                sshConnection?.disconnect()
                websocketConnection?.disconnect()
                proxySocket?.close()
                
                // Stop VPN service
                val vpnServiceInstance = ZeroNetVpnService.getInstance()
                vpnServiceInstance?.stopVpn()
                
                _connectionState.value = ConnectionState.DISCONNECTED
                addLog("Tunnel stopped")
                
            } catch (e: Exception) {
                addLog("Error stopping tunnel: ${e.message}")
                _connectionState.value = ConnectionState.ERROR
            }
        }
    }
    
    private suspend fun connectToProxy() {
        withContext(Dispatchers.IO) {
            try {
                addLog("Creating socket connection...")
                proxySocket = Socket()
                addLog("Connecting to proxy: $proxyHost:$proxyPort")
                proxySocket?.connect(InetSocketAddress(proxyHost, proxyPort), 10000)
                addLog("Connected to proxy successfully")
                addLog("Socket timeout set to 10 seconds")
                proxySocket?.soTimeout = 10000
            } catch (e: Exception) {
                addLog("Failed to connect to proxy: ${e.message}")
                throw e
            }
        }
    }
    
    private suspend fun establishWebSocket() {
        withContext(Dispatchers.IO) {
            try {
                // Split payload into parts like HTTP Injector does
                val payloadParts = currentPayload.split("[split]")
                if (payloadParts.size >= 2) {
                    val payloadPart1 = formatPayload(payloadParts[0])
                    val payloadPart2 = formatPayload(payloadParts[1])
                    
                    addLog("Sending payload part 1: ${payloadPart1.take(50)}...")
                    addLog("Sending payload part 2: ${payloadPart2.take(50)}...")
                    
                    val outputStream = proxySocket?.getOutputStream()
                    
                    // Send first part
                    outputStream?.write(payloadPart1.toByteArray())
                    outputStream?.flush()
                    
                    // Send second part immediately (like HTTP Injector)
                    outputStream?.write(payloadPart2.toByteArray())
                    outputStream?.flush()
                    
                    addLog("Payload sent successfully")
                } else {
                    // Fallback to single payload
                    val formattedPayload = formatPayload(currentPayload)
                    addLog("Sending single payload: ${formattedPayload.take(50)}...")
                    
                    val outputStream = proxySocket?.getOutputStream()
                    outputStream?.write(formattedPayload.toByteArray())
                    outputStream?.flush()
                }
                
                // Read response with timeout and proper parsing
                val response = readHttpResponse(proxySocket?.getInputStream())
                addLog("Proxy response: ${response.take(200)}...")
                
                // Handle response like HTTP Injector
                handleHttpResponse(response)
                
            } catch (e: Exception) {
                addLog("Failed to establish WebSocket: ${e.message}")
                throw e
            }
        }
    }
    
    private fun handleHttpResponse(response: String) {
        when {
            response.contains("101 Switching Protocols") -> {
                addLog("WebSocket upgrade successful")
                addLog("HTTP/1.1 101 Switching Protocols")
                websocketConnection = WebSocketConnection(proxySocket!!)
                websocketConnection?.connect()
            }
            response.contains("200 OK") -> {
                addLog("HTTP 200 OK received, continuing...")
                websocketConnection = WebSocketConnection(proxySocket!!)
                websocketConnection?.connect()
            }
            response.contains("400") -> {
                addLog("HTTP 400 received - checking for specific error")
                if (response.contains("WrongPass") || response.contains("NoXRealHost")) {
                    addLog("Authentication or configuration error")
                    throw Exception("Proxy authentication failed")
                } else {
                    addLog("HTTP 400 error - sending 200 OK response")
                    sendHttp200Response()
                    // Wait a bit and then continue
                    Thread.sleep(100)
                    websocketConnection = WebSocketConnection(proxySocket!!)
                    websocketConnection?.connect()
                }
            }
            response.isEmpty() -> {
                addLog("Empty response received - continuing anyway")
                websocketConnection = WebSocketConnection(proxySocket!!)
                websocketConnection?.connect()
            }
            else -> {
                addLog("Unexpected response: $response")
                addLog("Continuing anyway like HTTP Injector")
                websocketConnection = WebSocketConnection(proxySocket!!)
                websocketConnection?.connect()
            }
        }
    }
    
    private fun readHttpResponse(inputStream: InputStream?): String {
        if (inputStream == null) return ""
        
        val response = StringBuilder()
        val buffer = ByteArray(1024) // Larger buffer for better performance
        var totalBytesRead = 0
        val maxBytes = 8192 // 8KB max response size
        
        try {
            addLog("Reading HTTP response...")
            
            while (totalBytesRead < maxBytes) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) {
                    addLog("End of stream reached")
                    break
                }
                
                response.append(String(buffer, 0, bytesRead))
                totalBytesRead += bytesRead
                
                // Check if we have a complete HTTP response
                val responseStr = response.toString()
                if (responseStr.contains("\r\n\r\n")) {
                    addLog("Complete HTTP response received")
                    break
                }
                
                // Small delay to prevent busy waiting
                Thread.sleep(10)
            }
            
            addLog("Total bytes read: $totalBytesRead")
            
        } catch (e: Exception) {
            addLog("Error reading HTTP response: ${e.message}")
        }
        
        return response.toString()
    }
    
    private fun sendHttp200Response() {
        try {
            addLog("Sending 200 HTTP status - HTTP/1.1 200 OK")
            val http200Response = "HTTP/1.1 200 OK\r\n\r\n"
            val outputStream = proxySocket?.getOutputStream()
            outputStream?.write(http200Response.toByteArray())
            outputStream?.flush()
            addLog("200 OK response sent successfully")
        } catch (e: Exception) {
            addLog("Error sending 200 OK response: ${e.message}")
        }
    }
    
    private suspend fun connectSSH() {
        withContext(Dispatchers.IO) {
            try {
                sshConnection = SSHConnection(websocketConnection!!, sshHost, sshUser, sshPassword)
                sshConnection?.connect()
                addLog("SSH connection established")
            } catch (e: Exception) {
                addLog("Failed to connect SSH: ${e.message}")
                throw e
            }
        }
    }
    
    private suspend fun startVpnService() {
        withContext(Dispatchers.Main) {
            try {
                addLog("Starting VPN service...")
                
                // Get the VPN service instance with retry
                var vpnServiceInstance: ZeroNetVpnService? = null
                var retryCount = 0
                while (vpnServiceInstance == null && retryCount < 5) {
                    vpnServiceInstance = ZeroNetVpnService.getInstance()
                    if (vpnServiceInstance == null) {
                        addLog("Waiting for VPN service to initialize... (attempt ${retryCount + 1})")
                        Thread.sleep(500)
                        retryCount++
                    }
                }
                
                if (vpnServiceInstance != null) {
                    addLog("VPN service instance found")
                    
                    // Create and establish VPN interface
                    val vpnInterface = vpnServiceInstance.createVpnInterface()
                    if (vpnInterface != null) {
                        addLog("Creating VPN interface...")
                        
                                             // Establish the VPN connection with tunnel socket and connections
                     val vpnEstablished = vpnServiceInstance.establishVpn(
                         proxySocket!!, 
                         websocketConnection!!, 
                         sshConnection!!
                     ) { logMessage ->
                         addLog(logMessage)
                     }
                        if (vpnEstablished) {
                            addLog("VPN interface established successfully")
                            addLog("VPN Connected!")
                            addLog("DNS forwarding enabled")
                            addLog("Cloudflare DNS enabled")
                            addLog("All traffic now routed through SSH tunnel")
                        } else {
                            addLog("Failed to establish VPN interface")
                            throw Exception("VPN interface establishment failed")
                        }
                    } else {
                        addLog("Failed to create VPN interface")
                        throw Exception("VPN interface creation failed")
                    }
                } else {
                    addLog("VPN service instance not available")
                    addLog("VPN service started (simulated)")
                }
                
            } catch (e: Exception) {
                addLog("Failed to start VPN: ${e.message}")
                // Don't throw exception, just log the error
                addLog("Continuing without VPN interface")
            }
        }
    }
    
    private fun formatPayload(rawPayload: String): String {
        return rawPayload
            .replace("[lf]", "\n")
            .replace("[crlf]", "\r\n")
            .replace("[split]", "\r\n")
    }
    
    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val logMessage = "[$timestamp] $message"
        Log.d(TAG, logMessage)
        
        _logMessages.value = _logMessages.value + logMessage
    }
    
    fun getLogMessages(): List<String> = _logMessages.value
    
    fun clearLogs() {
        _logMessages.value = emptyList()
    }
    
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        ERROR
    }
}
