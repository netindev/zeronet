package tk.netindev.zeronet.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.netindev.zeronet.data.RemoteProxyConfig

@Composable
fun CustomPayloadSection(
    isCustomPayloadEnabled: Boolean,
    customPayloadText: String,
    onCustomPayloadTextChange: (String) -> Unit,
    remoteProxyConfig: RemoteProxyConfig,
    onRemoteProxyClick: () -> Unit,
    tunnelType: String,
    onTunnelTypeChange: (String) -> Unit,
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
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            
            OutlinedTextField(
                value = customPayloadText,
                onValueChange = onCustomPayloadTextChange,
                placeholder = { Text("Enter custom payload...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                maxLines = 4
            )


            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (tunnelType == "SSH_PROXY") "Proxied Connection" else "Direct Connection",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (tunnelType == "SSH_PROXY") {
                        IconButton(
                            onClick = onRemoteProxyClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configure Proxy",
                                tint = if (remoteProxyConfig.host.isNotEmpty() && remoteProxyConfig.port.isNotEmpty()) {
                                    Color(0xFF2196F3) // Azul quando configurado
                                } else {
                                    Color(0xFFB71C1C) // Vermelho quando não configurado
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Switch(
                        checked = tunnelType == "SSH_PROXY",
                        onCheckedChange = { isProxied ->
                            onTunnelTypeChange(if (isProxied) "SSH_PROXY" else "SSH_DIRECT")
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }
    }
}
