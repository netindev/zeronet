package tk.netindev.zeronet.vpn.tunnel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.trilead.ssh2.Connection
import com.trilead.ssh2.ConnectionMonitor
import com.trilead.ssh2.DebugLogger
import com.trilead.ssh2.DynamicPortForwarder
import com.trilead.ssh2.InteractiveCallback
import com.trilead.ssh2.KnownHosts
import com.trilead.ssh2.ProxyData
import com.trilead.ssh2.ServerHostKeyVerifier
import com.trilead.ssh2.transport.TransportManager
import tk.netindev.zeronet.data.PayloadItem
import tk.netindev.zeronet.data.PayloadManager.getPayloadItem
import tk.netindev.zeronet.service.config.Settings
import tk.netindev.zeronet.service.util.AppLog.d
import tk.netindev.zeronet.service.util.AppLog.e
import tk.netindev.zeronet.service.util.AppLog.i
import tk.netindev.zeronet.service.util.AppLog.w
import tk.netindev.zeronet.service.util.ConnectionStatsManager.setPing
import tk.netindev.zeronet.service.util.ConnectionStatus
import tk.netindev.zeronet.service.util.ConnectionStatusManager.setStatus
import tk.netindev.zeronet.vpn.ZeroNetService
import tk.netindev.zeronet.vpn.methods.HttpSSHProxy
import tk.netindev.zeronet.vpn.methods.SSLTunnelProxy
import tk.netindev.zeronet.vpn.tunnel.TunnelState.Companion.tunnelState
import tk.netindev.zeronet.vpn.util.NetworkUtils
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.CountDownLatch

