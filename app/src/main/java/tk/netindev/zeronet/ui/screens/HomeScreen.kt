package tk.netindev.zeronet.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tk.netindev.zeronet.R
import tk.netindev.zeronet.data.PayloadItem
import tk.netindev.zeronet.data.RemoteProxyConfig
import tk.netindev.zeronet.service.util.ConnectionStatus
import tk.netindev.zeronet.ui.components.ConnectionInfoModal
import tk.netindev.zeronet.ui.components.ConnectionStatusBadge
import tk.netindev.zeronet.ui.components.ConnectionStatusModal
import tk.netindev.zeronet.ui.components.ConnectionStatsCard
import tk.netindev.zeronet.ui.components.LogViewer
import tk.netindev.zeronet.ui.components.MenuDropdown
import tk.netindev.zeronet.ui.components.NetworkTypeBadge
import tk.netindev.zeronet.ui.components.OperatorConfigCard
import tk.netindev.zeronet.ui.components.PayloadConfigCard
import tk.netindev.zeronet.ui.components.RemoteProxyDialog

@Composable
fun HomeScreen(
    connectionStatus: ConnectionStatus,
    selectedOperator: String,
    operators: List<String>,
    onOperatorSelected: (String) -> Unit,
    isCustomPayloadEnabled: Boolean,
    onCustomPayloadToggle: (Boolean) -> Unit,
    selectedPayload: String,
    availablePayloads: List<PayloadItem>,
    onPayloadSelected: (String) -> Unit,
    customPayloadText: String,
    onCustomPayloadTextChange: (String) -> Unit,
    remoteProxyConfig: RemoteProxyConfig,
    onRemoteProxyConfirm: (RemoteProxyConfig) -> Unit,
    tunnelType: String,
    onTunnelTypeChange: (String) -> Unit,
    sniHost: String,
    onSniHostChange: (String) -> Unit,
    dnsttDnsServer: String,
    onDnsttDnsServerChange: (String) -> Unit,
    dnsttTunnelDomain: String,
    onDnsttTunnelDomainChange: (String) -> Unit,
    dnsttPublicKey: String,
    onDnsttPublicKeyChange: (String) -> Unit,
    pingMs: Long,
    avgUploadKbps: Double,
    avgDownloadKbps: Double,
    sshHost: String,
    uptimeText: String,
    geoIp: String,
    geoCity: String,
    geoRegion: String,
    geoCountry: String,
    geoOrg: String,
    geoCountryCode: String,
    geoStatus: String,
    isValid: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onSshConfigClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPresetsClick: () -> Unit,
    onStatsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onExitClick: () -> Unit,
    onRequestPhoneStatePermission: () -> Unit,
) {
    var isAnimating by remember { mutableStateOf(false) }
    var showRemoteProxyDialog by remember { mutableStateOf(false) }
    var showConnectionStatusModal by remember { mutableStateOf(false) }
    var showConnectionInfoModal by remember { mutableStateOf(false) }

    LaunchedEffect(isAnimating) {
        if (isAnimating) {
            delay(1500)
            isAnimating = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
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
                    IconButton(onClick = onSshConfigClick) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "SSH Configuration",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    MenuDropdown(
                        onPresetsClick = onPresetsClick,
                        onStatsClick = onStatsClick,
                        onAboutClick = onAboutClick,
                        onExitClick = onExitClick,
                    )
                }
            }

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
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 100f),
                        label = "rotation"
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (isAnimating) 1.2f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 100f),
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
                            ) { if (!isAnimating) isAnimating = true }
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
                        onOperatorSelected = onOperatorSelected,
                        isCustomPayloadEnabled = isCustomPayloadEnabled,
                        onCustomPayloadToggle = onCustomPayloadToggle,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PayloadConfigCard(
                        isCustomPayloadEnabled = isCustomPayloadEnabled,
                        selectedPayload = selectedPayload,
                        availablePayloads = availablePayloads,
                        onPayloadSelected = onPayloadSelected,
                        customPayloadText = customPayloadText,
                        onCustomPayloadTextChange = onCustomPayloadTextChange,
                        remoteProxyConfig = remoteProxyConfig,
                        onRemoteProxyClick = { showRemoteProxyDialog = true },
                        tunnelType = tunnelType,
                        onTunnelTypeChange = onTunnelTypeChange,
                        sniHost = sniHost,
                        onSniHostChange = onSniHostChange,
                        dnsttDnsServer = dnsttDnsServer,
                        onDnsttDnsServerChange = onDnsttDnsServerChange,
                        dnsttTunnelDomain = dnsttTunnelDomain,
                        onDnsttTunnelDomainChange = onDnsttTunnelDomainChange,
                        dnsttPublicKey = dnsttPublicKey,
                        onDnsttPublicKeyChange = onDnsttPublicKeyChange,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (connectionStatus != ConnectionStatus.LEVEL_NOT_CONNECTED) {
                    ConnectionStatsCard(
                        pingMs = pingMs,
                        avgUploadKbps = avgUploadKbps,
                        avgDownloadKbps = avgDownloadKbps,
                        sshHost = sshHost,
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
                        onConnectionInfoClick = {
                            showConnectionInfoModal = true
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                Button(
                    onClick = {
                        if (connectionStatus == ConnectionStatus.LEVEL_NOT_CONNECTED) {
                            onStartClick()
                        } else {
                            onStopClick()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = connectionStatus != ConnectionStatus.LEVEL_NOT_CONNECTED || isValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            connectionStatus != ConnectionStatus.LEVEL_NOT_CONNECTED -> Color(0xFFB71C1C)
                            isValid -> MaterialTheme.colorScheme.primary
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
                            isValid -> "Start"
                            else -> "Configure Required"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            LogViewer(modifier = Modifier.fillMaxWidth())
        }

        RemoteProxyDialog(
            isVisible = showRemoteProxyDialog,
            onDismiss = { showRemoteProxyDialog = false },
            onConfirm = { config ->
                onRemoteProxyConfirm(config)
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
