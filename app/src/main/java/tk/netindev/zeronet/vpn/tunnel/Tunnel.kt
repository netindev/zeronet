package tk.netindev.zeronet.vpn.tunnel

import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import tk.netindev.zeronet.service.util.AppLog.d
import tk.netindev.zeronet.service.util.AppLog.e
import tk.netindev.zeronet.service.util.AppLog.w
import tk.netindev.zeronet.vpn.ZeroNetService
import tk.netindev.zeronet.vpn.external.PDNSDService
import tk.netindev.zeronet.vpn.external.Tun2SocksService
import tk.netindev.zeronet.vpn.network.CIDR
import tk.netindev.zeronet.vpn.network.IPAddress
import tk.netindev.zeronet.vpn.network.NetworkSpace
import tk.netindev.zeronet.vpn.util.NetworkUtils.findAvailablePort
import tk.netindev.zeronet.vpn.util.NetworkUtils.selectPrivateAddress
import tk.netindev.zeronet.vpn.util.PrivateAddress
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class Tunnel internal constructor(
    private val context: Context,
    private val vpnService: VpnService,
    private val establishedCallback: Runnable?
) {
    private var privateAddress: PrivateAddress? = null
    private val fileDescriptorReference: AtomicReference<ParcelFileDescriptor?> = AtomicReference<ParcelFileDescriptor?>()
    private val isRouting: AtomicBoolean = AtomicBoolean(false)
    private var tun2SocksService: Tun2SocksService? = null
    private var pdnsdService: PDNSDService? = null
    private val routes: NetworkSpace = NetworkSpace()

    @Synchronized
    @Throws(Exception::class)
    fun startRouting(settings: TunnelVpnSettings): Boolean {
        return this.startVpn(
            settings.dnsForward,
            settings.dnsResolver,
            settings.excludeIps,
            settings.enableFilterApps,
            settings.filterBypassMode,
            settings.filterApps,
            settings.enableTethering
        )
    }

    @Throws(Exception::class)
    private fun startVpn(
        forwardDns: Boolean, dnsResolver: Array<String?>?, excludeIps: Array<String?>?,
        enabledFilter: Boolean, filterBypassMode: Boolean, filterApps: Array<String?>?,
        enableTethering: Boolean
    ): Boolean {
        val routeIncludeMessage = StringBuilder("Routes Included: ")
        val routeExcludeMessage = StringBuilder("Routes Excluded: ")
        this.privateAddress = selectPrivateAddress()
        for (ipAddress in excludeIps!!) {
            this.routes.addIP(CIDR(ipAddress!!, 32), false)
        }
        try {
            Locale.setDefault(Locale("en"))
            val tunFd: ParcelFileDescriptor?
            val builder = (vpnService as TunnelVpnService).newBuilder()
                .addAddress(privateAddress!!.ipAddress, privateAddress!!.prefixLength)

            routes.addIP(CIDR("0.0.0.0", 0), true)
            routes.addIP(CIDR("10.0.0.0", 8), false)
            routes.addIP(CIDR(privateAddress!!.subnet, privateAddress!!.prefixLength), false)

            if (enableTethering) {
                routes.addIP(CIDR("192.168.42.0", 23), false)
                routes.addIP(CIDR("192.168.44.0", 24), false)
                routes.addIP(CIDR("192.168.49.0", 24), false)
            }
            for (dnsServer in dnsResolver!!) {
                try {
                    builder.addDnsServer(dnsServer!!)
                    routes.addIP(CIDR(dnsServer, 32), forwardDns)
                } catch (iae: IllegalArgumentException) {
                    e(
                        TAG,
                        String.format(
                            "Error trying to add DNS %s, %s",
                            dnsServer,
                            iae.localizedMessage
                        )
                    )
                }
            }
            builder.setMtu(MTU)
            val includeRoutes: MutableCollection<IPAddress?> = routes.getNetworks(true)
            for (ipAddress in includeRoutes) {
                routeIncludeMessage.append(
                    String.format(Locale.US,
                        "%s/%d",
                        ipAddress?.iPv4Address,
                        ipAddress?.networkMask
                    )
                )
                routeIncludeMessage.append(", ")
            }
            routeIncludeMessage.deleteCharAt(routeIncludeMessage.lastIndexOf(", "))
            val excludeRoutes: MutableCollection<IPAddress?> = routes.getNetworks(false)
            for (ipAddress in excludeRoutes) {
                routeExcludeMessage.append(
                    String.format(Locale.US,
                        "%s/%d",
                        ipAddress?.iPv4Address,
                        ipAddress?.networkMask
                    )
                )
                routeExcludeMessage.append(", ")
            }
            routeExcludeMessage.deleteCharAt(routeExcludeMessage.lastIndexOf(", "))
            d(TAG, routeIncludeMessage.toString())
            d(TAG, routeExcludeMessage.toString())
            val multicastRange = IPAddress(CIDR("224.0.0.0", 3), true)
            for (route in routes.positiveIPList) {
                try {
                    if (multicastRange.containsNet(route!!)) d(TAG, "Ignoring multicast: $route")
                    else builder.addRoute(route.iPv4Address, route.networkMask)
                } catch (ia: IllegalArgumentException) {
                    w(TAG, "Route rejected: " + route + " " + ia.localizedMessage)
                }
            }
            if (enabledFilter) {
                for (appPackage in filterApps!!) {
                    try {
                        if (filterBypassMode) {
                            builder.addDisallowedApplication(appPackage!!)
                            d(TAG, String.format("Disallowed Restrict: \"%s\"", appPackage))
                        } else {
                            builder.addAllowedApplication(appPackage!!)
                            d(TAG, String.format("Allowed Restrict: \"%s\"", appPackage))
                        }
                    } catch (_: PackageManager.NameNotFoundException) {
                        w(TAG, "APP \"$appPackage\" not found to restrict.")
                    }
                }
            }
            tunFd = builder.setSession("ZeroNet")
                .setConfigureIntent(ZeroNetService.getGraphPendingIntent(this.context)!!)
                .establish()
            if (tunFd == null) {
                return false
            }
            this.fileDescriptorReference.set(tunFd)
            this.isRouting.set(false)
            this.establishedCallback?.run()
            this.routes.clear()
        } catch (e: IllegalArgumentException) {
            throw Exception("Error while trying to start VPN", e)
        } catch (e: SecurityException) {
            throw Exception("Error while trying to start VPN", e)
        } catch (e: IllegalStateException) {
            throw Exception("Error while trying to start VPN", e)
        }
        return true
    }

    @Synchronized
    fun routeThroughTunnel(
        socksServerAddress: String?, dnsResolver: Array<String?>,
        forwardDns: Boolean, udpResolver: String?,
        transparentDns: Boolean
    ): Boolean {
        if (!isRouting.compareAndSet(false, true)) {
            return false
        }
        val fileDescriptor = this.fileDescriptorReference.get() ?: return false
        var dnsgwRelay: String? = null
        if (forwardDns) {
            val pdnsdPort = findAvailablePort(8091, 10)
            dnsgwRelay = String.format(Locale.US, "%s:%d", privateAddress!!.ipAddress, pdnsdPort)
            this.pdnsdService = PDNSDService(
                this.context, dnsResolver, DNS_RESOLVER_PORT,
                this.privateAddress!!.ipAddress, pdnsdPort
            )
            this.pdnsdService!!.start()
        }
        this.tun2SocksService = Tun2SocksService(
            context, fileDescriptor, MTU,
            this.privateAddress!!.router, VPN_INTERFACE_NETMASK, socksServerAddress,
            udpResolver, dnsgwRelay, transparentDns
        )
        this.tun2SocksService!!.start()
        return true
    }

    @Synchronized
    fun stopRoutingThroughTunnel() {
        if (this.tun2SocksService != null && this.tun2SocksService!!.isAlive) {
            this.tun2SocksService!!.interrupt()
        }
        this.tun2SocksService = null
        if (this.pdnsdService != null && this.pdnsdService!!.isAlive) {
            this.pdnsdService!!.interrupt()
        }
        this.pdnsdService = null
    }

    @Synchronized
    fun stopVpn() {
        this.stopRoutingThroughTunnel()

        val fileDescriptor = this.fileDescriptorReference.getAndSet(null)
        if (fileDescriptor != null) {
            try {
                d(TAG, "Closing VPN interface")
                fileDescriptor.close()
            } catch (_: IOException) {
                // ignored
            }
        }
    }

    companion object {
        private const val VPN_INTERFACE_NETMASK = "255.255.255.0"
        private const val DNS_RESOLVER_PORT = 53
        private const val MTU = 1500
        private const val TAG = "Tunnel"
    }
}