open class TunnelManagerThread
    (context: Context) : Runnable, ConnectionMonitor, InteractiveCallback, ServerHostKeyVerifier,
    DebugLogger {
    lateinit var onStopClientListener: () -> Unit
    private var clientCallback: StopClientCallback? = null
    private val context: Context? = context
    private val settings: Settings
    private var zeroNetService: ZeroNetService? = null
    private var stopping = false
    private var starting = false
    private var dynamicPortForwarder: DynamicPortForwarder? = null

    private var connection: Connection? = null

    private var connected = false
    private var useProxy = false

    private var pingerThread: Thread? = null
    private var lastPingLatency: Long = -1

    private var countDownLatch: CountDownLatch? = null
    var reconnecting: Boolean = false

    interface StopClientCallback {
        fun onStop()
    }

    fun setOnStopClientListener(listener: StopClientCallback?) {
        this.clientCallback = listener
    }

    override fun run() {
        this.starting = true
        this.countDownLatch = CountDownLatch(1)

        i(TAG, "Starting SSH")

        var tries = 0
        while (!stopping) {
            try {
                if (this.isNetworkOffline(context!!)) {
                    i(TAG, "No network available")
                    setStatus(ConnectionStatus.LEVEL_NO_NETWORK)
                    try {
                        Thread.sleep(5000)
                    } catch (_: InterruptedException) {
                        stopAll()
                        break
                    }
                } else {
                    if (tries > 0) {
                        i(TAG, "Reconnecting")
                    }
                    try {
                        Thread.sleep(500)
                    } catch (_: InterruptedException) {
                        this.stopAll()
                        break
                    }
                    setStatus(ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET)
                    this.startSSHClient()
                    break
                }
            } catch (_: Exception) {
                e(TAG, "Disconnected")
                setStatus(ConnectionStatus.LEVEL_DISCONNECTING)
                this.closeSSH()
                try {
                    Thread.sleep(500)
                } catch (_: InterruptedException) {
                    this.stopAll()
                    break
                }
            }

            tries++
        }

        this.starting = false

        if (!stopping) {
            try {
                countDownLatch!!.await()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        if (clientCallback != null) {
            clientCallback!!.onStop()
        }
    }

    private fun isNetworkOffline(context: Context): Boolean {
        val manager = context
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = manager.getActiveNetworkInfo()

        return (networkInfo == null || !networkInfo.isConnectedOrConnecting)
    }

    fun stopAll() {
        if (stopping) {
            return
        }

        setStatus(ConnectionStatus.LEVEL_DISCONNECTING)

        i(TAG, "Stopping SSH service")

        Thread {
            stopping = true
            if (countDownLatch != null) countDownLatch!!.countDown()

            closeSSH()

            try {
                Thread.sleep(1000)
            } catch (_: InterruptedException) {
            }

            i(TAG, "SSH disconnected")

            starting = false
            reconnecting = false
        }.start()
    }

    @Throws(Exception::class)
    protected fun startForwarder(port: Int) {
        if (!connected) {
            throw Exception()
        }

        this.startForwarderSocks(port)
        this.startTunnelVpnService()

        Thread {
            while (true) {
                if (!this.connected) {
                    break
                }
                try {
                    Thread.sleep(2000)
                } catch (_: InterruptedException) {
                    break
                }
                if (this.lastPingLatency > 0) {
                    break
                }
            }
        }.start()
    }

    @Throws(Exception::class)
    protected fun startSSHClient() {
        this.stopping = false

        val sshHost = settings.getString(Settings.SSH_HOST_KEY)
        val sshPort = settings.getString(Settings.SSH_PORT_KEY).toInt()
        val sshUser = settings.getString(Settings.SSH_USERNAME_KEY)
        val sshPass = settings.getString(Settings.SSH_PASSWORD_KEY)
        val sshKeyPath = settings.getSSHKeypath()

        val sshLocalPort = settings.getString(Settings.SSH_LOCAL_PORT).toInt()

        try {
            this.connect(sshHost, sshPort)

            for (i in 0..<AUTH_TRIES) {
                if (this.stopping) {
                    return
                }

                try {
                    this.auth(sshUser, sshPass, sshKeyPath)

                    break
                } catch (_: IOException) {
                    if (i + 1 >= AUTH_TRIES) {
                        throw IOException("Auth failed")
                    } else {
                        try {
                            Thread.sleep(3000)
                        } catch (_: InterruptedException) {
                            return
                        }
                    }
                }
            }

            i(TAG, "Connected")

            if (settings.getSSHPinger() > 0) {
                this.startPinger(this.settings.getSSHPinger())
            }

            this.startForwarder(sshLocalPort)
        } catch (e: Exception) {
            this.connected = false

            throw e
        }
    }

    @Synchronized
    fun closeSSH() {
        stopTunnelVpnService()
        stopForwarderSocks()
        stopPinger()

        if (connection != null) {
            d("TunnelManagerThread", "Stopping SSH")
            connection!!.close()
        }
    }

    @Throws(Exception::class)
    protected fun connect(server: String?, port: Int) {
        if (!this.starting) {
            throw Exception()
        }

        try {
            this.connection = Connection(server, port)
            if (this.settings.getIsDisabledDelaySSH()) {
                this.connection!!.setTCPNoDelay(true)
            }

            this.configureProxy(connection!!)

            this.connection!!.addConnectionMonitor(this)

            val connectivityManager =
                context!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val defaultProxy = connectivityManager.defaultProxy
            if (defaultProxy != null) {
                i(
                    TAG,
                    "Proxy on network: " + String.format(
                        "%s:%d",
                        defaultProxy.host,
                        defaultProxy.port
                    )
                )
            }

            i(TAG, "Connecting")
            setStatus(ConnectionStatus.LEVEL_CONNECTING_SSH)

            this.connection!!.connect(this, 10 * 1000, 20 * 1000)

            this.connected = true
            setStatus(ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED)
        } catch (e: Exception) {
            val stringWriter = StringWriter()
            e.printStackTrace(PrintWriter(stringWriter))

            val cause = e.cause.toString()
            if (useProxy && cause.contains("Key exchange was not finished")) {
                e(TAG, "Proxy: connection lost")
            } else {
                e(TAG, "SSH: $cause")
            }
            throw Exception(e)
        }
    }

    @Throws(IOException::class)
    protected fun auth(user: String?, pass: String, keyPath: String?) {
        var pass = pass
        if (!connected) {
            throw IOException()
        }
        setStatus(ConnectionStatus.LEVEL_AUTHENTICATING)
        i("TunnelManagerThread", "Authenticating...")
        try {
            if (connection!!.isAuthMethodAvailable(
                    user,
                    AUTH_PASSWORD
                )
            ) {
                if (connection!!.authenticateWithPassword(
                        user,
                        pass
                    )
                ) {
                    i("TunnelManagerThread", "Authentication successful")
                    setStatus(ConnectionStatus.LEVEL_CONNECTED)
                    if (zeroNetService != null) {
                        zeroNetService!!.showNotification(
                            "Connected",
                            ZeroNetService.ZERONET_NOTIFICATION_CHANNEL_STATUS, 0
                        )
                    }
                }
            }
        } catch (e: IllegalStateException) {
            Log.e(
                TAG,
                "Connection went away while we were trying to authenticate",
                e
            )
        } catch (e: Exception) {
            Log.e(TAG, "Problem during auth", e)
        }
        try {
            if (connection!!.isAuthMethodAvailable(
                    user,
                    AUTH_PUBLIC_KEY
                ) && keyPath != null && !keyPath.isEmpty()
            ) {
                val file = File(keyPath)
                if (file.exists()) {
                    if (pass.isEmpty()) pass = ""
                    i("TunnelManagerThread", "Authenticating with public key")
                    if (connection!!.authenticateWithPublicKey(
                            user, file,
                            pass
                        )
                    ) {
                        i("TunnelManagerThread", "Authentication successful")
                    }
                }
            }
        } catch (_: Exception) {
            Log.d(TAG, "Host does not support 'Public key' authentication.")
        }
        if (!connection!!.isAuthenticationComplete) {
            i("TunnelManagerThread", "Authentication failed, user or password expired")
            setStatus(ConnectionStatus.LEVEL_AUTH_FAILED)
            throw IOException("Authentication failed, user or password expired")
        }
    }

    override fun replyToChallenge(
        name: String?, instruction: String?,
        numPrompts: Int, prompt: Array<String?>, echo: BooleanArray?
    ): Array<String?> {
        val responses = arrayOfNulls<String>(numPrompts)
        for (i in 0..<numPrompts) {
            if (prompt[i]!!.lowercase(Locale.getDefault()).contains("password")) {
                responses[i] = settings.getString(Settings.SSH_PASSWORD_KEY)
            }
        }
        return responses
    }

    override fun verifyServerHostKey(
        hostname: String?, port: Int, serverHostKeyAlgorithm: String?,
        serverHostKey: ByteArray?
    ): Boolean {
        KnownHosts.createHexFingerprint(serverHostKeyAlgorithm, serverHostKey)
        return true
    }

    @Throws(Exception::class)
    private fun configureProxy(connection: Connection) {
        if (this.settings.getCustomPayloadEnabled()) {
            this.configureCustomPayloadProxy(connection)
        } else {
            this.configurePredefinedPayloadProxy(connection)
        }
    }

    @Throws(Exception::class)
    private fun configureCustomPayloadProxy(connection: Connection) {
        val customPayload = settings.getTunnelConfig().customPayload
        val tunnelType = settings.getTunnelConfig().tunnelType

        val proxyHost = settings.getString(Settings.REMOTE_PROXY_HOST_KEY)
        val proxyPortStr = settings.getString(Settings.REMOTE_PROXY_PORT_KEY)

        if (proxyHost.trim { it <= ' ' }.isEmpty()) {
            w(TAG, "Proxy host not configured, using direct connection")
            this.useProxy = false
            return
        }

        val proxyPort: Int
        try {
            proxyPort = proxyPortStr.toInt()
            if (proxyPort !in 1..65535) {
                throw NumberFormatException("Invalid port range")
            }
        } catch (_: NumberFormatException) {
            e(TAG, "Invalid proxy port: $proxyPortStr")
            throw Exception("Invalid proxy port configuration")
        }

        val payloadItem = PayloadItem("Custom", customPayload, proxyHost, proxyPort, tunnelType)
        this.addProxy(tunnelType, customPayload, payloadItem, connection)

        i(TAG, "Using custom payload proxy: $proxyHost:$proxyPort")
    }

    @Throws(Exception::class)
    private fun configurePredefinedPayloadProxy(connection: Connection) {
        val operator = settings.getTunnelConfig().operator
        val payloadName = settings.getTunnelConfig().payload

        val payloadItem = getPayloadItem(operator, payloadName)
        if (payloadItem == null) {
            e(TAG, "Payload not found for operator: $operator, payload: $payloadName")
            throw Exception("Payload configuration not found")
        }

        val tunnelType = payloadItem.tunnelType
        val payloadString = payloadItem.payloadString

        this.addProxy(tunnelType, payloadString, payloadItem, connection)

        i(TAG, "Using predefined payload: $operator - $payloadName")
    }

    @Throws(Exception::class)
    protected fun addProxy(
        proxyType: String?, customPayload: String?, payloadItem: PayloadItem,
        connection: Connection
    ) {
        if (proxyType == null || proxyType.trim { it <= ' ' }.isEmpty()) {
            d(TAG, "No proxy type specified, using direct connection")
            this.useProxy = false
            return
        }
        this.useProxy = true
        d(TAG, "Configuring proxy type: $proxyType")
        try {
            when (proxyType) {
                "SSH_DIRECT" -> configureSSHDirectProxy(customPayload, connection)
                "SSH_PROXY" -> configureSSHProxyProxy(customPayload, payloadItem, connection)
                "SSH_SSL_TUNNEL" -> configureSSLTunnelProxy(customPayload, payloadItem, connection)
                else -> {
                    w(TAG, "Unknown proxy type: $proxyType, using direct connection")
                    this.useProxy = false
                }
            }
        } catch (e: Exception) {
            e(TAG, "Failed to configure proxy: " + e.message)
            this.useProxy = false
            throw Exception("Proxy configuration failed: " + e.message)
        }
    }

    @Throws(Exception::class)
    private fun configureSSHDirectProxy(customPayload: String?, conn: Connection) {
        if (customPayload == null || customPayload.trim { it <= ' ' }.isEmpty()) {
            d(TAG, "No custom payload for SSH_DIRECT, using direct connection")
            useProxy = false
            return
        }
        val sshHost = settings.getString(Settings.SSH_HOST_KEY)
        val sshPortStr = settings.getString(Settings.SSH_PORT_KEY)
        if (sshHost.trim { it <= ' ' }.isEmpty()) {
            throw Exception("SSH host not configured")
        }
        val sshPort: Int
        try {
            sshPort = sshPortStr.toInt()
        } catch (_: NumberFormatException) {
            throw Exception("Invalid SSH port: $sshPortStr")
        }
        val proxyData: ProxyData = HttpSSHProxy(sshHost, sshPort, null, null, customPayload)
        conn.setProxyData(proxyData)
        i(TAG, "SSH Direct proxy configured for host: $sshHost:$sshPort")
    }

    @Throws(Exception::class)
    private fun configureSSHProxyProxy(
        customPayload: String?, payloadItem: PayloadItem,
        conn: Connection
    ) {
        var customPayload = customPayload
        if (customPayload != null && customPayload.trim { it <= ' ' }.isEmpty()) {
            customPayload = null
        }
        val proxyHost = payloadItem.proxyHost
        val proxyPort = payloadItem.proxyPort
        if (proxyHost.trim { it <= ' ' }.isEmpty()) {
            throw Exception("Proxy host not configured in payload item")
        }
        if (proxyPort !in 1..65535) {
            throw Exception("Invalid proxy port in payload item: $proxyPort")
        }
        val proxyData: ProxyData = HttpSSHProxy(proxyHost, proxyPort, null, null, customPayload)
        conn.setProxyData(proxyData)
        i(TAG, "SSH Proxy configured: $proxyHost:$proxyPort")
    }

    @Throws(Exception::class)
    private fun configureSSLTunnelProxy(
        customPayload: String?, payloadItem: PayloadItem,
        conn: Connection
    ) {
        var payload = customPayload
        if (payload != null && payload.trim().isEmpty()) {
            payload = null
        }

        val proxyHost = payloadItem.proxyHost
        val proxyPort = payloadItem.proxyPort
        if (proxyHost.trim().isEmpty()) {
            throw Exception("Proxy host not configured in payload item for SSL tunnel")
        }
        if (proxyPort !in 1..65535) {
            throw Exception("Invalid proxy port in payload item: $proxyPort")
        }

        val sniHost = settings.getString(Settings.SSL_SNI_HOST_KEY)

        val proxyData: ProxyData = SSLTunnelProxy(
            proxyHost, proxyPort, null, null, payload,
            sniHost.ifBlank { null }
        )
        conn.setProxyData(proxyData)
        i(TAG, "SSL Tunnel proxy configured: $proxyHost:$proxyPort, SNI: ${sniHost.ifBlank { "(default)" }}")
    }

    @Synchronized
    @Throws(Exception::class)
    private fun startForwarderSocks(localPort: Int) {
        if (!connected) {
            throw Exception()
        }
        try {
            val threads = settings.getThreadPoolCount()
            if (threads > 0) {
                this.dynamicPortForwarder =
                    connection!!.createDynamicPortForwarder(localPort, threads)
            } else {
                this.dynamicPortForwarder = connection!!.createDynamicPortForwarder(localPort)
            }
        } catch (e: Exception) {
            e("TunnelManagerThread", "Socks Local: " + e.cause.toString())
            throw Exception()
        }
    }

    @Synchronized
    private fun stopForwarderSocks() {
        if (this.dynamicPortForwarder != null) {
            try {
                this.dynamicPortForwarder!!.close()
            } catch (_: IOException) {
                // ignored
            }
            this.dynamicPortForwarder = null
        }
    }

    @Throws(Exception::class)
    private fun startPinger(timePing: Int) {
        if (!connected) {
            throw Exception()
        }
        this.pingerThread = object : Thread() {
            override fun run() {
                while (connected) {
                    try {
                        this.makePinger()
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }

            @Synchronized
            @Throws(InterruptedException::class)
            fun makePinger() {
                try {
                    if (connection != null) {
                        val ping = connection!!.ping()
                        if (lastPingLatency < 0) {
                            lastPingLatency = ping
                        }
                        setPing(ping)
                    } else {
                        throw InterruptedException()
                    }
                } catch (_: Exception) {
                    e(TAG, "Ping error")
                }
                if (timePing == 0) {
                    return
                }
                if (timePing > 0) {
                    sleep(timePing * 1000L)
                } else {
                    e(TAG, "Invalid ping")
                    throw InterruptedException()
                }
            }
        }
        pingerThread!!.start()
    }

    @Synchronized
    private fun stopPinger() {
        if (pingerThread != null && pingerThread!!.isAlive) {
            pingerThread!!.interrupt()
            pingerThread = null
        }
    }

    override fun connectionLost(throwable: Throwable?) {
        if (this.starting || this.stopping || this.reconnecting) {
            return
        }
        e(TAG, "Connection lost")
        if (throwable != null) {
            if (throwable.message!!.contains(
                    "There was a problem during connect"
                )
            ) {
                return
            } else if (throwable.message!!.contains(
                    "Closed due to user request"
                )
            ) {
                return
            } else if (throwable.message!!.contains(
                    "The connect timeout expired"
                )
            ) {
                this.stopAll()
                return
            }
        } else {
            this.stopAll()
            return
        }
        this.reconnectSSH()
    }

    fun reconnectSSH() {
        if (this.starting || this.stopping || this.reconnecting) {
            return
        }
        this.reconnecting = true
        this.closeSSH()
        i(TAG, "Reconnecting..")
        try {
            Thread.sleep(1000)
        } catch (_: InterruptedException) {
            reconnecting = false
            return
        }
        var i = 0
        while (i < RECONNECT_TRIES) {
            if (stopping) {
                reconnecting = false
                return
            }
            var sleepTime = 5
            if (this.isNetworkOffline(context!!)) {
                i(TAG, "Waiting for network..")

                i(TAG, "No network available")
            } else {
                sleepTime = 3
                this.starting = true
                i(TAG, "Reconnecting..")
                i(TAG, "Reconnecting")
                try {
                    this.startSSHClient()
                    starting = false
                    reconnecting = false
                    return
                } catch (_: Exception) {
                    i(TAG, "Disconnected")
                }
                this.starting = false
            }
            try {
                Thread.sleep((sleepTime * 1000).toLong())
                i--
            } catch (_: InterruptedException) {
                this.reconnecting = false
                return
            }
            i++
        }
        this.reconnecting = false
        this.stopAll()
    }

    override fun onReceiveInfo(id: Int, msg: String?) {
        if (id == ConnectionMonitor.SERVER_BANNER) {
            i("TunnelManagerThread", "Server banner: $msg")
        }
    }

    override fun log(level: Int, className: String, message: String?) {
        d("TunnelManagerThread", String.format("%s: %s", className, message))
    }

    @Throws(IOException::class)
    protected fun startTunnelVpnService() {
        if (!this.connected) {
            throw IOException()
        }
        i("TunnelManagerThread", "Starting tunnel service")

        val broadcastFilter =
            IntentFilter(TunnelVpnService.TUNNEL_VPN_DISCONNECT_BROADCAST)
        broadcastFilter.addAction(TunnelVpnService.TUNNEL_VPN_START_BROADCAST)

        LocalBroadcastManager.getInstance(context!!)
            .registerReceiver(broadcastReceiver, broadcastFilter)

        val socksServerAddress =
            String.format("127.0.0.1:%s", this.settings.getString(Settings.SSH_LOCAL_PORT))
        val dnsForward = this.settings.getVpnDnsForward()
        val udpResolver =
            if (this.settings.getVpnUdpForward()) this.settings.getVpnUdpResolver() else null

        val server: String?
        var proxyHost: String?

        if (this.settings.getCustomPayloadEnabled()) {
            proxyHost = this.settings.getString(Settings.REMOTE_PROXY_HOST_KEY)
            if (proxyHost.trim { it <= ' ' }.isEmpty()) {
                proxyHost = this.settings.getString(Settings.SSH_HOST_KEY)
            }
        } else {
            val payloadItem = getPayloadItem(
                this.settings.getTunnelConfig().operator,
                this.settings.getTunnelConfig().payload
            )
            if (payloadItem == null) {
                proxyHost = this.settings.getString(Settings.SSH_HOST_KEY)
            } else {
                proxyHost = payloadItem.proxyHost
                if (proxyHost.trim { it <= ' ' }.isEmpty()) {
                    proxyHost = this.settings.getString(Settings.SSH_HOST_KEY)
                }
            }
        }

        if (proxyHost.trim { it <= ' ' }.isEmpty()) {
            throw IOException("No proxy host configured")
        }

        try {
            val inetAddress = TransportManager.createInetAddress(proxyHost)
            server = inetAddress.hostAddress
            if (server != null && server.contains("::")) {
                e(TAG, "IPv6 proxy resolved: $server, it should be IPv4!")
                stopAll()
            }
            i(TAG, "Resolved proxy host: " + proxyHost + " -> " + inetAddress.hostAddress)
        } catch (_: UnknownHostException) {
            e(TAG, "Failed to resolve proxy host: $proxyHost")
            throw IOException("Invalid proxy host: $proxyHost")
        }

        val excludeIps = arrayOf<String?>(server)
        val dnsResolvers: Array<String?>

        if (dnsForward) {
            dnsResolvers = arrayOf(settings.getVpnDnsResolver())
        } else {
            val list: List<String?> = NetworkUtils.getNetworkDnsServer(context)
            dnsResolvers = arrayOf(list[0])
        }

        if (isServiceVpnRunning) {
            Log.d(TAG, "Already running service")

            val tunnelManager = tunnelState
                .tunnelManager

            tunnelManager?.restartTunnel(socksServerAddress)

            return
        }

        val startTunnelVpn = Intent(context, TunnelVpnService::class.java)
        startTunnelVpn.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val filterApps = this.settings.getFilterApps().map { it as String? }.toTypedArray()
        val settings = TunnelVpnSettings(
            socksServerAddress,
            dnsForward,
            dnsResolvers,
            (dnsForward && udpResolver == null || !dnsForward && udpResolver != null),
            udpResolver,
            excludeIps,
            this.settings.getIsFilterApps(),
            this.settings.getIsFilterBypassMode(),
            filterApps,
            this.settings.getIsTetheringSubnet()
        )
        startTunnelVpn.putExtra(TunnelVpnManager.VPN_SETTINGS, settings)

        if (this.context.startService(startTunnelVpn) == null) {
            i("TunnelManagerThread", "Failed to start tunnel VPN service")
            throw IOException("Failed to start tunnel VPN service")
        }
        tunnelState.setStartingTunnelManager()
    }

    @Synchronized
    protected fun stopTunnelVpnService() {
        if (!isServiceVpnRunning) {
            return
        }

        i("TunnelManagerThread", "Stopping tunnel service")

        val currentTunnelManager = tunnelState
            .tunnelManager

        currentTunnelManager?.signalStopService()

        LocalBroadcastManager.getInstance(context!!)
            .unregisterReceiver(broadcastReceiver)
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @Synchronized
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action

            if (TunnelVpnService.TUNNEL_VPN_START_BROADCAST == action) {
                val startSuccess =
                    intent.getBooleanExtra(TunnelVpnService.TUNNEL_VPN_START_SUCCESS_EXTRA, true)

                if (!startSuccess) {
                    stopAll()
                }
            } else if (TunnelVpnService.TUNNEL_VPN_DISCONNECT_BROADCAST == action) {
                stopAll()
            }
        }
    }

    init {
        if (context is ZeroNetService) {
            this.zeroNetService = context
        }
        this.settings = Settings(context)
    }

    companion object {
        private const val TAG = "TunnelManagerThread"
        private const val AUTH_PUBLIC_KEY = "publickey"
        private const val AUTH_PASSWORD = "password"
        private const val AUTH_TRIES = 1
        private const val RECONNECT_TRIES = 5

        val isServiceVpnRunning: Boolean
            get() {
                val tunnelState =
                    tunnelState
                return tunnelState.startingTunnelManager || tunnelState.tunnelManager != null
            }
    }
}
