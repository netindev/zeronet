package tk.netindev.zeronet.service.config

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.core.content.edit

class Settings(context: Context) {
    
    companion object {
        const val SSH_HOST_KEY = "sshServer"
        const val SSH_PORT_KEY = "sshPort"
        const val SSH_USERNAME_KEY = "sshUser"
        const val SSH_PASSWORD_KEY = "sshPass"
        const val OPERATOR_KEY = "operator"
        const val PAYLOAD_KEY = "payload"
        const val CUSTOM_PAYLOAD_KEY = "proxyPayload"
        const val REMOTE_PROXY_HOST_KEY = "proxyRemoto"
        const val REMOTE_PROXY_PORT_KEY = "proxyRemotoPorta"
        const val DNS_RESOLVER_KEY = "dnsResolver"
        const val DNS_FORWARD_KEY = "dnsForward"
        const val ENABLE_TETHERING_KEY = "enable_tethering"
        const val TUNNEL_TYPE_KEY = "tunnelType"
        const val SSH_PING_INTERVAL_KEY = "pingerSSH"
        const val APP_FILTER_LIST_KEY = "filterAppsList"
        const val DISABLE_TCP_DELAY_KEY = "disableDelaySSH"
        const val CUSTOM_PAYLOAD_ENABLED_KEY = "customPayloadEnabled"
        const val LAST_CONFIG_IS_PREDEFINED_KEY = "lastConfigIsPredefined"
        const val THREAD_POOL_COUNT_KEY = "threadPoolCount"

        const val SSH_LOCAL_PORT = "sshLocalPort"
        const val FILTER_APPS = "filterApps"
        const val FILTER_BYPASS_MODE = "filterBypassMode"

        const val UDPFORWARD_KEY = "udpforward"

        const val TOTAL_UPTIME_KEY = "total_uptime_seconds"
        const val TOTAL_DOWNLOAD_BYTES_KEY = "total_download_bytes"
        const val TOTAL_UPLOAD_BYTES_KEY = "total_upload_bytes"
        const val LAST_CONNECTION_START_KEY = "last_connection_start"
        const val UDPRESOLVER_KEY = "udpresolver"
        const val KEYPATH_KEY = "keyPath"

        const val SSL_SNI_HOST_KEY = "sslSniHost"
        const val SSL_PORT_KEY = "sslPort"

        const val DNSTT_DNS_SERVER_KEY = "dnsttDnsServer"
        const val DNSTT_TUNNEL_DOMAIN_KEY = "dnsttTunnelDomain"
        const val DNSTT_PUBLIC_KEY_KEY = "dnsttPublicKey"
        const val DNSTT_PROXY_PORT_KEY = "dnsttProxyPort"

        const val MNO_FREE_INTERNET_OPERATOR_KEY = "mnoFreeInternetOperator"
        const val MNO_FREE_INTERNET_HOST_KEY = "mnoFreeInternetHost"
        const val MNO_FREE_INTERNET_PORT_KEY = "mnoFreeInternetPort"
        const val MNO_FREE_INTERNET_PHONE_KEY = "mnoFreeInternetPhone"
        const val MNO_FREE_INTERNET_API_KEY = "mnoFreeInternetApiKey"
    }
    
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun getString(key: String): String {
        val defaultStr = when (key) {
            SSH_LOCAL_PORT -> "1080"
            SSH_PORT_KEY -> "22"
            SSH_USERNAME_KEY -> ""
            SSH_PASSWORD_KEY -> ""
            OPERATOR_KEY -> "TIM"
            PAYLOAD_KEY -> ""
            CUSTOM_PAYLOAD_KEY -> ""
            REMOTE_PROXY_HOST_KEY -> ""
            REMOTE_PROXY_PORT_KEY -> "0"
            DNS_RESOLVER_KEY -> "8.8.8.8"
            SSH_PING_INTERVAL_KEY -> "30"
            CUSTOM_PAYLOAD_ENABLED_KEY -> "false"
            LAST_CONFIG_IS_PREDEFINED_KEY -> "true"
            SSL_SNI_HOST_KEY -> ""
            SSL_PORT_KEY -> "443"
            DNSTT_DNS_SERVER_KEY -> "8.8.8.8"
            DNSTT_TUNNEL_DOMAIN_KEY -> ""
            DNSTT_PUBLIC_KEY_KEY -> ""
            DNSTT_PROXY_PORT_KEY -> "7000"
            MNO_FREE_INTERNET_OPERATOR_KEY -> "TIM"
            MNO_FREE_INTERNET_HOST_KEY -> ""
            MNO_FREE_INTERNET_PORT_KEY -> "8080"
            MNO_FREE_INTERNET_PHONE_KEY -> ""
            MNO_FREE_INTERNET_API_KEY -> ""
            else -> ""
        }
        return prefs.getString(key, defaultStr) ?: defaultStr
    }

