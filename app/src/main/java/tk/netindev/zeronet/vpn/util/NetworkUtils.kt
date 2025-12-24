package tk.netindev.zeronet.vpn.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import java.io.IOException
import java.net.*

object NetworkUtils {

    private const val DEFAULT_PRIMARY_DNS_SERVER = "8.8.4.4"
    private const val DEFAULT_SECONDARY_DNS_SERVER = "8.8.8.8"

    @Throws(Exception::class)
    fun getActiveNetworkDnsResolver(context: Context): List<String?> {
        val resolvers = getActiveNetworkDnsResolvers(context)
        if (resolvers.isEmpty()) throw Exception("no active network DNS resolver")

        return resolvers
            .map { it.hostAddress }
            .filter { !it!!.contains(":") }
            .take(2)
    }

    @Throws(Exception::class)
    private fun getActiveNetworkDnsResolvers(context: Context): Collection<InetAddress> {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val method = ConnectivityManager::class.java.getMethod("getActiveLinkProperties")
            val linkProperties = method.invoke(connectivityManager)
            if (linkProperties is LinkProperties) linkProperties.dnsServers else emptyList()
        } catch (e: ReflectiveOperationException) {
            throw Exception("getActiveNetworkDnsResolvers failed", e)
        } catch (e: NullPointerException) {
            throw Exception("getActiveNetworkDnsResolvers failed", e)
        }
    }

    fun getNetworkDnsServer(context: Context): List<String?> {
        return try {
            getActiveNetworkDnsResolver(context)
        } catch (_: Exception) {
            listOf(DEFAULT_PRIMARY_DNS_SERVER, DEFAULT_SECONDARY_DNS_SERVER)
        }
    }

    @Throws(Exception::class)
    fun selectPrivateAddress(): PrivateAddress {
        val candidates = mutableListOf(
            PrivateAddress("10.0.0.1", "10.0.0.0", 8, "10.0.0.2"),
            PrivateAddress("172.16.0.1", "172.16.0.0", 12, "172.16.0.2"),
            PrivateAddress("192.168.0.1", "192.168.0.0", 16, "192.168.0.2"),
            PrivateAddress("169.254.1.1", "169.254.1.0", 24, "169.254.1.2")
        )

        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { networkInterface ->
                networkInterface.inetAddresses?.toList()?.forEach { inetAddress ->
                    if (inetAddress.isLoopbackAddress || inetAddress !is Inet4Address) return@forEach
                    val ipAddress = inetAddress.hostAddress
                    if (ipAddress != null) {
                        when {
                            ipAddress.startsWith("10.") -> removeCandidate(candidates, "10.")
                            is172Range(ipAddress) -> removeCandidate(candidates, "172.")
                            ipAddress.startsWith("192.168") -> removeCandidate(candidates, "192.")
                        }
                    }
                }
            }
        } catch (e: SocketException) {
            throw Exception("selectPrivateAddress failed", e)
        }

        if (candidates.isEmpty()) throw Exception("no private address available")
        return candidates[0]
    }

    private fun removeCandidate(candidates: MutableList<PrivateAddress>, prefix: String) {
        candidates.removeIf { it.ipAddress.startsWith(prefix) }
    }

    private fun is172Range(ipAddress: String): Boolean {
        if (!ipAddress.startsWith("172.")) return false
        val parts = ipAddress.split(".")
        if (parts.size < 2) return false
        return try {
            val secondOctet = parts[1].toInt()
            secondOctet in 16..31
        } catch (_: NumberFormatException) {
            false
        }
    }

    private fun isPortAvailable(port: Int): Boolean {
        return try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1000) }
            false
        } catch (_: SocketTimeoutException) {
            false
        } catch (_: IOException) {
            true
        }
    }

    fun findAvailablePort(startPort: Int, maxIncrement: Int): Int {
        return (startPort until startPort + maxIncrement)
            .firstOrNull { isPortAvailable(it) } ?: 0
    }
}