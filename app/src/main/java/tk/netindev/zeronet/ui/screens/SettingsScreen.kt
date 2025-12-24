package tk.netindev.zeronet.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.netindev.zeronet.service.config.Settings
import tk.netindev.zeronet.ui.components.AppFilterListDialog
import tk.netindev.zeronet.data.AppSettings
import tk.netindev.zeronet.ui.theme.ZeronetTheme
import tk.netindev.zeronet.ui.components.ThreadPoolCountSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var appSettings by remember { mutableStateOf(AppSettings()) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val settings = Settings(context)
        appSettings = AppSettings(
            redirectUdp = settings.getBoolean(Settings.UDPFORWARD_KEY, false),
            udpRemoteGateway = settings.getString(Settings.UDPRESOLVER_KEY, ""),
            redirectDns = settings.getBoolean(Settings.DNS_FORWARD_KEY, false),
            primaryDns = settings.getString(Settings.DNS_RESOLVER_KEY),
            enableTethering = settings.getBoolean(Settings.ENABLE_TETHERING_KEY, false),
            sshPingInterval = settings.getSshPingInterval(),
            appFilterList = settings.getAppFilterList(),
            disableTcpDelay = settings.getDisableTcpDelay(),
            threadPoolCount = settings.getThreadPoolCount()
        )
    }

    BackHandler {
        onNavigateBack()
    }
    
    ZeronetTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Settings",
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                AppSettingsContent(
                    appSettings = appSettings,
                    onAppSettingsChange = { appSettings = it }
                )
            }
        }
    }
}