    fun setString(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun setBoolean(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return prefs.getInt(key, defaultValue)
    }

    fun setInt(key: String, value: Int) {
        prefs.edit { putInt(key, value) }
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return prefs.getLong(key, defaultValue)
    }

    fun setLong(key: String, value: Long) {
        prefs.edit { putLong(key, value) }
    }

    fun remove(key: String) {
        prefs.edit { remove(key) }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    fun contains(key: String): Boolean {
        return prefs.contains(key)
    }

    fun getSshPingInterval(): Int {
        return getString(SSH_PING_INTERVAL_KEY).toIntOrNull() ?: 30
    }

    fun setSshPingInterval(interval: Int) {
        setString(SSH_PING_INTERVAL_KEY, interval.toString())
    }

    fun getAppFilterList(): List<String> {
        val filterListString = this@Settings.getString(APP_FILTER_LIST_KEY, "")
        return if (filterListString.isBlank()) {
            emptyList()
        } else {
            filterListString.split(",").filter { it.isNotBlank() }
        }
    }

    fun setAppFilterList(packageNames: List<String>) {
        val filterListString = packageNames.joinToString(",")
        setString(APP_FILTER_LIST_KEY, filterListString)
    }

    fun getDisableTcpDelay(): Boolean {
        return getBoolean(DISABLE_TCP_DELAY_KEY, false)
    }

    fun setDisableTcpDelay(disable: Boolean) {
        setBoolean(DISABLE_TCP_DELAY_KEY, disable)
    }

    fun getThreadPoolCount(): Int {
        val value = getInt(THREAD_POOL_COUNT_KEY, 8)
        return when {
            value < 2 -> 2
            value > 30 -> 30
            else -> value
        }
    }

    fun setThreadPoolCount(count: Int) {
        val clamped = when {
            count < 2 -> 2
            count > 30 -> 30
            else -> count
        }
        setInt(THREAD_POOL_COUNT_KEY, clamped)
    }

    fun getSshConfig(): SshConfig {
        return SshConfig(
            host = getString(SSH_HOST_KEY),
            port = getString(SSH_PORT_KEY).toIntOrNull() ?: 22,
            username = getString(SSH_USERNAME_KEY),
            password = getString(SSH_PASSWORD_KEY)
        )
    }

    fun setSshConfig(config: SshConfig) {
        setString(SSH_HOST_KEY, config.host)
        setString(SSH_PORT_KEY, config.port.toString())
        setString(SSH_USERNAME_KEY, config.username)
        setString(SSH_PASSWORD_KEY, config.password)
    }

    fun getTunnelConfig(): TunnelConfig {
        return TunnelConfig(
            operator = getString(OPERATOR_KEY),
            payload = getString(PAYLOAD_KEY),
            customPayload = getString(CUSTOM_PAYLOAD_KEY),
            dnsResolver = getString(DNS_RESOLVER_KEY),
            dnsForward = getBoolean(DNS_FORWARD_KEY),
            enableTethering = getBoolean(ENABLE_TETHERING_KEY),
            tunnelType = this@Settings.getString(TUNNEL_TYPE_KEY, "SSH_DIRECT")
        )
    }

    fun getSSHKeypath(): String {
        return this@Settings.getString(KEYPATH_KEY, "")
    }

    fun getSSHPinger(): Int {
        return getString(SSH_PING_INTERVAL_KEY).toIntOrNull() ?: 3
    }

    fun getIsDisabledDelaySSH(): Boolean {
        return getBoolean(DISABLE_TCP_DELAY_KEY, false)
    }

    fun getVpnDnsForward(): Boolean {
        return getBoolean(DNS_FORWARD_KEY, true)
    }

    fun getVpnDnsResolver(): String {
        return getString(DNS_RESOLVER_KEY)
    }

    fun getVpnUdpForward(): Boolean {
        return getBoolean(UDPFORWARD_KEY, false)
    }

    fun getVpnUdpResolver(): String {
        return this@Settings.getString(UDPRESOLVER_KEY, "127.0.0.1:7300")
    }

    fun getIsFilterApps(): Boolean {
        return getBoolean(FILTER_APPS, false)
    }

    fun getIsFilterBypassMode(): Boolean {
        return getBoolean(FILTER_BYPASS_MODE, false)
    }

    fun getFilterApps(): Array<String> {
        val txt = this@Settings.getString(APP_FILTER_LIST_KEY, "")
        return if (txt.isEmpty()) {
            arrayOf()
        } else {
            txt.split("\n").toTypedArray()
        }
    }

    fun getIsTetheringSubnet(): Boolean {
        return getBoolean(ENABLE_TETHERING_KEY, false)
    }

    fun getPrefsPrivate(): SharedPreferences {
        return prefs
    }

    fun getCustomPayloadEnabled(): Boolean {
        return getString(CUSTOM_PAYLOAD_ENABLED_KEY).toBoolean()
    }

    fun setCustomPayloadEnabled(enabled: Boolean) {
        setString(CUSTOM_PAYLOAD_ENABLED_KEY, enabled.toString())
    }

    fun getLastConfigIsPredefined(): Boolean {
        return getString(LAST_CONFIG_IS_PREDEFINED_KEY).toBoolean()
    }

    fun getTotalUptimeSeconds(): Long {
        return getLong(TOTAL_UPTIME_KEY, 0L)
    }
    
    fun setTotalUptimeSeconds(seconds: Long) {
        setLong(TOTAL_UPTIME_KEY, seconds)
    }
    
    fun getTotalDownloadBytes(): Long {
        return getLong(TOTAL_DOWNLOAD_BYTES_KEY, 0L)
    }
    
    fun setTotalDownloadBytes(bytes: Long) {
        setLong(TOTAL_DOWNLOAD_BYTES_KEY, bytes)
    }
    
    fun getTotalUploadBytes(): Long {
        return getLong(TOTAL_UPLOAD_BYTES_KEY, 0L)
    }
    
    fun setTotalUploadBytes(bytes: Long) {
        setLong(TOTAL_UPLOAD_BYTES_KEY, bytes)
    }
    
    fun setLastConnectionStart(timestamp: Long) {
        setLong(LAST_CONNECTION_START_KEY, timestamp)
    }

    fun setLastConfigIsPredefined(isPredefined: Boolean) {
        setString(LAST_CONFIG_IS_PREDEFINED_KEY, isPredefined.toString())
    }

    fun getDnsttConfig(): DnsttConfig {
        return DnsttConfig(
            dnsServer = getString(DNSTT_DNS_SERVER_KEY),
            tunnelDomain = getString(DNSTT_TUNNEL_DOMAIN_KEY),
            publicKey = getString(DNSTT_PUBLIC_KEY_KEY),
            proxyPort = getString(DNSTT_PROXY_PORT_KEY).toIntOrNull() ?: 7000
        )
    }

    fun setDnsttConfig(config: DnsttConfig) {
        setString(DNSTT_DNS_SERVER_KEY, config.dnsServer)
        setString(DNSTT_TUNNEL_DOMAIN_KEY, config.tunnelDomain)
        setString(DNSTT_PUBLIC_KEY_KEY, config.publicKey)
        setString(DNSTT_PROXY_PORT_KEY, config.proxyPort.toString())
    }

    data class DnsttConfig(
        val dnsServer: String = "8.8.8.8",
        val tunnelDomain: String = "",
        val publicKey: String = "",
        val proxyPort: Int = 7000
    )

    data class SshConfig(
        val host: String,
        val port: Int,
        val username: String,
        val password: String
    )
    
    data class TunnelConfig(
        val operator: String,
        val payload: String,
        val customPayload: String,
        val dnsResolver: String,
        val dnsForward: Boolean,
        val enableTethering: Boolean,
        val tunnelType: String
    )
}
