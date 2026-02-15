package tk.netindev.zeronet.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tk.netindev.zeronet.MainActivity
import tk.netindev.zeronet.R
import tk.netindev.zeronet.notification.NotificationActionReceiver
import tk.netindev.zeronet.service.config.Settings
import tk.netindev.zeronet.service.util.AppLog.d
import tk.netindev.zeronet.service.util.AppLog.e
import tk.netindev.zeronet.service.util.AppLog.i
import tk.netindev.zeronet.service.util.AppLog.w
import tk.netindev.zeronet.service.util.ConnectionStatus
import tk.netindev.zeronet.service.util.ConnectionStatusManager
import tk.netindev.zeronet.service.util.ConnectionStatsManager
import tk.netindev.zeronet.service.util.ConnectionStatusManager.setStatus
import tk.netindev.zeronet.service.util.SpeedMonitor
import tk.netindev.zeronet.vpn.tunnel.TunnelManagerThread
import java.lang.reflect.InvocationTargetException
import java.util.Locale

class ZeroNetService : Service() {
    private var notifyBuilder: Notification.Builder? = null
    private var lastChannel: String? = null

    private var notificationManager: NotificationManager? = null

    private var handler: Handler? = null
    private var tunnelThread: Thread? = null
    private var tunnelManager: TunnelManagerThread? = null
    private var connectivityManager: ConnectivityManager? = null

