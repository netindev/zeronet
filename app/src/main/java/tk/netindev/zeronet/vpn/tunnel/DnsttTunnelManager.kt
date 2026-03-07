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
import com.trilead.ssh2.DynamicPortForwarder
import com.trilead.ssh2.KnownHosts
import mobile.Mobile
import tk.netindev.zeronet.service.config.Settings
import tk.netindev.zeronet.service.util.AppLog.d
import tk.netindev.zeronet.service.util.AppLog.e
import tk.netindev.zeronet.service.util.AppLog.i
import tk.netindev.zeronet.service.util.AppLog.w
import tk.netindev.zeronet.service.util.ConnectionStatus
import tk.netindev.zeronet.service.util.ConnectionStatusManager.setStatus
import tk.netindev.zeronet.vpn.ZeroNetService
import tk.netindev.zeronet.vpn.methods.Socks5ProxyData
import tk.netindev.zeronet.vpn.tunnel.TunnelState.Companion.tunnelState
import tk.netindev.zeronet.vpn.util.NetworkUtils
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.concurrent.CountDownLatch

/**
 * Manages DNSTT + SSH chained tunnel:
 *
 * 1. DNSTT client starts → SOCKS5 proxy on 127.0.0.1:DNSTT_PORT
 * 2. SSH connects THROUGH the DNSTT SOCKS5 proxy to the SSH server
 * 3. SSH dynamic port forward → SOCKS5 on 127.0.0.1:SSH_LOCAL_PORT
 * 4. tun2socks uses 127.0.0.1:SSH_LOCAL_PORT
 */
class DnsttTunnelManager(context: Context) : Runnable, ConnectionMonitor {

    private val context: Context = context
    private val settings: Settings = Settings(context)
    private var zeroNetService: ZeroNetService? = if (context is ZeroNetService) context else null

    private var clientCallback: TunnelManagerThread.StopClientCallback? = null
    private var dnsttClient: mobile.DnsttClient? = null
    private var sshConnection: Connection? = null
    private var dynamicPortForwarder: DynamicPortForwarder? = null

    @Volatile
    private var stopping = false

    @Volatile
    private var connected = false

    @Volatile
    private var connecting = false

    private var countDownLatch: CountDownLatch? = null

    fun setOnStopClientListener(listener: TunnelManagerThread.StopClientCallback?) {
        this.clientCallback = listener
    }

    override fun run() {
        countDownLatch = CountDownLatch(1)

        i(TAG, "Starting DNSTT + SSH tunnel")

        var tries = 0
        while (!stopping) {
            try {
                if (isNetworkOffline()) {
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
                        i(TAG, "Reconnecting DNSTT + SSH")
                    }
                    try {
                        Thread.sleep(500)
                    } catch (_: InterruptedException) {
                        stopAll()
                        break
                    }
                    setStatus(ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET)
                    startDnsttAndSsh()
                    break
                }
            } catch (ex: Exception) {
                e(TAG, "Connection failed: ${ex.message}")
                Log.e(TAG, "Connection error details:", ex)
                setStatus(ConnectionStatus.LEVEL_DISCONNECTING)
                closeAll()
                try {
                    Thread.sleep(500)
                } catch (_: InterruptedException) {
                    stopAll()
                    break
                }
            }
            tries++
        }

