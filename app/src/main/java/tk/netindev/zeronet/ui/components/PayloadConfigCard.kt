package tk.netindev.zeronet.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.netindev.zeronet.data.RemoteProxyConfig
import tk.netindev.zeronet.data.PayloadItem

@Composable
fun PayloadConfigCard(
    isCustomPayloadEnabled: Boolean,
    selectedPayload: String,
    availablePayloads: List<PayloadItem>,
    onPayloadSelected: (String) -> Unit,
    customPayloadText: String,
    onCustomPayloadTextChange: (String) -> Unit,
    remoteProxyConfig: RemoteProxyConfig,
    onRemoteProxyClick: () -> Unit,
    tunnelType: String,
    onTunnelTypeChange: (String) -> Unit,
    sniHost: String = "",
    onSniHostChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = tk.netindev.zeronet.ui.theme.DarkGray
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PayloadDropdown(
                isVisible = !isCustomPayloadEnabled,
                selectedPayload = selectedPayload,
                availablePayloads = availablePayloads,
                onPayloadSelected = onPayloadSelected
            )

            CustomPayloadSection(
                isCustomPayloadEnabled = isCustomPayloadEnabled,
                customPayloadText = customPayloadText,
                onCustomPayloadTextChange = onCustomPayloadTextChange,
                remoteProxyConfig = remoteProxyConfig,
                onRemoteProxyClick = onRemoteProxyClick,
                tunnelType = tunnelType,
                onTunnelTypeChange = onTunnelTypeChange,
                sniHost = sniHost,
                onSniHostChange = onSniHostChange
            )
        }
    }
}
