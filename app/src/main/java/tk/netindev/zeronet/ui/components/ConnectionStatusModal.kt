package tk.netindev.zeronet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tk.netindev.zeronet.service.util.ConnectionStatus

@Composable
fun ConnectionStatusModal(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    currentStatus: ConnectionStatus
) {
    if (isVisible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .fillMaxHeight(0.95f)
                    .padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        
                        IconButton(
                            onClick = onDismiss
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ConnectionStatus.values().toList()) { status ->
                            StatusItem(
                                status = status,
                                isCurrentStatus = status == currentStatus
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "OK",
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusItem(
    status: ConnectionStatus,
    isCurrentStatus: Boolean
) {
    val (statusText, statusColor, description) = when (status) {
        ConnectionStatus.LEVEL_CONNECTED -> Triple(
            "CONNECTED",
            Color(0xFF4CAF50),
            "VPN tunnel is active and working properly"
        )
        ConnectionStatus.LEVEL_START -> Triple(
            "STARTING",
            Color(0xFF2196F3),
            "Initializing VPN service and preparing connection"
        )
        ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET -> Triple(
            "CONNECTING",
            Color(0xFF03DAC6),
            "Attempting to establish connection to server"
        )
        ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED -> Triple(
            "HANDSHAKE",
            Color(0xFF00BCD4),
            "Server responded, performing handshake protocol"
        )
        ConnectionStatus.LEVEL_CONNECTING_DNS -> Triple(
            "DNS LOOKUP",
            Color(0xFF00E5FF),
            "Resolving server hostname to IP address"
        )
        ConnectionStatus.LEVEL_CONNECTING_SSH -> Triple(
            "SSH CONNECT",
            Color(0xFF18FFFF),
            "Establishing SSH connection to remote server"
        )
        ConnectionStatus.LEVEL_AUTHENTICATING -> Triple(
            "AUTHENTICATING",
            Color(0xFF40E0D0),
            "Verifying SSH credentials with server"
        )
        ConnectionStatus.LEVEL_TUNNEL_SETUP -> Triple(
            "TUNNEL SETUP",
            Color(0xFF20B2AA),
            "Configuring VPN tunnel and network routing"
        )
        ConnectionStatus.LEVEL_RECONNECTING -> Triple(
            "RECONNECTING",
            Color(0xFF00CED1),
            "Attempting to restore lost connection"
        )
        ConnectionStatus.LEVEL_DISCONNECTING -> Triple(
            "DISCONNECTING",
            Color(0xFF607D8B),
            "Gracefully closing VPN connection"
        )
        ConnectionStatus.LEVEL_AUTH_FAILED -> Triple(
            "AUTH FAILED",
            Color(0xFFF44336),
            "SSH authentication failed - check credentials"
        )
        ConnectionStatus.LEVEL_NO_NETWORK -> Triple(
            "NO NETWORK",
            Color(0xFFE91E63),
            "No internet connection available"
        )
        ConnectionStatus.LEVEL_TIMEOUT -> Triple(
            "TIMEOUT",
            Color(0xFFFF9800),
            "Connection attempt timed out"
        )
        ConnectionStatus.LEVEL_PROXY_ERROR -> Triple(
            "PROXY ERROR",
            Color(0xFF9C27B0),
            "Error with proxy configuration or connection"
        )
        ConnectionStatus.LEVEL_NOT_CONNECTED -> Triple(
            "DISCONNECTED",
            Color(0xFF9E9E9E),
            "VPN is not connected"
        )
        ConnectionStatus.UNKNOWN_LEVEL -> Triple(
            "UNKNOWN",
            Color(0xFF424242),
            "Status is unknown or undefined"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentStatus) {
                Color(0xFF9C27B0).copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCurrentStatus) 6.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        color = statusColor,
                        shape = CircleShape
                    )
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}
