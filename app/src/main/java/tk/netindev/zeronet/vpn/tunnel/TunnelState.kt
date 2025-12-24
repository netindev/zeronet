package tk.netindev.zeronet.vpn.tunnel

class TunnelState private constructor() {
    private var tunnelVpnManager: TunnelVpnManager? = null

    @get:Synchronized
    var startingTunnelManager: Boolean = false
        private set

    @get:Synchronized
    @set:Synchronized
    var tunnelManager: TunnelVpnManager?
        get() = tunnelVpnManager
        set(tunnelManager) {
            tunnelVpnManager = tunnelManager
            startingTunnelManager = false
        }

    @Synchronized
    fun setStartingTunnelManager() {
        startingTunnelManager = true
    }

    companion object {
        private var TUNNEL_STATE: TunnelState? = null

        @JvmStatic
        @get:Synchronized
        val tunnelState: TunnelState
            get() {
                if (TUNNEL_STATE == null) {
                    TUNNEL_STATE =
                        TunnelState()
                }
                return TUNNEL_STATE!!
            }
    }
}
