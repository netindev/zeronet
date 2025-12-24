package tk.netindev.zeronet.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import tk.netindev.zeronet.service.util.ConnectionStatus

@Composable
fun ConnectionIndicator(
    status: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val isConnected = status == ConnectionStatus.LEVEL_CONNECTED
    val isError = status in listOf(
        ConnectionStatus.LEVEL_AUTH_FAILED,
        ConnectionStatus.LEVEL_NO_NETWORK,
        ConnectionStatus.LEVEL_TIMEOUT,
        ConnectionStatus.LEVEL_PROXY_ERROR
    )
    val isConnecting = status in listOf(
        ConnectionStatus.LEVEL_START,
        ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET,
        ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED,
        ConnectionStatus.LEVEL_CONNECTING_DNS,
        ConnectionStatus.LEVEL_CONNECTING_SSH,
        ConnectionStatus.LEVEL_AUTHENTICATING,
        ConnectionStatus.LEVEL_TUNNEL_SETUP,
        ConnectionStatus.LEVEL_RECONNECTING
    )
    
    val color = when (status) {
        ConnectionStatus.LEVEL_CONNECTED -> Color(0xFF4CAF50) // Verde - Conectado
        ConnectionStatus.LEVEL_AUTH_FAILED -> Color(0xFFF44336) // Vermelho - Falha de autenticação
        ConnectionStatus.LEVEL_NO_NETWORK -> Color(0xFFE91E63) // Rosa - Sem rede
        ConnectionStatus.LEVEL_TIMEOUT -> Color(0xFFFF9800) // Laranja - Timeout
        ConnectionStatus.LEVEL_PROXY_ERROR -> Color(0xFF9C27B0) // Roxo - Erro de proxy
        ConnectionStatus.LEVEL_DISCONNECTING -> Color(0xFF607D8B) // Azul acinzentado - Desconectando
        ConnectionStatus.LEVEL_START -> Color(0xFF2196F3) // Azul - Iniciando
        ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET -> Color(0xFF03DAC6) // Ciano - Conectando
        ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED -> Color(0xFF00BCD4) // Ciano claro - Handshake
        ConnectionStatus.LEVEL_CONNECTING_DNS -> Color(0xFF00E5FF) // Ciano muito claro - DNS
        ConnectionStatus.LEVEL_CONNECTING_SSH -> Color(0xFF18FFFF) // Ciano brilhante - SSH
        ConnectionStatus.LEVEL_AUTHENTICATING -> Color(0xFF40E0D0) // Turquesa - Autenticando
        ConnectionStatus.LEVEL_TUNNEL_SETUP -> Color(0xFF20B2AA) // Verde azulado - Configurando túnel
        ConnectionStatus.LEVEL_RECONNECTING -> Color(0xFF00CED1) // Turquesa escuro - Reconectando
        ConnectionStatus.LEVEL_NOT_CONNECTED -> Color(0xFF9E9E9E) // Cinza - Desconectado
        ConnectionStatus.UNKNOWN_LEVEL -> Color(0xFF424242) // Cinza escuro - Desconhecido
    }

    if (isConnected) {
        PulsingIndicator(
            color = color,
            modifier = modifier
        )
    } else if (isConnecting) {
        BlinkingIndicator(
            color = color,
            modifier = modifier
        )
    } else {
        StaticIndicator(
            color = color,
            modifier = modifier
        )
    }
}

@Composable
private fun PulsingIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = (size.minDimension / 2) * scale
            
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = center
            )
        }
    }
}

@Composable
private fun BlinkingIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2
            
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = center
            )
        }
    }
}

@Composable
private fun StaticIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2
            
            drawCircle(
                color = color,
                radius = radius,
                center = center
            )
        }
    }
}

@Composable
fun ConnectionStatusBadge(
    status: ConnectionStatus,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (status) {
        ConnectionStatus.LEVEL_CONNECTED -> "CONNECTED" to Color(0xFF4CAF50)
        ConnectionStatus.LEVEL_START -> "STARTING" to Color(0xFF2196F3)
        ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET -> "CONNECTING" to Color(0xFF03DAC6)
        ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED -> "HANDSHAKE" to Color(0xFF00BCD4)
        ConnectionStatus.LEVEL_CONNECTING_DNS -> "DNS LOOKUP" to Color(0xFF00E5FF)
        ConnectionStatus.LEVEL_CONNECTING_SSH -> "SSH CONNECT" to Color(0xFF18FFFF)
        ConnectionStatus.LEVEL_AUTHENTICATING -> "AUTHENTICATING" to Color(0xFF40E0D0)
        ConnectionStatus.LEVEL_TUNNEL_SETUP -> "TUNNEL SETUP" to Color(0xFF20B2AA)
        ConnectionStatus.LEVEL_RECONNECTING -> "RECONNECTING" to Color(0xFF00CED1)
        ConnectionStatus.LEVEL_DISCONNECTING -> "DISCONNECTING" to Color(0xFF607D8B)
        ConnectionStatus.LEVEL_AUTH_FAILED -> "AUTH FAILED" to Color(0xFFF44336)
        ConnectionStatus.LEVEL_NO_NETWORK -> "NO NETWORK" to Color(0xFFE91E63)
        ConnectionStatus.LEVEL_TIMEOUT -> "TIMEOUT" to Color(0xFFFF9800)
        ConnectionStatus.LEVEL_PROXY_ERROR -> "PROXY ERROR" to Color(0xFF9C27B0)
        ConnectionStatus.LEVEL_NOT_CONNECTED -> "DISCONNECTED" to Color(0xFF9E9E9E)
        ConnectionStatus.UNKNOWN_LEVEL -> "UNKNOWN" to Color(0xFF424242)
    }
    
    Surface(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ConnectionIndicator(
                status = status,
                modifier = Modifier.size(8.dp)
            )
            
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}