    private var speedMonitor: SpeedMonitor? = null
    private var powerManager: PowerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val binder: IBinder = Binder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        this.handler = Handler()
        this.speedMonitor = SpeedMonitor(
            this.handler!!,
            object : SpeedMonitor.SpeedUpdateCallback {
                override fun onSpeedUpdate(uploadKbps: Double, downloadKbps: Double) {
                    this@ZeroNetService.updateNotificationWithStats()
                    val session = this@ZeroNetService.speedMonitor?.sessionStats
                    if (session != null) {
                        ConnectionStatsManager.setSessionStats(
                            session.durationSeconds,
                            session.downloadBytes,
                            session.uploadBytes
                        )
                    }
                }
            },
            this
        )
        this.connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        this.notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        this.powerManager = getSystemService(POWER_SERVICE) as PowerManager
        this.wakeLock = this.powerManager!!.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ZeroNet::VPNTunnelWakeLock"
        )
        createNotificationChannels()

        serviceScope.launch {
            ConnectionStatusManager.status.collect { status ->
                updateNotificationWithStats()
                if (status == ConnectionStatus.LEVEL_CONNECTED) {
                    showNotification(null, ZERONET_NOTIFICATION_CHANNEL_STATUS, System.currentTimeMillis())
                }
            }
        }
    }

    private fun createNotificationChannels() {
        val foregroundChannel = NotificationChannel(
            ZERONET_NOTIFICATION_CHANNEL_FG,
            "VPN Service",
            NotificationManager.IMPORTANCE_LOW
        )
        foregroundChannel.description = "VPN connection status"
        foregroundChannel.setShowBadge(false)
        foregroundChannel.setShowBadge(true)
        this.notificationManager!!.createNotificationChannel(foregroundChannel)

        val backgroundChannel = NotificationChannel(
            ZERONET_NOTIFICATION_CHANNEL_BG,
            "Background Service",
            NotificationManager.IMPORTANCE_MIN
        )
        backgroundChannel.description = "Background service notifications"
        backgroundChannel.setShowBadge(false)
        this.notificationManager!!.createNotificationChannel(backgroundChannel)

        val userReqChannel = NotificationChannel(
            ZERONET_NOTIFICATION_CHANNEL_USER_REQ,
            "User Requests",
            NotificationManager.IMPORTANCE_HIGH
        )
        userReqChannel.description = "User request notifications"
        userReqChannel.setShowBadge(true)
        this.notificationManager!!.createNotificationChannel(userReqChannel)

        val newStatusChannel = NotificationChannel(
            ZERONET_NOTIFICATION_CHANNEL_STATUS,
            "Connection Status",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        newStatusChannel.description = "VPN connection status updates"
        newStatusChannel.setShowBadge(true)
        this.notificationManager!!.createNotificationChannel(newStatusChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        this.startTunnelBroadcast()
        if (intent != null && ZERONET_START_SERVICE == intent.action) {
            return START_NOT_STICKY
        }
        this.showNotification("Service Started", ZERONET_NOTIFICATION_CHANNEL_STATUS, 0)
        setStatus(ConnectionStatus.LEVEL_START)
        val connectionStartTime = System.currentTimeMillis()
        val settings = Settings(this)
        settings.setLastConnectionStart(connectionStartTime)

        if (this.wakeLock != null && !this.wakeLock!!.isHeld) {
            this.wakeLock!!.acquire(10*60*1000L)
            i(TAG, "Wake lock acquired to keep VPN tunnel active")
        }
        
        Thread { this.startTunnel() }.start()
        if (speedMonitor != null) {
            speedMonitor!!.startMonitoring()
        }
        return START_NOT_STICKY
    }

    @Synchronized
    fun startTunnel() {
        i(TAG, "Starting SSH tunnel")
        setStatus(ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET)
        try {
            this.tunnelManager = TunnelManagerThread(this)
            this.tunnelManager!!.setOnStopClientListener(object : TunnelManagerThread.StopClientCallback {
                override fun onStop() {
                    this@ZeroNetService.endTunnelService()
                }
            })
            this.tunnelThread = Thread(tunnelManager)
            this.tunnelThread!!.start()
        } catch (e: Exception) {
            e(TAG, "Failed to start tunnel", e)
            setStatus(ConnectionStatus.LEVEL_AUTH_FAILED)
            this.endTunnelService()
        }
    }

    @Synchronized
    fun stopTunnel() {
        if (this.tunnelManager != null) {
            this.tunnelManager!!.stopAll()
            setStatus(ConnectionStatus.LEVEL_NOT_CONNECTED)
            if (this.tunnelThread != null) {
                this.tunnelThread!!.interrupt()
                i(TAG, "Tunnel thread stopped")
            }
            tunnelManager = null
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        this.stopTunnel()
        this.stopTunnelBroadcast()
        if (this.speedMonitor != null) {
            this.speedMonitor!!.stopMonitoring()
        }

        if (this.wakeLock != null && this.wakeLock!!.isHeld) {
            this.wakeLock!!.release()
            i(TAG, "Wake lock released")
        }

        serviceScope.cancel()
        
        i(TAG, "Service destroyed")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        w(TAG, "Low memory")
    }

    fun endTunnelService() {
        this.handler!!.post {
            if (this.speedMonitor != null) {
                this.speedMonitor!!.updateStatistics()
            }
            this.stopForeground(true)
            this.notificationManager!!.cancelAll()
            setStatus(ConnectionStatus.LEVEL_NOT_CONNECTED)

            if (this.wakeLock != null && this.wakeLock!!.isHeld) {
                this.wakeLock!!.release()
                i(TAG, "Wake lock released in endTunnelService")
            }
            
            this.stopSelf()
            i(TAG, "Service stopped")
        }
    }

    fun showNotification(msg: String?, channel: String, `when`: Long) {
        var priority: Int = PRIORITY_DEFAULT
        if (channel == ZERONET_NOTIFICATION_CHANNEL_BG) {
            priority = PRIORITY_MIN
        } else if (channel == ZERONET_NOTIFICATION_CHANNEL_USER_REQ) {
            priority = PRIORITY_MAX
        }

        val customView = createCustomNotificationView()
        val (contentTitle, contentText) = getNotificationContentText()

        val builder = Notification.Builder(this)
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setCustomBigContentView(customView)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setAutoCancel(false)
            .setDefaults(0)
            .setContentIntent(getGraphPendingIntent(this))

        val color = when (ConnectionStatusManager.status.value) {
            ConnectionStatus.LEVEL_CONNECTED -> 0xFF4CAF50.toInt()
            ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET,
            ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED,
            ConnectionStatus.LEVEL_CONNECTING_DNS,
            ConnectionStatus.LEVEL_CONNECTING_SSH,
            ConnectionStatus.LEVEL_AUTHENTICATING,
            ConnectionStatus.LEVEL_TUNNEL_SETUP,
            ConnectionStatus.LEVEL_START -> 0xFF2196F3.toInt()
            ConnectionStatus.LEVEL_AUTH_FAILED,
            ConnectionStatus.LEVEL_TIMEOUT,
            ConnectionStatus.LEVEL_PROXY_ERROR -> 0xFFF44336.toInt()
            ConnectionStatus.LEVEL_NOT_CONNECTED -> 0xFF757575.toInt()
            else -> 0xFF9C27B0.toInt()
        }
        builder.setColor(color)

        this.setLollipopNotificationExtras(builder)

        val notificationTime = if (`when` != 0L) `when` else System.currentTimeMillis()
        builder.setWhen(notificationTime)
        
        this.setJellyBeanNotificationExtras(priority, builder)
        builder.setChannelId(channel)
        if (msg != null && !msg.isEmpty()) {
            builder.setTicker(msg)
        }
        
        val notification = builder.build()
        val notificationId = channel.hashCode()
        this.startForeground(notificationId, notification)
        this.notificationManager!!.notify(notificationId, notification)
        if (this.lastChannel != null && channel != this.lastChannel) {
            this.notificationManager!!.cancel(this.lastChannel.hashCode())
        }
        this.lastChannel = channel
    }
    
    private fun createCustomNotificationView(): android.widget.RemoteViews {
        val remoteViews = android.widget.RemoteViews(packageName, R.layout.custom_notification)

        val status = ConnectionStatusManager.status.value
        val statusText = getStatusText(status)
        remoteViews.setTextViewText(R.id.notification_status, statusText)

        val stopIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = "tk.netindev.zeronet.vpn.ACTION_SERVICE_STOP"
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        remoteViews.setOnClickPendingIntent(R.id.stop_vpn_button, stopPendingIntent)

        if (status == ConnectionStatus.LEVEL_CONNECTED && this.speedMonitor != null) {
            val avgUp = speedMonitor!!.currentUploadSpeed
            val avgDown = speedMonitor!!.currentDownloadSpeed
            val upStr = String.format(Locale.ENGLISH, "%.1f kbps", avgUp)
            val downStr = String.format(Locale.ENGLISH, "%.1f kbps", avgDown)
            remoteViews.setTextViewText(R.id.upload_speed, upStr)
            remoteViews.setTextViewText(R.id.download_speed, downStr)
        } else {
            remoteViews.setTextViewText(R.id.upload_speed, "-- kbps")
            remoteViews.setTextViewText(R.id.download_speed, "-- kbps")
        }

        return remoteViews
    }
    
    private fun getNotificationContentText(): Pair<String, String> {
        val status = ConnectionStatusManager.status.value
        val statusStr = getStatusText(status)
        return if (status == ConnectionStatus.LEVEL_CONNECTED && speedMonitor != null) {
            val down = String.format(Locale.ENGLISH, "%.1f", speedMonitor!!.currentDownloadSpeed)
            val up = String.format(Locale.ENGLISH, "%.1f", speedMonitor!!.currentUploadSpeed)
            "ZeroNet" to "$statusStr · ↓ $down ↑ $up kbps"
        } else {
            "ZeroNet" to statusStr
        }
    }

    private fun getStatusText(status: ConnectionStatus): String {
        return when (status) {
            ConnectionStatus.LEVEL_CONNECTED -> "Connected"
            ConnectionStatus.LEVEL_START -> "Starting"
            ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET -> "Connecting"
            ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED -> "Handshake"
            ConnectionStatus.LEVEL_CONNECTING_DNS -> "DNS"
            ConnectionStatus.LEVEL_CONNECTING_SSH -> "SSH"
            ConnectionStatus.LEVEL_AUTHENTICATING -> "Auth"
            ConnectionStatus.LEVEL_TUNNEL_SETUP -> "Tunnel"
            ConnectionStatus.LEVEL_RECONNECTING -> "Reconnecting"
            ConnectionStatus.LEVEL_DISCONNECTING -> "Disconnecting"
            ConnectionStatus.LEVEL_AUTH_FAILED -> "Auth Failed"
            ConnectionStatus.LEVEL_NO_NETWORK -> "No Network"
            ConnectionStatus.LEVEL_TIMEOUT -> "Timeout"
            ConnectionStatus.LEVEL_PROXY_ERROR -> "Proxy Error"
            ConnectionStatus.LEVEL_NOT_CONNECTED -> "Disconnected"
            ConnectionStatus.UNKNOWN_LEVEL -> "Unknown"
        }
    }
    
    private fun updateNotificationWithStats() {
        val channel = this.lastChannel ?: ZERONET_NOTIFICATION_CHANNEL_STATUS
        showNotification(null, channel, 0)
    }

    private fun setLollipopNotificationExtras(builder: Notification.Builder) {
        builder.setCategory(Notification.CATEGORY_SERVICE)
        builder.setLocalOnly(true)
    }

    private fun setJellyBeanNotificationExtras(
        priority: Int,
        builder: Notification.Builder
    ) {
        try {
            if (priority != 0) {
                val setPriority =
                    builder.javaClass.getMethod("setPriority", Int::class.javaPrimitiveType)
                setPriority.invoke(builder, priority)
                val setUsesChronometer = builder.javaClass.getMethod(
                    "setUsesChronometer",
                    Boolean::class.javaPrimitiveType
                )
                setUsesChronometer.invoke(builder, true)
            }
        } catch (e: NoSuchMethodException) {
            e(TAG, "Error in notification setup", e)
        } catch (e: IllegalArgumentException) {
            e(TAG, "Error in notification setup", e)
        } catch (e: InvocationTargetException) {
            e(TAG, "Error in notification setup", e)
        } catch (e: IllegalAccessException) {
            e(TAG, "Error in notification setup", e)
        }
    }

    private fun addActionsToNotification(builder: Notification.Builder) {
        val intent = Intent(this, NotificationActionReceiver::class.java)
        intent.setAction(NotificationActionReceiver.ACTION_SERVICE_STOP)
        val disconnectPendingIntent =
            PendingIntent.getBroadcast(
                this, 12345, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop VPN", disconnectPendingIntent
        )
    }

    private val networkCallback
            : NetworkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            d(TAG, "Network available")
        }

        override fun onLost(network: Network) {
            d(TAG, "Network lost")
            setStatus(ConnectionStatus.LEVEL_NO_NETWORK)
        }

        override fun onUnavailable() {
            d(TAG, "Network unavailable")
            setStatus(ConnectionStatus.LEVEL_NO_NETWORK)
        }
    }

    private fun startTunnelBroadcast() {
        this.connectivityManager!!.registerDefaultNetworkCallback(this.networkCallback)
        val broadcastFilter = IntentFilter()
        broadcastFilter.addAction(ZERONET_STOP_SERVICE)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(this.broadcastReceiver, broadcastFilter)
    }

    private fun stopTunnelBroadcast() {
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(this.broadcastReceiver)
        this.connectivityManager!!.unregisterNetworkCallback(this.networkCallback)
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            d(TAG, "BroadcastReceiver received action: ${intent.action}")
            val action = intent.action
            if (action == null) {
                w(TAG, "Received broadcast with null action")
                return
            }
            if (action == ZERONET_STOP_SERVICE) {
                d(TAG, "Received stop service broadcast, calling endTunnelService()")
                endTunnelService()
            } else {
                w(TAG, "Unknown broadcast action: $action")
            }
        }
    }

    companion object {
        private const val TAG = "ZeroNetService"

        const val ZERONET_START_SERVICE: String = "tk.netindev.zeronet:startTunnel"
        val ZERONET_STOP_SERVICE: String =
            ZeroNetService::class.java.getName() + "::stopServiceBroadcast"
        const val ZERONET_NOTIFICATION_CHANNEL_FG: String = "zeronet_fg"
        const val ZERONET_NOTIFICATION_CHANNEL_BG: String = "zeronet_bg"
        const val ZERONET_NOTIFICATION_CHANNEL_STATUS: String = "zeronet_status"
        const val ZERONET_NOTIFICATION_CHANNEL_USER_REQ: String = "zeronet_userreq"

        private const val PRIORITY_MIN = -2
        private const val PRIORITY_DEFAULT = 0
        private const val PRIORITY_MAX = 2

        fun getGraphPendingIntent(context: Context?): PendingIntent? {
            val intent = Intent(context, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
