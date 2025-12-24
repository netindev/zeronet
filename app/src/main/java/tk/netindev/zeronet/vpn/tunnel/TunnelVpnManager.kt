package tk.netindev.zeronet.vpn.tunnel

import android.app.Service
import android.content.Intent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class TunnelVpnManager(private val parentService: TunnelVpnService) {
    private var tunnelThreadStopSignal: CountDownLatch? = null
    private var tunnelThread: Thread? = null
    private val isStopping: AtomicBoolean = AtomicBoolean(false)
    private var tunnel: Tunnel? = null
    private val isReconnecting: AtomicBoolean = AtomicBoolean(false)
    private var settings: TunnelVpnSettings? = null

    init {
        if (this.tunnel != null) {
            this.tunnel!!.stopVpn()
        }
        this.tunnel =
            Tunnel(this.parentService, this.parentService) { this.startTunnel() }
    }

    fun onStartCommand(intent: Intent?): Int {
        if (intent == null) {
            parentService.broadcastVpnStart(false)
            return 0
        }
        this.settings = intent.getParcelableExtra(VPN_SETTINGS)
        if (this.settings == null) {
            this.parentService.broadcastVpnStart(false)
            return 0
        }
        if (this.settings!!.socksServer == null) {
            this.parentService.broadcastVpnStart(false)
            return 0
        }
        if (this.settings!!.dnsResolver == null) {
            this.parentService.broadcastVpnStart(false)
            return 0
        }
        try {
            if (!this.tunnel!!.startRouting(this.settings!!)) {
                this.parentService.broadcastVpnStart(false)
            }
        } catch (_: Exception) {
            this.parentService.broadcastVpnStart(false)
        }
        return Service.START_NOT_STICKY
    }

    fun onDestroy() {
        if (this.tunnelThread == null) {
            return
        }
        this.signalStopService()
        try {
            this.tunnelThread!!.join()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        this.tunnelThreadStopSignal = null
        this.tunnelThread = null
    }

    fun signalStopService() {
        if (this.tunnelThreadStopSignal != null) {
            this.tunnelThreadStopSignal!!.countDown()
        }
    }

    fun restartTunnel(socksServerAddress: String?) {
        if (socksServerAddress == null ||
            socksServerAddress == settings!!.socksServer
        ) {
            this.parentService.broadcastVpnStart(true)
            return
        }
        this.settings!!.socksServer = socksServerAddress
        this.isReconnecting.set(true)
        this.signalStopService()
    }

    private fun startTunnel() {
        this.tunnelThreadStopSignal = CountDownLatch(1)
        this.tunnelThread = Thread {
            this.runTunnel(
                this.settings!!.socksServer, this.settings!!.dnsResolver!!,
                this.settings!!.dnsForward, this.settings!!.udpResolver, this.settings!!.udpDnsRelay
            )
        }
        this.tunnelThread!!.start()
    }

    private fun runTunnel(
        socksServerAddress: String?,
        dnsResolver: Array<String?>,
        forwardDns: Boolean,
        udpResolver: String?,
        udpDnsRelay: Boolean
    ) {
        this.isStopping.set(false)
        try {
            if (!this.tunnel!!.routeThroughTunnel(
                    socksServerAddress,
                    dnsResolver,
                    forwardDns,
                    udpResolver,
                    udpDnsRelay
                )
            ) {
                throw Exception("Application is not prepared or revoked")
            }
            this.parentService.broadcastVpnStart(true)
            try {
                this.tunnelThreadStopSignal!!.await()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            this.isStopping.set(true)
        } catch (_: Exception) {
            this.parentService.broadcastVpnStart(false)
        } finally {
            if (isReconnecting.get()) {
                this.tunnel!!.stopRoutingThroughTunnel()
            } else {
                this.tunnel!!.stopVpn()
                this.parentService.stopForeground(true)
                this.parentService.stopSelf()
            }
            this.isReconnecting.set(false)
        }
    }

    companion object {
        const val VPN_SETTINGS: String = "vpnSettings"
    }
}
