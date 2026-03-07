package tk.netindev.zeronet.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tk.netindev.zeronet.data.PayloadManager
import tk.netindev.zeronet.data.RemoteProxyConfig
import tk.netindev.zeronet.service.config.Settings
import tk.netindev.zeronet.service.util.ConnectionStatus
import tk.netindev.zeronet.service.util.ConnectionStatsManager
import tk.netindev.zeronet.service.util.ConnectionStatusManager
import tk.netindev.zeronet.ui.components.DnsttWarningDialog
import tk.netindev.zeronet.ui.screens.AboutScreen
import tk.netindev.zeronet.ui.screens.HomeScreen
import tk.netindev.zeronet.ui.screens.PresetsScreen
import tk.netindev.zeronet.ui.screens.SettingsScreen
import tk.netindev.zeronet.ui.screens.SshConfigScreen
import tk.netindev.zeronet.ui.screens.StatsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeronetApp(
    onStartTunnel: (String, String) -> Unit,
    onStopTunnel: () -> Unit,
    context: android.content.Context,
    onRequestPhoneStatePermission: () -> Unit = {},
) {
    var isRunning by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf(ConnectionStatus.LEVEL_NOT_CONNECTED) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showPresetsScreen by remember { mutableStateOf(false) }
    var showSshConfigScreen by remember { mutableStateOf(false) }
    var showAboutScreen by remember { mutableStateOf(false) }
    var showStatsScreen by remember { mutableStateOf(false) }
    var selectedOperator by remember { mutableStateOf("") }
    var selectedPayload by remember { mutableStateOf("") }

    var isCustomPayloadEnabled by remember { mutableStateOf(false) }
    var customPayloadText by remember { mutableStateOf("") }
    var remoteProxyConfig by remember { mutableStateOf(RemoteProxyConfig()) }
    var tunnelType by remember { mutableStateOf("SSH_DIRECT") }
    var sniHost by remember { mutableStateOf("") }
    var dnsttDnsServer by remember { mutableStateOf("8.8.8.8") }
    var dnsttTunnelDomain by remember { mutableStateOf("") }
    var dnsttPublicKey by remember { mutableStateOf("") }

    var avgUploadKbps by remember { mutableStateOf(0.0) }
    var avgDownloadKbps by remember { mutableStateOf(0.0) }
    var connectedSince by remember { mutableStateOf(0L) }
    var geoText by remember { mutableStateOf("") }
    var geoIp by remember { mutableStateOf("") }
    var geoCity by remember { mutableStateOf("") }
    var geoRegion by remember { mutableStateOf("") }
    var geoCountry by remember { mutableStateOf("") }
    var geoOrg by remember { mutableStateOf("") }
    var geoCountryCode by remember { mutableStateOf("") }
    var geoStatus by remember { mutableStateOf("") }
    var pingMs by remember { mutableStateOf(-1L) }
    var uptimeText by remember { mutableStateOf("--:--:--") }

    val operators = PayloadManager.getOperators()
    val availablePayloads = PayloadManager.getPayloadsForOperator(selectedOperator)

    var sshConfigValid by remember { mutableStateOf(false) }

    var showDnsttWarning by remember { mutableStateOf(false) }
    var pendingDnsttStart by remember { mutableStateOf(false) }

    fun validateSshConfig() {
        val settings = Settings(context)
        val sshConfig = settings.getSshConfig()
        sshConfigValid = sshConfig.host.isNotEmpty() && sshConfig.username.isNotEmpty() && sshConfig.password.isNotEmpty()
    }

    fun reloadSettingsState() {
        val settings = Settings(context)
        selectedOperator = settings.getString(Settings.OPERATOR_KEY)
        selectedPayload = settings.getString(Settings.PAYLOAD_KEY)

        val proxyHost = settings.getString(Settings.REMOTE_PROXY_HOST_KEY)
        val proxyPort = settings.getString(Settings.REMOTE_PROXY_PORT_KEY).toIntOrNull() ?: 0
        val hasProxyConfigured = proxyHost.isNotEmpty() && proxyPort > 0

        val savedTunnelType = settings.getString(Settings.TUNNEL_TYPE_KEY, "SSH_DIRECT")
        tunnelType = when (savedTunnelType) {
            "DNSTT" -> "DNSTT"
            "SSH_SSL_TUNNEL" -> "SSH_SSL_TUNNEL"
            "SSH_PROXY" -> if (hasProxyConfigured) "SSH_PROXY" else "SSH_DIRECT"
            else -> if (hasProxyConfigured) "SSH_PROXY" else "SSH_DIRECT"
        }
        sniHost = settings.getString(Settings.SSL_SNI_HOST_KEY, "")

        val dnsttConfig = settings.getDnsttConfig()
        dnsttDnsServer = dnsttConfig.dnsServer
        dnsttTunnelDomain = dnsttConfig.tunnelDomain
        dnsttPublicKey = dnsttConfig.publicKey

        val lastConfigIsPredefined = settings.getLastConfigIsPredefined()
        val savedCustomPayloadText = settings.getString(Settings.CUSTOM_PAYLOAD_KEY)
        val customPayloadEnabled = settings.getCustomPayloadEnabled()

        isCustomPayloadEnabled = customPayloadEnabled || (!lastConfigIsPredefined && savedCustomPayloadText.isNotEmpty())
        customPayloadText = savedCustomPayloadText
        if (hasProxyConfigured) {
            remoteProxyConfig = RemoteProxyConfig(proxyHost, proxyPort.toString())
        } else {
            remoteProxyConfig = RemoteProxyConfig()
        }

        validateSshConfig()
    }

    LaunchedEffect(Unit) {
        validateSshConfig()

        launch {
            ConnectionStatusManager.status.collectLatest { status ->
                connectionStatus = status
                isRunning = status == ConnectionStatus.LEVEL_CONNECTED ||
                        status == ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET ||
                        status == ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED ||
                        status == ConnectionStatus.LEVEL_START
                if (status == ConnectionStatus.LEVEL_CONNECTED && connectedSince == 0L) {
                    connectedSince = System.currentTimeMillis()
                    ConnectionStatsManager.setConnectedSince(connectedSince)
                }
                if (status == ConnectionStatus.LEVEL_NOT_CONNECTED) {
                    connectedSince = 0L
                    avgUploadKbps = 0.0
                    avgDownloadKbps = 0.0
                    geoText = ""
                    geoIp = ""
                    geoCity = ""
                    geoRegion = ""
                    geoCountry = ""
                    geoOrg = ""
                    geoCountryCode = ""
                    geoStatus = ""
                    pingMs = -1L
                    uptimeText = "--:--:--"
                    ConnectionStatsManager.reset()
                }
            }
        }
        launch {
            ConnectionStatsManager.stats.collectLatest { s ->
                pingMs = s.pingMs
                avgUploadKbps = s.avgUploadKbps
                avgDownloadKbps = s.avgDownloadKbps
                if (s.city.isNotEmpty() || s.country.isNotEmpty()) {
                    geoIp = s.ip
                    geoCity = s.city
                    geoRegion = s.region
                    geoCountry = s.country
                    geoOrg = s.org
                    geoCountryCode = s.countryCode
                    geoText = listOfNotNull(
                        s.city.takeIf { it.isNotEmpty() },
                        s.region.takeIf { it.isNotEmpty() },
                        s.country.takeIf { it.isNotEmpty() }
                    ).joinToString(", ")
                }
            }
        }
        val settings = Settings(context)
        selectedOperator = settings.getString(Settings.OPERATOR_KEY)
        selectedPayload = settings.getString(Settings.PAYLOAD_KEY)

        val proxyHost = settings.getString(Settings.REMOTE_PROXY_HOST_KEY)
        val proxyPort = settings.getString(Settings.REMOTE_PROXY_PORT_KEY).toIntOrNull() ?: 0
        val hasProxyConfigured = proxyHost.isNotEmpty() && proxyPort > 0

        val savedTunnelType = settings.getString(Settings.TUNNEL_TYPE_KEY, "SSH_DIRECT")
        tunnelType = when (savedTunnelType) {
            "DNSTT" -> "DNSTT"
            "SSH_SSL_TUNNEL" -> "SSH_SSL_TUNNEL"
            "SSH_PROXY" -> if (hasProxyConfigured) "SSH_PROXY" else "SSH_DIRECT"
            else -> if (hasProxyConfigured) "SSH_PROXY" else "SSH_DIRECT"
        }
        sniHost = settings.getString(Settings.SSL_SNI_HOST_KEY, "")

        val dnsttConfig = settings.getDnsttConfig()
        dnsttDnsServer = dnsttConfig.dnsServer
        dnsttTunnelDomain = dnsttConfig.tunnelDomain
        dnsttPublicKey = dnsttConfig.publicKey

        if (savedTunnelType != tunnelType) {
            settings.setString(Settings.TUNNEL_TYPE_KEY, tunnelType)
        }

        val lastConfigIsPredefined = settings.getLastConfigIsPredefined()
        val savedCustomPayloadText = settings.getString(Settings.CUSTOM_PAYLOAD_KEY)
        val customPayloadEnabled = settings.getCustomPayloadEnabled()

        isCustomPayloadEnabled = customPayloadEnabled || (!lastConfigIsPredefined && savedCustomPayloadText.isNotEmpty())

        if (isCustomPayloadEnabled) {
            customPayloadText = savedCustomPayloadText
            if (hasProxyConfigured) {
                remoteProxyConfig = RemoteProxyConfig(proxyHost, proxyPort.toString())
            }
        }
    }

    LaunchedEffect(connectionStatus, connectedSince) {
        if (connectionStatus == ConnectionStatus.LEVEL_CONNECTED && connectedSince > 0L) {
            geoStatus = "Waiting connection to establish fully..."
            delay(5000)
            withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient()
                    val request = Request.Builder().url("https://ipwho.is/").get().build()
                    client.newCall(request).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        fun parseVal(key: String): String {
                            val idx = body.indexOf("\"$key\":")
                            if (idx < 0) return ""
                            val start = body.indexOf('"', idx + key.length + 3) + 1
                            val end = body.indexOf('"', start)
                            if (start < 1 || end < 0) return ""
                            return body.substring(start, end)
                        }
                        val ip = parseVal("ip")
                        val city = parseVal("city")
                        val region = parseVal("region")
                        val countryName = parseVal("country")
                        var org = parseVal("org")
                        if (org.isEmpty()) {
                            val idx = body.indexOf("\"connection\":")
                            if (idx >= 0) {
                                val sub = body.substring(idx)
                                val oidx = sub.indexOf("\"org\":")
                                if (oidx >= 0) {
                                    val s = sub.indexOf('"', oidx + 6) + 1
                                    val e = sub.indexOf('"', s)
                                    if (s > 0 && e > s) org = sub.substring(s, e)
                                }
                            }
                        }
                        val countryCode = parseVal("country_code")
                        ConnectionStatsManager.setGeoDetailed(ip, city, region, countryName, org, countryCode)
                        geoStatus = ""
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    geoStatus = "Failed to fetch IP data"
                }
            }
            while (connectionStatus == ConnectionStatus.LEVEL_CONNECTED && connectedSince > 0L) {
                val secs = ((System.currentTimeMillis() - connectedSince) / 1000).toInt()
                val h = secs / 3600
                val m = (secs % 3600) / 60
                val s = secs % 60
                uptimeText = String.format("%02d:%02d:%02d", h, m, s)
                delay(1000)
            }
        } else {
            uptimeText = "--:--:--"
        }
    }

    fun validateTunnelData(): Pair<Boolean, String> {
        if (tunnelType == "DNSTT") {
            if (dnsttTunnelDomain.isBlank()) {
                return Pair(false, "DNSTT tunnel domain is required.")
            }
            if (dnsttPublicKey.isBlank()) {
                return Pair(false, "DNSTT public key is required.")
            }
            if (!sshConfigValid) {
                return Pair(false, "SSH configuration is required for DNSTT mode.")
            }
            return Pair(true, "DNSTT + SSH configuration is valid.")
        }

        if (!sshConfigValid) {
            return Pair(false, "SSH configuration is incomplete. Please configure SSH settings first.")
        }
        if (selectedOperator.isEmpty()) {
            return Pair(false, "Please select an operator (MNO) first.")
        }
        if (isCustomPayloadEnabled) {
            if (customPayloadText.isEmpty()) {
                return Pair(false, "Please enter a custom payload.")
            }
        } else {
            if (selectedPayload.isEmpty()) {
                return Pair(false, "Please select a payload.")
            }
        }
        if (isCustomPayloadEnabled && customPayloadText.trim().isEmpty()) {
            return Pair(false, "Custom payload text cannot be empty.")
        }
        return Pair(true, "All data is valid.")
    }

    fun performStart() {
        val payloadToUse = if (isCustomPayloadEnabled) customPayloadText else selectedPayload
        onStartTunnel(payloadToUse, selectedOperator)
        isRunning = true
    }

    fun handleStartClick() {
        val (isValid, _) = validateTunnelData()
        if (!isValid) return

        if (tunnelType == "DNSTT") {
            val settings = Settings(context)
            val dismissed = settings.getBoolean(Settings.DNSTT_WARNING_DISMISSED_KEY, false)
            if (!dismissed) {
                showDnsttWarning = true
                pendingDnsttStart = true
                return
            }
        }
        performStart()
    }

    if (showDnsttWarning) {
        DnsttWarningDialog(
            onDismiss = {
                showDnsttWarning = false
                pendingDnsttStart = false
            },
            onContinue = { doNotShowAgain ->
                if (doNotShowAgain) {
                    val settings = Settings(context)
                    settings.setBoolean(Settings.DNSTT_WARNING_DISMISSED_KEY, true)
                }
                showDnsttWarning = false
                if (pendingDnsttStart) {
                    pendingDnsttStart = false
                    performStart()
                }
            }
        )
    }

    AnimatedContent(
        targetState = when {
            showSettingsScreen -> "settings"
            showPresetsScreen -> "presets"
            showSshConfigScreen -> "ssh_config"
            showAboutScreen -> "about"
            showStatsScreen -> "stats"
            else -> "main"
        },
        transitionSpec = {
            val isGoingToSubScreen = targetState != "main"
            val isReturningToMain = initialState != "main"

            if (isGoingToSubScreen) {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(300)
                )
            } else if (isReturningToMain) {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(300)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300)
                )
            } else {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(300)
                )
            }
        },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            "settings" -> {
                SettingsScreen(onNavigateBack = { showSettingsScreen = false })
            }

            "ssh_config" -> {
                SshConfigScreen(
                    onNavigateBack = { showSshConfigScreen = false },
                    onSshConfigSave = { validateSshConfig() },
                    context = context
                )
            }

            "about" -> {
                AboutScreen(onNavigateBack = { showAboutScreen = false })
            }

            "stats" -> {
                StatsScreen(onNavigateBack = { showStatsScreen = false })
            }

            "presets" -> {
                PresetsScreen(
                    onNavigateBack = { showPresetsScreen = false },
                    onPresetApplied = { reloadSettingsState() }
                )
            }

            "main" -> {
                HomeScreen(
                    connectionStatus = connectionStatus,
                    selectedOperator = selectedOperator,
                    operators = operators,
                    onOperatorSelected = { operator ->
                        selectedOperator = operator
                        selectedPayload = ""
                        val settings = Settings(context)
                        settings.setString(Settings.OPERATOR_KEY, operator)
                        settings.setString(Settings.PAYLOAD_KEY, "")
                        settings.setLastConfigIsPredefined(true)
                        isCustomPayloadEnabled = false
                    },
                    isCustomPayloadEnabled = isCustomPayloadEnabled,
                    onCustomPayloadToggle = { enabled ->
                        isCustomPayloadEnabled = enabled
                        val settings = Settings(context)
                        settings.setCustomPayloadEnabled(enabled)
                        if (enabled) {
                            selectedPayload = ""
                            settings.setString(Settings.CUSTOM_PAYLOAD_KEY, customPayloadText)
                            settings.setLastConfigIsPredefined(false)
                        } else {
                            settings.setString(Settings.PAYLOAD_KEY, selectedPayload)
                            settings.setLastConfigIsPredefined(true)
                        }
                    },
                    selectedPayload = selectedPayload,
                    availablePayloads = availablePayloads,
                    onPayloadSelected = { payloadName ->
                        selectedPayload = payloadName
                        val settings = Settings(context)
                        settings.setString(Settings.PAYLOAD_KEY, payloadName)
                        settings.setLastConfigIsPredefined(true)

                        val payloadItem = PayloadManager.getPayloadItem(selectedOperator, payloadName)
                        if (payloadItem != null && payloadItem.tunnelType == "DNSTT") {
                            tunnelType = "DNSTT"
                            settings.setString(Settings.TUNNEL_TYPE_KEY, "DNSTT")
                            dnsttDnsServer = payloadItem.dnsttDnsServer
                            dnsttTunnelDomain = payloadItem.dnsttTunnelDomain
                            dnsttPublicKey = payloadItem.dnsttPublicKey
                            settings.setDnsttConfig(
                                Settings.DnsttConfig(
                                    dnsServer = payloadItem.dnsttDnsServer,
                                    tunnelDomain = payloadItem.dnsttTunnelDomain,
                                    publicKey = payloadItem.dnsttPublicKey
                                )
                            )
                        } else if (payloadItem != null) {
                            tunnelType = payloadItem.tunnelType
                            settings.setString(Settings.TUNNEL_TYPE_KEY, payloadItem.tunnelType)
                        }
                    },
                    customPayloadText = customPayloadText,
                    onCustomPayloadTextChange = {
                        customPayloadText = it
                        if (isCustomPayloadEnabled) {
                            val settings = Settings(context)
                            settings.setString(Settings.CUSTOM_PAYLOAD_KEY, it)
                            settings.setLastConfigIsPredefined(false)
                            settings.setCustomPayloadEnabled(true)
                        }
                    },
                    remoteProxyConfig = remoteProxyConfig,
                    onRemoteProxyConfirm = { config ->
                        remoteProxyConfig = config
                        val settings = Settings(context)
                        settings.setString(Settings.REMOTE_PROXY_HOST_KEY, config.host)
                        settings.setString(Settings.REMOTE_PROXY_PORT_KEY, config.port)
                        settings.setLastConfigIsPredefined(false)
                    },
                    tunnelType = tunnelType,
                    onTunnelTypeChange = { newType ->
                        tunnelType = newType
                        val settings = Settings(context)
                        settings.setString(Settings.TUNNEL_TYPE_KEY, newType)
                        settings.setCustomPayloadEnabled(newType != "SSH_DIRECT" && newType != "DNSTT")
                        if (newType == "SSH_DIRECT" || newType == "DNSTT") {
                            settings.setString(Settings.REMOTE_PROXY_HOST_KEY, "")
                            settings.setString(Settings.REMOTE_PROXY_PORT_KEY, "0")
                            remoteProxyConfig = RemoteProxyConfig()
                        }
                    },
                    sniHost = sniHost,
                    onSniHostChange = { newSni ->
                        sniHost = newSni
                        val settings = Settings(context)
                        settings.setString(Settings.SSL_SNI_HOST_KEY, newSni)
                    },
                    dnsttDnsServer = dnsttDnsServer,
                    onDnsttDnsServerChange = { newVal ->
                        dnsttDnsServer = newVal
                        val settings = Settings(context)
                        settings.setString(Settings.DNSTT_DNS_SERVER_KEY, newVal)
                    },
                    dnsttTunnelDomain = dnsttTunnelDomain,
                    onDnsttTunnelDomainChange = { newVal ->
                        dnsttTunnelDomain = newVal
                        val settings = Settings(context)
                        settings.setString(Settings.DNSTT_TUNNEL_DOMAIN_KEY, newVal)
                    },
                    dnsttPublicKey = dnsttPublicKey,
                    onDnsttPublicKeyChange = { newVal ->
                        dnsttPublicKey = newVal
                        val settings = Settings(context)
                        settings.setString(Settings.DNSTT_PUBLIC_KEY_KEY, newVal)
                    },
                    pingMs = pingMs,
                    avgUploadKbps = avgUploadKbps,
                    avgDownloadKbps = avgDownloadKbps,
                    sshHost = Settings(context).getString(Settings.SSH_HOST_KEY),
                    uptimeText = uptimeText,
                    geoIp = geoIp,
                    geoCity = geoCity,
                    geoRegion = geoRegion,
                    geoCountry = geoCountry,
                    geoOrg = geoOrg,
                    geoCountryCode = geoCountryCode,
                    geoStatus = geoStatus,
                    isValid = validateTunnelData().first,
                    onStartClick = { handleStartClick() },
                    onStopClick = {
                        onStopTunnel()
                        isRunning = false
                    },
                    onSshConfigClick = { showSshConfigScreen = true },
                    onSettingsClick = { showSettingsScreen = true },
                    onPresetsClick = { showPresetsScreen = true },
                    onStatsClick = { showStatsScreen = true },
                    onAboutClick = { showAboutScreen = true },
                    onExitClick = { android.os.Process.killProcess(android.os.Process.myPid()) },
                    onRequestPhoneStatePermission = onRequestPhoneStatePermission,
                )
            }
        }
    }
}
