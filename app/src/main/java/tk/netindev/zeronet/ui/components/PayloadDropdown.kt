package tk.netindev.zeronet.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.netindev.zeronet.data.PayloadItem

fun extractBadges(payloadName: String): List<String> {
    val badges = mutableListOf<String>()
    val upperName = payloadName.uppercase()
    
    if (upperName.contains("DNSTT")) badges.add("DNSTT")
    if (upperName.contains("WS")) badges.add("WS")
    if (upperName.contains("ZERO-RATED")) badges.add("ZERO-RATED")
    if (upperName.contains("ROTATE")) badges.add("ROTATE")
    
    return badges
}

fun getLastWord(payloadName: String): String {
    return payloadName.split(" ").lastOrNull() ?: payloadName
}

@Composable
fun PayloadBadge(text: String) {
    val backgroundColor = when (text) {
        "DNSTT" -> Color(0xFFB71C1C)
        "WS" -> Color(0xFF4CAF50)
        "ZERO-RATED" -> Color(0xFF2196F3)
        "ROTATE" -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    
    val textColor = when (text) {
        "DNSTT" -> Color.White
        "WS" -> Color.White
        "ZERO-RATED" -> Color.White
        "ROTATE" -> Color.White
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    
    Box(
        modifier = Modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayloadDropdown(
    isVisible: Boolean,
    selectedPayload: String,
    availablePayloads: List<PayloadItem>,
    onPayloadSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var payloadExpanded by remember { mutableStateOf(false) }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(
            animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        ),
        exit = fadeOut(
            animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing)
        ),
        modifier = modifier
    ) {
        Column {
            Text(
                text = "Payload",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            
            ExposedDropdownMenuBox(
                expanded = payloadExpanded,
                onExpandedChange = { payloadExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (selectedPayload.isNotEmpty()) {
                        getLastWord(selectedPayload)
                    } else {
                        selectedPayload
                    },
                    onValueChange = { },
                    readOnly = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    trailingIcon = { 
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (selectedPayload.isNotEmpty()) {
                                val badges = extractBadges(selectedPayload)
                                badges.forEach { badge ->
                                    PayloadBadge(badge)
                                }
                            }
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = payloadExpanded)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .background(MaterialTheme.colorScheme.surface),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                
                ExposedDropdownMenu(
                    expanded = payloadExpanded,
                    onDismissRequest = { payloadExpanded = false },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    availablePayloads.forEach { payloadItem ->
                        DropdownMenuItem(
                            text = { 
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = getLastWord(payloadItem.payloadName),
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 16.sp
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val badges = extractBadges(payloadItem.payloadName)
                                        badges.forEach { badge ->
                                            PayloadBadge(badge)
                                        }
                                    }
                                }
                            },
                            onClick = { 
                                onPayloadSelected(payloadItem.payloadName)
                                payloadExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
