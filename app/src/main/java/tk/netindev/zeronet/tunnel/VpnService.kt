package tk.netindev.zeronet.tunnel

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class VpnService(private val context: Context) {
    private val TAG = "VpnService"
    
    private var vpnInterface: android.net.VpnService.Builder? = null
    private var vpnService: android.net.VpnService? = null
    private val isRunning = AtomicBoolean(false)
    
    fun startVpn() {
        if (isRunning.get()) {
            Log.d(TAG, "VPN already running")
            return
        }
        
        try {
            // This would typically require user interaction to establish VPN
            // For now, we'll simulate the VPN setup
            Log.d(TAG, "VPN service started (simulated)")
            isRunning.set(true)
            
            // In a real implementation, you would:
            // 1. Request VPN permissions
            // 2. Create VPN interface
            // 3. Route traffic through SSH tunnel
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            throw e
        }
    }
    
    fun stopVpn() {
        if (isRunning.getAndSet(false)) {
            try {
                vpnInterface?.let { builder ->
                    // Close VPN interface
                }
                Log.d(TAG, "VPN service stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping VPN", e)
            }
        }
    }
    
    fun isRunning(): Boolean = isRunning.get()
    
    // Helper method to protect socket from VPN routing
    fun protect(socket: Socket) {
        try {
            vpnService?.protect(socket)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to protect socket", e)
        }
    }
}

// Extension class for actual VPN service implementation
class ZeroNetVpnService : android.net.VpnService() {
    private val TAG = "ZeroNetVpnService"
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnTunnel: VpnTunnel? = null
    private var tunnelSocket: Socket? = null
    
    companion object {
        private var instance: ZeroNetVpnService? = null
        
        fun getInstance(): ZeroNetVpnService? = instance
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "ZeroNet VPN Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "ZeroNet VPN Service started with startId: $startId")
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "ZeroNet VPN Service destroyed")
    }
    

    
    fun createVpnInterface(): android.net.VpnService.Builder? {
        return try {
            Builder()
                .setSession("ZeroNet")
                .addAddress("10.0.0.2", 32)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)
                .setBlocking(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating VPN interface: ${e.message}")
            null
        }
    }
    
    fun establishVpn(
        tunnelSocket: Socket, 
        websocketConnection: WebSocketConnection, 
        sshConnection: SSHConnection, 
        onLogMessage: (String) -> Unit
    ): Boolean {
        return try {
            this.tunnelSocket = tunnelSocket
            
            val builder = createVpnInterface()
            if (builder != null) {
                vpnInterface = builder.establish()
                if (vpnInterface != null) {
                    Log.d(TAG, "VPN interface established successfully")
                    
                    // Start VPN tunnel routing with SSH and WebSocket connections
                    vpnTunnel = VpnTunnel(vpnInterface!!, tunnelSocket, websocketConnection, sshConnection, onLogMessage)
                    vpnTunnel?.start()
                    
                    true
                } else {
                    Log.e(TAG, "Failed to establish VPN interface")
                    false
                }
            } else {
                Log.e(TAG, "Failed to create VPN builder")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error establishing VPN: ${e.message}")
            false
        }
    }
    
    fun stopVpn() {
        try {
            vpnTunnel?.stop()
            vpnTunnel = null
            
            vpnInterface?.close()
            vpnInterface = null
            
            tunnelSocket?.close()
            tunnelSocket = null
            
            Log.d(TAG, "VPN interface closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN: ${e.message}")
        }
    }
}