@Composable
private fun AppSettingsContent(
    appSettings: AppSettings,
    onAppSettingsChange: (AppSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var redirectUdp by remember { mutableStateOf(appSettings.redirectUdp) }
    var udpRemoteGateway by remember { mutableStateOf(appSettings.udpRemoteGateway) }
    var redirectDns by remember { mutableStateOf(appSettings.redirectDns) }
    var primaryDns by remember { mutableStateOf(appSettings.primaryDns) }
    var enableTethering by remember { mutableStateOf(appSettings.enableTethering) }
    var sshPingInterval by remember { mutableStateOf(appSettings.sshPingInterval.toString()) }
    var disableTcpDelay by remember { mutableStateOf(appSettings.disableTcpDelay) }
    var threadPoolCount by remember { mutableStateOf(appSettings.threadPoolCount) }
    var showAppFilterDialog by remember { mutableStateOf(false) }

    fun saveSettings(newSettings: AppSettings) {
        val settings = Settings(context)
        settings.setBoolean(Settings.UDPFORWARD_KEY, newSettings.redirectUdp)
        settings.setString(Settings.UDPRESOLVER_KEY, newSettings.udpRemoteGateway)
        settings.setBoolean(Settings.DNS_FORWARD_KEY, newSettings.redirectDns)
        settings.setString(Settings.DNS_RESOLVER_KEY, newSettings.primaryDns)
        settings.setBoolean(Settings.ENABLE_TETHERING_KEY, newSettings.enableTethering)
        settings.setSshPingInterval(newSettings.sshPingInterval)
        settings.setAppFilterList(newSettings.appFilterList)
        settings.setDisableTcpDelay(newSettings.disableTcpDelay)
        settings.setThreadPoolCount(newSettings.threadPoolCount)
    }

    LaunchedEffect(appSettings) {
        redirectUdp = appSettings.redirectUdp
        udpRemoteGateway = appSettings.udpRemoteGateway
        redirectDns = appSettings.redirectDns
        primaryDns = appSettings.primaryDns
        enableTethering = appSettings.enableTethering
        sshPingInterval = appSettings.sshPingInterval.toString()
        disableTcpDelay = appSettings.disableTcpDelay
        threadPoolCount = appSettings.threadPoolCount
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Redirect UDP",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = redirectUdp,
                onCheckedChange = { 
                    redirectUdp = it
                    val newSettings = appSettings.copy(redirectUdp = it)
                    onAppSettingsChange(newSettings)
                    saveSettings(newSettings)
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Redirect UDP",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        
        OutlinedTextField(
            value = udpRemoteGateway,
            onValueChange = { 
                udpRemoteGateway = it
                val newSettings = appSettings.copy(udpRemoteGateway = it)
                onAppSettingsChange(newSettings)
                saveSettings(newSettings)
            },
            label = { Text("UDP Remote Gateway") },
            placeholder = { Text("192.168.1.1") },
            enabled = redirectUdp,
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = if (redirectUdp) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                focusedBorderColor = if (redirectUdp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        )
        
        Spacer(modifier = Modifier.height(6.dp))

        Divider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Text(
            text = "Redirect DNS",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = redirectDns,
                onCheckedChange = { 
                    redirectDns = it
                    val newSettings = appSettings.copy(redirectDns = it)
                    onAppSettingsChange(newSettings)
                    saveSettings(newSettings)
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Redirect DNS",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        
        OutlinedTextField(
            value = primaryDns,
            onValueChange = { 
                primaryDns = it
                val newSettings = appSettings.copy(primaryDns = it)
                onAppSettingsChange(newSettings)
                saveSettings(newSettings)
            },
            label = { Text("Primary DNS") },
            placeholder = { Text("8.8.8.8") },
            enabled = redirectDns,
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = if (redirectDns) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                focusedBorderColor = if (redirectDns) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        )
        
        Spacer(modifier = Modifier.height(6.dp))

        Divider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Text(
            text = "SSH Ping",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        
        OutlinedTextField(
            value = sshPingInterval,
            onValueChange = { 
                sshPingInterval = it
                val interval = it.toIntOrNull() ?: 30
                val newSettings = appSettings.copy(sshPingInterval = interval)
                onAppSettingsChange(newSettings)
                saveSettings(newSettings)
            },
            label = { Text("SSH Ping Interval (seconds)") },
            placeholder = { Text("30") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            ),
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )
        
        Spacer(modifier = Modifier.height(6.dp))

        Divider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Text(
            text = "Thread Pool Count",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )

        ThreadPoolCountSelector(
            value = threadPoolCount,
            onChange = {
                val clamped = when {
                    it < 2 -> 2
                    it > 30 -> 30
                    else -> it
                }
                threadPoolCount = clamped
                val newSettings = appSettings.copy(threadPoolCount = clamped)
                onAppSettingsChange(newSettings)
                val settings = Settings(context)
                settings.setThreadPoolCount(clamped)
            }
        )

        Divider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Text(
            text = "Restricted Apps",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        
        Button(
            onClick = { showAppFilterDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "App Filter Settings",
                modifier = Modifier.size(18.dp),
                tint = androidx.compose.ui.graphics.Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Configure Restricted Apps (${appSettings.appFilterList.size} apps)",
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = androidx.compose.ui.graphics.Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))

        Divider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = disableTcpDelay,
                onCheckedChange = { 
                    disableTcpDelay = it
                    val newSettings = appSettings.copy(disableTcpDelay = it)
                    onAppSettingsChange(newSettings)
                    saveSettings(newSettings)
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Disable TCP Delay",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = enableTethering,
                onCheckedChange = { 
                    enableTethering = it
                    val newSettings = appSettings.copy(enableTethering = it)
                    onAppSettingsChange(newSettings)
                    saveSettings(newSettings)
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Enable Tethering",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }

    AppFilterListDialog(
        isVisible = showAppFilterDialog,
        onDismiss = { showAppFilterDialog = false },
        onConfirm = { selectedApps ->
            val newSettings = appSettings.copy(appFilterList = selectedApps)
            onAppSettingsChange(newSettings)
            saveSettings(newSettings)
        },
        initialSelectedApps = appSettings.appFilterList
    )
}
