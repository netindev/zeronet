package tk.netindev.zeronet.vpn.tunnel

import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import tk.netindev.zeronet.vpn.tunnel.TunnelState.Companion.tunnelState

class TunnelVpnService : VpnService() {
    private val tunnelVpnManager = TunnelVpnManager(this)
    private var powerManager: PowerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    inner class LocalBinder : Binder() {
        val service: TunnelVpnService
            get() = this@TunnelVpnService
    }

    private val localBinder: IBinder = LocalBinder()

    override fun onBind(intent: Intent): IBinder? {
        val action = intent.action
        if (action != null && action == SERVICE_INTERFACE) {
            return super.onBind(intent)
        }
        return localBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (this.wakeLock != null && !this.wakeLock!!.isHeld) {
            this.wakeLock!!.acquire(10*60*1000L)
        }
        return tunnelVpnManager.onStartCommand(intent)
    }

    override fun onCreate() {
        tunnelState.tunnelManager = tunnelVpnManager

        this.powerManager = getSystemService(POWER_SERVICE) as PowerManager
        this.wakeLock = this.powerManager!!.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ZeroNet::TunnelVpnWakeLock"
        )
    }

    override fun onDestroy() {
        tunnelState.tunnelManager = null
        tunnelVpnManager.onDestroy()

        if (this.wakeLock != null && this.wakeLock!!.isHeld) {
            this.wakeLock!!.release()
        }
    }

    override fun onRevoke() {
        if (this.wakeLock != null && this.wakeLock!!.isHeld) {
            this.wakeLock!!.release()
        }
        broadcastVpnDisconnect()
        stopSelf()
    }

    fun newBuilder(): Builder {
        return Builder()
    }

    fun broadcastVpnDisconnect() {
        dispatchBroadcast(Intent(TUNNEL_VPN_DISCONNECT_BROADCAST))
    }

    fun broadcastVpnStart(success: Boolean) {
        val vpnStart = Intent(TUNNEL_VPN_START_BROADCAST)
        vpnStart.putExtra(TUNNEL_VPN_START_SUCCESS_EXTRA, success)
        dispatchBroadcast(vpnStart)
    }

    private fun dispatchBroadcast(broadcast: Intent) {
        LocalBroadcastManager.getInstance(this@TunnelVpnService)
            .sendBroadcast(broadcast)
    }

    companion object {
        const val TUNNEL_VPN_DISCONNECT_BROADCAST: String = "tunnelVpnDisconnectBroadcast"
        const val TUNNEL_VPN_START_BROADCAST: String = "tunnelVpnStartBroadcast"
        const val TUNNEL_VPN_START_SUCCESS_EXTRA: String = "tunnelVpnStartSuccessExtra"
    }
}
