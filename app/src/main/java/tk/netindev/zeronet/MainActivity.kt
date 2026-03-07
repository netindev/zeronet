package tk.netindev.zeronet

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Network
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.netindev.zeronet.vpn.ZeroNetService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient
import okhttp3.Request
import tk.netindev.zeronet.data.PayloadManager
import tk.netindev.zeronet.data.RemoteProxyConfig
import tk.netindev.zeronet.service.config.Settings
import tk.netindev.zeronet.service.util.AppLog
import tk.netindev.zeronet.service.util.ConnectionStatus
import tk.netindev.zeronet.service.util.ConnectionStatusManager
import tk.netindev.zeronet.ui.components.ConnectionStatusBadge
import tk.netindev.zeronet.ui.components.NetworkTypeBadge
import tk.netindev.zeronet.ui.components.ConnectionStatsCard
import tk.netindev.zeronet.ui.components.OperatorConfigCard
import tk.netindev.zeronet.ui.components.PayloadConfigCard
import tk.netindev.zeronet.ui.components.MenuDropdown
import tk.netindev.zeronet.ui.components.RemoteProxyDialog
import tk.netindev.zeronet.ui.components.LogViewer
import tk.netindev.zeronet.ui.components.ConnectionStatusModal
import tk.netindev.zeronet.ui.components.ConnectionInfoModal
import tk.netindev.zeronet.ui.screens.AboutScreen
import tk.netindev.zeronet.ui.screens.SettingsScreen
import tk.netindev.zeronet.ui.screens.SshConfigScreen
import tk.netindev.zeronet.ui.screens.StatsScreen
import tk.netindev.zeronet.ui.screens.TweaksScreen
import tk.netindev.zeronet.ui.theme.ZeronetTheme

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            selectedPayloadForTunnel?.let { payloadName ->
                startTunnelWithPayload()
            }
        }
    }

    val phoneStatePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private var selectedPayloadForTunnel: String? = null
    private var selectedOperatorForTunnel: String? = null

    private var isRunning = false;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ZeronetTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        ZeronetApp(
                            onStartTunnel = { selectedPayload, selectedOperator ->
                                requestVpnPermission(selectedPayload, selectedOperator)
                            },
                            onStopTunnel = { stopTunnel() },
                            context = this@MainActivity,
                            onRequestPhoneStatePermission = {
                                phoneStatePermissionLauncher.launch(android.Manifest.permission.READ_PHONE_STATE)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestVpnPermission(payloadName: String, operator: String) {
        selectedPayloadForTunnel = payloadName
        selectedOperatorForTunnel = operator
        
        if (isActiveVpn()) {
            AppLog.d("MainActivity", "Another VPN service is already running, stop it first")
            return
        }
        
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startTunnelWithPayload()
        }
    }
    
    private fun isActiveVpn(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun startTunnelWithPayload() {
        selectedPayloadForTunnel?.let { payloadName ->
            val intent = Intent(this, ZeroNetService::class.java)
            startService(intent)
            
            isRunning = true
        }
    }

    private fun stopTunnel() {
        val intent = Intent(this, ZeroNetService::class.java)
        stopService(intent)
        
        isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isRunning) {
            stopTunnel()
        }
    }
}

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
    var showConfigManagerScreen by remember { mutableStateOf(false) }
    var showSshConfigScreen by remember { mutableStateOf(false) }
    var showAboutScreen by remember { mutableStateOf(false) }
    var showStatsScreen by remember { mutableStateOf(false) }
    var showTweaksScreen by remember { mutableStateOf(false) }
    var selectedOperator by remember { mutableStateOf("") }
    var selectedPayload by remember { mutableStateOf("") }

    var isCustomPayloadEnabled by remember { mutableStateOf(false) }
    var customPayloadText by remember { mutableStateOf("") }
    var remoteProxyConfig by remember { mutableStateOf(RemoteProxyConfig()) }
    var showRemoteProxyDialog by remember { mutableStateOf(false) }
    var showConnectionStatusModal by remember { mutableStateOf(false) }
    var showConnectionInfoModal by remember { mutableStateOf(false) }
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

    var isAnimating by remember { mutableStateOf(false) }

    val operators = PayloadManager.getOperators()
    val availablePayloads = PayloadManager.getPayloadsForOperator(selectedOperator)

    var sshConfigValid by remember { mutableStateOf(false) }

    fun validateSshConfig() {
        val settings = Settings(context)
        val sshConfig = settings.getSshConfig()
        if (sshConfig.host.isNotEmpty() && sshConfig.username.isNotEmpty() && sshConfig.password.isNotEmpty()) {
            sshConfigValid = true
        } else {
            sshConfigValid = false
        }
    }
    
    fun handleLogoClick() {
        if (!isAnimating) {
            isAnimating = true
        }
    }

    LaunchedEffect(isAnimating) {
        if (isAnimating) {
            delay(1500)
            isAnimating = false
        }
    }

    LaunchedEffect(Unit) {
        validateSshConfig()
        
        
        launch {
            ConnectionStatusManager.status.collectLatest { status ->
                connectionStatus = status
                isRunning = status == ConnectionStatus.LEVEL_CONNECTED || status == ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET || status == ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED || status == ConnectionStatus.LEVEL_START
                if (status == ConnectionStatus.LEVEL_CONNECTED && connectedSince == 0L) {
                    connectedSince = System.currentTimeMillis()
                    tk.netindev.zeronet.service.util.ConnectionStatsManager.setConnectedSince(connectedSince)
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
                    // Clear the global stats singleton so stale data doesn't persist
                    tk.netindev.zeronet.service.util.ConnectionStatsManager.reset()
                }
            }
        }
        launch {
            tk.netindev.zeronet.service.util.ConnectionStatsManager.stats.collectLatest { s ->
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
                    geoText = listOfNotNull(s.city.takeIf { it.isNotEmpty() }, s.region.takeIf { it.isNotEmpty() }, s.country.takeIf { it.isNotEmpty() }).joinToString(", ")
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
                        tk.netindev.zeronet.service.util.ConnectionStatsManager.setGeoDetailed(ip, city, region, countryName, org, countryCode)
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
            return Pair(
                false,
                "SSH configuration is incomplete. Please configure SSH settings first."
            )
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

    AnimatedContent(
        targetState = when {
            showSettingsScreen -> "settings"
            showConfigManagerScreen -> "config_manager"
            showSshConfigScreen -> "ssh_config"
            showAboutScreen -> "about"
            showStatsScreen -> "stats"
            showTweaksScreen -> "tweaks"
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
                SettingsScreen(
                    onNavigateBack = { showSettingsScreen = false },
                )
            }

            "ssh_config" -> {
                SshConfigScreen(
                    onNavigateBack = { showSshConfigScreen = false },
                    onSshConfigSave = { config ->
                        validateSshConfig()
                    },
                    context = context
                )
            }

            "about" -> {
                AboutScreen(
                    onNavigateBack = { showAboutScreen = false }
                )
            }

            "stats" -> {
                StatsScreen(
                    onNavigateBack = { showStatsScreen = false }
                )
            }

            "tweaks" -> {
                TweaksScreen(
                    onNavigateBack = { showTweaksScreen = false }
                )
            }


            "main" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Fixed top bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                ConnectionStatusBadge(
                                    status = connectionStatus,
                                    onClick = { showConnectionStatusModal = true }
                                )
                                NetworkTypeBadge(onRequestPhoneStatePermission = onRequestPhoneStatePermission)
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { showSshConfigScreen = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "SSH Configuration",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }

                                IconButton(
                                    onClick = { showSettingsScreen = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }

                                MenuDropdown(
                                    onPresetsClick = { showConfigManagerScreen = true },
                                    onStatsClick = { showStatsScreen = true },
                                    onTweaksClick = { showTweaksScreen = true },
                                    onAboutClick = { showAboutScreen = true },
                                    onExitClick = {
                                        android.os.Process.killProcess(android.os.Process.myPid())
                                    }
                                )
                            }
                        }

                        // Scrollable middle content
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val rotation by animateFloatAsState(
                                    targetValue = if (isAnimating) 360f else 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.6f,
                                        stiffness = 100f
                                    ),
                                    label = "rotation"
                                )

                                val scale by animateFloatAsState(
                                    targetValue = if (isAnimating) 1.2f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = 0.6f,
                                        stiffness = 100f
                                    ),
                                    label = "scale"
                                )

                                Icon(
                                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                    contentDescription = "App Icon",
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() }
                                        ) { handleLogoClick() }
                                        .rotate(rotation)
                                        .scale(scale),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "ZeroNet",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                Text(
                                    text = "by netindev",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (connectionStatus == ConnectionStatus.LEVEL_NOT_CONNECTED) {
                                OperatorConfigCard(
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
                                    }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                PayloadConfigCard(
                                    isCustomPayloadEnabled = isCustomPayloadEnabled,
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
                                            settings.setDnsttConfig(Settings.DnsttConfig(
                                                dnsServer = payloadItem.dnsttDnsServer,
                                                tunnelDomain = payloadItem.dnsttTunnelDomain,
                                                publicKey = payloadItem.dnsttPublicKey
                                            ))
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
                                    onRemoteProxyClick = { showRemoteProxyDialog = true },
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
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (connectionStatus != ConnectionStatus.LEVEL_NOT_CONNECTED) {
                                ConnectionStatsCard(
                                    pingMs = pingMs,
                                    avgUploadKbps = avgUploadKbps,
                                    avgDownloadKbps = avgDownloadKbps,
                                    sshHost = Settings(context).getString(Settings.SSH_HOST_KEY),
                                    payloadName = if (isCustomPayloadEnabled) "Custom" else {
                                        if (selectedPayload.isNotEmpty()) {
                                            selectedPayload.split(" ").lastOrNull() ?: selectedPayload
                                        } else {
                                            selectedPayload
                                        }
                                    },
                                    connectionDurationText = uptimeText,
                                    geoIp = geoIp,
                                    geoCity = geoCity,
                                    geoRegion = geoRegion,
                                    geoCountry = geoCountry,
                                    geoOrg = geoOrg,
                                    geoCountryCode = geoCountryCode,
                                    geoStatus = geoStatus,
                                    onConnectionInfoClick = { showConnectionInfoModal = true }
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            Button(
                                onClick = {
                                    if (connectionStatus == ConnectionStatus.LEVEL_NOT_CONNECTED) {
                                        val (isValid, errorMessage) = validateTunnelData()

                                        if (isValid) {
                                            val payloadToUse =
                                                if (isCustomPayloadEnabled) customPayloadText else selectedPayload

                                            onStartTunnel(payloadToUse, selectedOperator)
                                            isRunning = true
                                        }
                                    } else {
                                        onStopTunnel()
                                        isRunning = false
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                enabled = connectionStatus != ConnectionStatus.LEVEL_NOT_CONNECTED || validateTunnelData().first,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when {
                                        connectionStatus != ConnectionStatus.LEVEL_NOT_CONNECTED -> Color(0xFFB71C1C)
                                        validateTunnelData().first -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                if (connectionStatus == ConnectionStatus.LEVEL_NOT_CONNECTED) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Start",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = when {
                                        connectionStatus != ConnectionStatus.LEVEL_NOT_CONNECTED -> "Stop"
                                        validateTunnelData().first -> "Start"
                                        else -> "Configure Required"
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Fixed bottom log viewer
                        LogViewer(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Dialogs / modals (overlays)
                    RemoteProxyDialog(
                        isVisible = showRemoteProxyDialog,
                        onDismiss = { showRemoteProxyDialog = false },
                        onConfirm = { config ->
                            remoteProxyConfig = config
                            val settings = Settings(context)
                            settings.setString(Settings.REMOTE_PROXY_HOST_KEY, config.host)
                            settings.setString(Settings.REMOTE_PROXY_PORT_KEY, config.port)
                            settings.setLastConfigIsPredefined(false)
                        },
                        initialConfig = remoteProxyConfig
                    )

                    ConnectionStatusModal(
                        isVisible = showConnectionStatusModal,
                        onDismiss = { showConnectionStatusModal = false },
                        currentStatus = connectionStatus
                    )

                    ConnectionInfoModal(
                        isVisible = showConnectionInfoModal,
                        onDismiss = { showConnectionInfoModal = false },
                        geoIp = geoIp,
                        geoCity = geoCity,
                        geoRegion = geoRegion,
                        geoCountry = geoCountry,
                        geoOrg = geoOrg,
                        geoCountryCode = geoCountryCode
                    )
                }
            }
        }
    }
}



