package tk.netindev.zeronet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import tk.netindev.zeronet.R
import tk.netindev.zeronet.data.PayloadManager

@Composable
fun getOperatorLogo(operator: String): Int {
    return when (operator) {
        "TIM" -> R.drawable.tim
        "VIVO" -> R.drawable.vivo
        "CLARO" -> R.drawable.claro
        else -> R.drawable.ic_zeronet
    }
}

fun getPayloadCount(operator: String): Int {
    return PayloadManager.getPayloadsForOperator(operator).size
}

@Composable
fun PayloadCountBadge(count: Int) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$count payload(s)",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperatorDropdown(
    selectedOperator: String,
    operators: List<String>,
    onOperatorSelected: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var operatorExpanded by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MNO",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        ExposedDropdownMenuBox(
            expanded = operatorExpanded && enabled,
            onExpandedChange = { if (enabled) operatorExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedOperator,
                onValueChange = { },
                readOnly = true,
                enabled = enabled,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                leadingIcon = {
                    if (selectedOperator.isNotEmpty()) {
                        Image(
                            painter = painterResource(id = getOperatorLogo(selectedOperator)),
                            contentDescription = selectedOperator,
                            modifier = Modifier.size(24.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                },
                trailingIcon = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedOperator.isNotEmpty()) {
                            PayloadCountBadge(getPayloadCount(selectedOperator))
                        }
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = operatorExpanded)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .background(MaterialTheme.colorScheme.surface),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                    unfocusedBorderColor = if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedBorderColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            )
            
            ExposedDropdownMenu(
                expanded = operatorExpanded && enabled,
                onDismissRequest = { operatorExpanded = false },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                operators.forEach { operator ->
                    DropdownMenuItem(
                        text = { 
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = getOperatorLogo(operator)),
                                        contentDescription = operator,
                                        modifier = Modifier.size(24.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Text(
                                        text = operator,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 16.sp
                                    )
                                }
                                PayloadCountBadge(getPayloadCount(operator))
                            }
                        },
                        onClick = { 
                            onOperatorSelected(operator)
                            operatorExpanded = false
                        }
                    )
                }
            }
        }
    }
}
