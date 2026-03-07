package tk.netindev.zeronet.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.netindev.zeronet.data.RemoteProxyConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPayloadSection(
    isCustomPayloadEnabled: Boolean,
    customPayloadText: String,
    onCustomPayloadTextChange: (String) -> Unit,
    remoteProxyConfig: RemoteProxyConfig,
    onRemoteProxyClick: () -> Unit,
    tunnelType: String,
    onTunnelTypeChange: (String) -> Unit,
    sniHost: String,
    onSniHostChange: (String) -> Unit,
    dnsttDnsServer: String = "",
    onDnsttDnsServerChange: (String) -> Unit = {},
    dnsttTunnelDomain: String = "",
    onDnsttTunnelDomainChange: (String) -> Unit = {},
    dnsttPublicKey: String = "",
    onDnsttPublicKeyChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isCustomPayloadEnabled,
        enter = fadeIn(
            animationSpec = tween(300, delayMillis = 100, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        ),
        exit = fadeOut(
            animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Custom Payload",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = FontFamily.Monospace
            )

            OutlinedTextField(
                value = customPayloadText,
                onValueChange = onCustomPayloadTextChange,
                placeholder = { Text("Enter custom payload...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                maxLines = 4
            )

            // Tunnel type selector
            Text(
                text = "Connection Mode",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = FontFamily.Monospace
            )

            val tunnelOptions = listOf(
                "SSH_DIRECT" to "Direct",
                "SSH_PROXY" to "HTTP",
                "SSH_SSL_TUNNEL" to "SSL/TLS",
                "DNSTT" to "DNSTT"
            )
            val selectedIndex = tunnelOptions.indexOfFirst { it.first == tunnelType }.coerceAtLeast(0)

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                tunnelOptions.forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = index == selectedIndex,
                        onClick = { onTunnelTypeChange(type) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = tunnelOptions.size
                        ),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveContainerColor = Color.Transparent,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Proxy configuration button (for SSH_PROXY and SSH_SSL_TUNNEL)
            if (tunnelType == "SSH_PROXY" || tunnelType == "SSH_SSL_TUNNEL") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Remote Proxy",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = FontFamily.Monospace
                    )

                    IconButton(
                        onClick = onRemoteProxyClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configure Proxy",
                            tint = if (remoteProxyConfig.host.isNotEmpty() && remoteProxyConfig.port.isNotEmpty()) {
                                Color(0xFF2196F3)
                            } else {
                                Color(0xFFB71C1C)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // SNI host field (only for SSL/TLS mode)
            AnimatedVisibility(
                visible = tunnelType == "SSH_SSL_TUNNEL",
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(150))
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SNI Host",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = FontFamily.Monospace
                    )

                    OutlinedTextField(
                        value = sniHost,
                        onValueChange = onSniHostChange,
                        placeholder = { Text("e.g. www.example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // DNSTT configuration (only for DNSTT mode)
            AnimatedVisibility(
                visible = tunnelType == "DNSTT",
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(150))
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "DNS Server",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = FontFamily.Monospace
                    )

                    OutlinedTextField(
                        value = dnsttDnsServer,
                        onValueChange = onDnsttDnsServerChange,
                        placeholder = { Text("8.8.8.8") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Text(
                        text = "Tunnel Domain",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = FontFamily.Monospace
                    )

                    OutlinedTextField(
                        value = dnsttTunnelDomain,
                        onValueChange = onDnsttTunnelDomainChange,
                        placeholder = { Text("tunnel.example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Text(
                        text = "Public Key",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = FontFamily.Monospace
                    )

                    OutlinedTextField(
                        value = dnsttPublicKey,
                        onValueChange = onDnsttPublicKeyChange,
                        placeholder = { Text("DNSTT server public key") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        maxLines = 3
                    )
                }
            }
        }
    }
}
