package tk.netindev.zeronet.tunnel

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import tk.netindev.zeronet.MainActivity
import tk.netindev.zeronet.R

class TunnelService : Service() {
    private val TAG = "TunnelService"
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "zeronet_tunnel"
        
        fun startService(context: Context) {
            val intent = Intent(context, TunnelService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, TunnelService::class.java)
            context.stopService(intent)
        }
    }
    
    private lateinit var tunnelManager: TunnelManager
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TunnelService created")
        
        tunnelManager = TunnelManager(this)
        createNotificationChannel()
        
        // Initialize VPN service
        Log.d(TAG, "Initializing VPN service")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "TunnelService started")
        
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TunnelService destroyed")
        
        tunnelManager.stopTunnel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ZeroNet Tunnel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ZeroNet SSH Tunnel Service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ZeroNet Tunnel")
            .setContentText("SSH tunnel is active")
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    fun getTunnelManager(): TunnelManager = tunnelManager
}