        if (!stopping) {
            try {
                countDownLatch!!.await()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        clientCallback?.onStop()
    }

    @Throws(Exception::class)
    private fun startDnsttAndSsh() {
        // --- Phase 1: Start DNSTT client ---
        val dnsttConfig = settings.getDnsttConfig()

        if (dnsttConfig.tunnelDomain.isBlank()) {
            setStatus(ConnectionStatus.LEVEL_AUTH_FAILED)
            throw Exception("DNSTT tunnel domain is required")
        }
        if (dnsttConfig.publicKey.isBlank()) {
            setStatus(ConnectionStatus.LEVEL_AUTH_FAILED)
            throw Exception("DNSTT public key is required")
        }

        stopDnsttClient()

        val dnsttPort = dnsttConfig.proxyPort
        if (isPortInUse(dnsttPort)) {
            w(TAG, "Port $dnsttPort in use, waiting...")
            waitForPortRelease(dnsttPort)
            if (isPortInUse(dnsttPort)) {
                throw Exception("DNSTT proxy port $dnsttPort is in use")
            }
        }

        val listenAddr = "127.0.0.1:$dnsttPort"
        i(TAG, "Starting DNSTT client - DNS: ${dnsttConfig.dnsServer}, Domain: ${dnsttConfig.tunnelDomain}")

        setStatus(ConnectionStatus.LEVEL_CONNECTING_SSH)

        dnsttClient = Mobile.newClient(
            dnsttConfig.dnsServer,
            dnsttConfig.tunnelDomain,
            dnsttConfig.publicKey,
            listenAddr
        )
        dnsttClient!!.start()

        Thread.sleep(500)

        if (dnsttClient?.isRunning != true) {
            throw Exception("DNSTT client failed to start")
        }

        i(TAG, "DNSTT client running on $listenAddr")

        if (!verifySocks5Listening(dnsttPort)) {
            w(TAG, "DNSTT SOCKS5 proxy not responding on port $dnsttPort")
        }

        // --- Phase 2: Connect SSH through DNSTT SOCKS5 proxy ---
        val sshHost = settings.getString(Settings.SSH_HOST_KEY)
        val sshPort = settings.getString(Settings.SSH_PORT_KEY).toIntOrNull() ?: 22
        val sshUser = settings.getString(Settings.SSH_USERNAME_KEY)
        val sshPass = settings.getString(Settings.SSH_PASSWORD_KEY)
        val sshKeyPath = settings.getSSHKeypath()
        val sshLocalPort = settings.getString(Settings.SSH_LOCAL_PORT).toIntOrNull() ?: 1080

        if (sshUser.isBlank()) {
            throw Exception("SSH username is required for DNSTT mode")
        }

        setStatus(ConnectionStatus.LEVEL_CONNECTING_SSH)

        // Try SOCKS5 proxy first, then fall back to direct connection
        var sshConnected = false

        // Attempt 1: SSH through DNSTT SOCKS5 proxy
        i(TAG, "Attempt 1: SSH to $sshHost:$sshPort via DNSTT SOCKS5 proxy")
        try {
            sshConnection = Connection(sshHost, sshPort)
            if (settings.getIsDisabledDelaySSH()) {
                sshConnection!!.setTCPNoDelay(true)
            }
            sshConnection!!.setProxyData(Socks5ProxyData("127.0.0.1", dnsttPort))

            connecting = true
            try {
                sshConnection!!.connect(
                    { _, _, _, _ -> true },
                    30 * 1000,
                    60 * 1000
                )
            } finally {
                connecting = false
            }
            sshConnected = true
            i(TAG, "SSH connected via SOCKS5 proxy")
        } catch (ex: Exception) {
            w(TAG, "SOCKS5 proxy SSH failed: ${ex.message}")
            Log.w(TAG, "SOCKS5 SSH error details:", ex)
            sshConnection?.close()
            sshConnection = null
        }

        // Attempt 2: SSH directly to DNSTT port (raw TCP tunnel)
        if (!sshConnected) {
            i(TAG, "Attempt 2: SSH directly to 127.0.0.1:$dnsttPort (raw tunnel)")
            try {
                sshConnection = Connection("127.0.0.1", dnsttPort)
                if (settings.getIsDisabledDelaySSH()) {
                    sshConnection!!.setTCPNoDelay(true)
                }

                connecting = true
                try {
                    sshConnection!!.connect(
                        { _, _, _, _ -> true },
                        30 * 1000,
                        60 * 1000
                    )
                } finally {
                    connecting = false
                }
                sshConnected = true
                i(TAG, "SSH connected via direct tunnel")
            } catch (ex: Exception) {
                w(TAG, "Direct tunnel SSH failed: ${ex.message}")
                Log.w(TAG, "Direct SSH error details:", ex)
                sshConnection?.close()
                sshConnection = null
            }
        }

        if (!sshConnected || sshConnection == null) {
            throw Exception("SSH connection failed through both SOCKS5 and direct tunnel")
        }

        sshConnection!!.addConnectionMonitor(this)

        setStatus(ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED)

        // --- Phase 3: SSH authentication ---
        setStatus(ConnectionStatus.LEVEL_AUTHENTICATING)
        i(TAG, "Authenticating SSH as '$sshUser'...")

        var authenticated = false

        try {
            if (sshConnection!!.isAuthMethodAvailable(sshUser, "password") && sshPass.isNotEmpty()) {
                if (sshConnection!!.authenticateWithPassword(sshUser, sshPass)) {
                    i(TAG, "SSH password authentication successful")
                    authenticated = true
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Password auth error: ${ex.message}", ex)
        }

        if (!authenticated && sshKeyPath.isNotEmpty()) {
            try {
                val keyFile = File(sshKeyPath)
                if (keyFile.exists() && sshConnection!!.isAuthMethodAvailable(sshUser, "publickey")) {
                    if (sshConnection!!.authenticateWithPublicKey(sshUser, keyFile, sshPass.ifEmpty { "" })) {
                        i(TAG, "SSH public key authentication successful")
                        authenticated = true
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Public key auth error: ${ex.message}", ex)
            }
        }

        if (!sshConnection!!.isAuthenticationComplete) {
            setStatus(ConnectionStatus.LEVEL_AUTH_FAILED)
            throw IOException("SSH authentication failed")
        }

        connected = true
        setStatus(ConnectionStatus.LEVEL_CONNECTED)
        i(TAG, "SSH connected and authenticated through DNSTT")

        if (zeroNetService != null) {
            zeroNetService!!.showNotification(
                "Connected",
                ZeroNetService.ZERONET_NOTIFICATION_CHANNEL_STATUS, 0
            )
        }

        // --- Phase 4: SSH dynamic port forwarding ---
        i(TAG, "Starting SSH dynamic port forwarder on port $sshLocalPort")

        val threads = settings.getThreadPoolCount()
        dynamicPortForwarder = if (threads > 0) {
            sshConnection!!.createDynamicPortForwarder(sshLocalPort, threads)
        } else {
            sshConnection!!.createDynamicPortForwarder(sshLocalPort)
        }

        // --- Phase 5: Start VPN service ---
        startTunnelVpnService(sshLocalPort)

        // --- Phase 6: Start SSH pinger ---
        if (settings.getSSHPinger() > 0) {
            startPinger(settings.getSSHPinger())
        }
    }

    @Throws(IOException::class)
    private fun startTunnelVpnService(sshLocalPort: Int) {
        if (!connected) {
            throw IOException("Not connected")
        }
        i(TAG, "Starting tunnel VPN service")

        val broadcastFilter = IntentFilter(TunnelVpnService.TUNNEL_VPN_DISCONNECT_BROADCAST)
        broadcastFilter.addAction(TunnelVpnService.TUNNEL_VPN_START_BROADCAST)
        LocalBroadcastManager.getInstance(context)
            .registerReceiver(broadcastReceiver, broadcastFilter)

        val socksServerAddress = "127.0.0.1:$sshLocalPort"
        val dnsForward = settings.getVpnDnsForward()
        val udpResolver = if (settings.getVpnUdpForward()) settings.getVpnUdpResolver() else null

        // Exclude both the DNSTT DNS server and the SSH server from VPN routing
        val dnsttConfig = settings.getDnsttConfig()
        val excludeIpsList = mutableListOf<String?>()

        try {
            val dnsServerIp = InetAddress.getByName(dnsttConfig.dnsServer).hostAddress
            if (dnsServerIp != null) excludeIpsList.add(dnsServerIp)
        } catch (_: Exception) {
            excludeIpsList.add(dnsttConfig.dnsServer)
        }

        try {
            val sshHost = settings.getString(Settings.SSH_HOST_KEY)
            if (sshHost.isNotBlank()) {
                val sshIp = InetAddress.getByName(sshHost).hostAddress
                if (sshIp != null) excludeIpsList.add(sshIp)
            }
        } catch (_: Exception) {}

        val excludeIps = excludeIpsList.toTypedArray()
        val dnsResolvers: Array<String?> = if (dnsForward) {
            arrayOf(settings.getVpnDnsResolver())
        } else {
            val list = NetworkUtils.getNetworkDnsServer(context)
            arrayOf(list[0])
        }

        if (TunnelManagerThread.isServiceVpnRunning) {
            d(TAG, "VPN service already running, restarting tunnel")
            tunnelState.tunnelManager?.restartTunnel(socksServerAddress)
            return
        }

        val startTunnelVpn = Intent(context, TunnelVpnService::class.java)
        startTunnelVpn.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val filterApps = settings.getFilterApps().map { it as String? }.toTypedArray()
        val vpnSettings = TunnelVpnSettings(
            socksServerAddress,
            dnsForward,
            dnsResolvers,
            (dnsForward && udpResolver == null || !dnsForward && udpResolver != null),
            udpResolver,
            excludeIps,
            settings.getIsFilterApps(),
            settings.getIsFilterBypassMode(),
            filterApps,
            settings.getIsTetheringSubnet()
        )
        startTunnelVpn.putExtra(TunnelVpnManager.VPN_SETTINGS, vpnSettings)

        if (context.startService(startTunnelVpn) == null) {
            throw IOException("Failed to start tunnel VPN service")
        }
        tunnelState.setStartingTunnelManager()
    }

    fun stopAll() {
        if (stopping) return
        setStatus(ConnectionStatus.LEVEL_DISCONNECTING)
        i(TAG, "Stopping DNSTT + SSH tunnel")

        Thread {
            stopping = true
            countDownLatch?.countDown()

            stopTunnelVpnService()
            closeAll()

            try {
                Thread.sleep(500)
            } catch (_: InterruptedException) {
            }

            connected = false
            i(TAG, "DNSTT + SSH tunnel stopped")
        }.start()
    }

    @Synchronized
    private fun closeAll() {
        stopPinger()
        stopForwarder()
        closeSsh()
        stopDnsttClient()
    }

    private fun stopForwarder() {
        dynamicPortForwarder?.let {
            try {
                it.close()
            } catch (_: IOException) {
            }
        }
        dynamicPortForwarder = null
    }

    private fun closeSsh() {
        sshConnection?.let {
            try {
                it.close()
                i(TAG, "SSH connection closed")
            } catch (ex: Exception) {
                e(TAG, "Error closing SSH: ${ex.message}")
            }
        }
        sshConnection = null
    }

    private fun stopDnsttClient() {
        dnsttClient?.let { client ->
            try {
                client.stop()
                i(TAG, "DNSTT client stopped")
                Thread.sleep(500)
            } catch (ex: Exception) {
                e(TAG, "Error stopping DNSTT client: ${ex.message}")
            }
        }
        dnsttClient = null
    }

    @Synchronized
    private fun stopTunnelVpnService() {
        if (!TunnelManagerThread.isServiceVpnRunning) return

        i(TAG, "Stopping tunnel VPN service")
        tunnelState.tunnelManager?.signalStopService()

        try {
            LocalBroadcastManager.getInstance(context)
                .unregisterReceiver(broadcastReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    // --- SSH pinger ---
    private var pingerThread: Thread? = null

    private fun startPinger(intervalSeconds: Int) {
        pingerThread = object : Thread() {
            override fun run() {
                while (connected && !stopping) {
                    try {
                        sleep((intervalSeconds * 1000).toLong())
                        if (sshConnection != null && connected) {
                            val ping = sshConnection!!.ping()
                            tk.netindev.zeronet.service.util.ConnectionStatsManager.setPing(ping)
                        }
                    } catch (_: InterruptedException) {
                        break
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }
        pingerThread!!.isDaemon = true
        pingerThread!!.start()
    }

    private fun stopPinger() {
        pingerThread?.interrupt()
        pingerThread = null
    }

    // --- ConnectionMonitor ---
    override fun connectionLost(reason: Throwable?) {
        if (!stopping && !connecting) {
            e(TAG, "SSH connection lost: ${reason?.message}")
            stopAll()
        } else if (connecting) {
            d(TAG, "connectionLost during connect phase, ignoring: ${reason?.message}")
        }
    }

    override fun onReceiveInfo(infoId: Int, infoMsg: String?) {
        if (infoId == ConnectionMonitor.SERVER_BANNER && infoMsg != null) {
            i(TAG, "Server banner: $infoMsg")
        }
    }

    // --- Utility ---
    private fun isNetworkOffline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = manager.activeNetworkInfo
        return networkInfo == null || !networkInfo.isConnectedOrConnecting
    }

    private fun isPortInUse(port: Int): Boolean {
        return try {
            java.net.ServerSocket(port).use { false }
        } catch (_: Exception) {
            true
        }
    }

    private fun waitForPortRelease(port: Int, maxWaitMs: Int = 3000) {
        val start = System.currentTimeMillis()
        while (isPortInUse(port) && (System.currentTimeMillis() - start) < maxWaitMs) {
            Thread.sleep(200)
        }
    }

    private fun verifySocks5Listening(port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 2000)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @Synchronized
        override fun onReceive(context: Context?, intent: Intent) {
            when (intent.action) {
                TunnelVpnService.TUNNEL_VPN_START_BROADCAST -> {
                    val success = intent.getBooleanExtra(
                        TunnelVpnService.TUNNEL_VPN_START_SUCCESS_EXTRA, true
                    )
                    if (!success) stopAll()
                }
                TunnelVpnService.TUNNEL_VPN_DISCONNECT_BROADCAST -> stopAll()
            }
        }
    }

    companion object {
        private const val TAG = "DnsttTunnelManager"
    }
}
