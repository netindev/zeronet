package tk.netindev.zeronet.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tk.netindev.zeronet.service.util.LogManager

@Composable
fun LogLevelFilterButton(
    level: LogManager.LogLevel,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when (level) {
        LogManager.LogLevel.ERROR -> Color(0xFFF44336)
        LogManager.LogLevel.WARN -> Color(0xFFFF9800)
        LogManager.LogLevel.INFO -> Color(0xFF2196F3)
        LogManager.LogLevel.DEBUG -> Color(0xFF4CAF50)
    }
    
    val textColor = Color.White
    
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                color = if (isEnabled) backgroundColor else backgroundColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = level.displayName,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isEnabled) textColor else textColor.copy(alpha = 0.5f),
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun LogViewer(
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(emptyList<LogManager.LogEntry>()) }
    var enabledLogLevels by remember { mutableStateOf(setOf(LogManager.LogLevel.INFO, LogManager.LogLevel.ERROR)) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    fun toggleLogLevel(level: LogManager.LogLevel) {
        enabledLogLevels = if (enabledLogLevels.contains(level)) {
            enabledLogLevels - level
        } else {
            enabledLogLevels + level
        }
    }

    LaunchedEffect(Unit) {
        val listener = object : LogManager.LogListener {
            override fun onLogAdded(entry: LogManager.LogEntry) {
                logs = LogManager.getAllLogs()
                coroutineScope.launch {
                    if (logs.isNotEmpty()) {
                        listState.animateScrollToItem(logs.size - 1)
                    }
                }
            }

            override fun onLogsCleared() {
                logs = emptyList()
            }
        }
        
        LogManager.addLogListener(listener)
        logs = LogManager.getAllLogs()

        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    val titleHeight = 56.dp
    val minHeight = (screenHeight * 0.25f)
    val maxHeight = (screenHeight * 0.85f)
    
    var currentHeight by remember { mutableStateOf((screenHeight * 0.28f).value) }
    
    val animatedHeight by animateFloatAsState(
        targetValue = if (isExpanded) currentHeight else titleHeight.value,
        animationSpec = tween(300),
        label = "height_animation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(animatedHeight.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {}
                        ) { change, _ ->
                            if (isExpanded) {
                                val newHeight = (currentHeight - change.position.y / 3f).coerceIn(
                                    minHeight.value,
                                    maxHeight.value
                                )
                                currentHeight = newHeight
                            }
                        }
                    }
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Logs (${logs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isExpanded) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LogManager.LogLevel.values().sortedBy { it.priority }.forEach { level ->
                                LogLevelFilterButton(
                                    level = level,
                                    isEnabled = enabledLogLevels.contains(level),
                                    onClick = { toggleLogLevel(level) }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { LogManager.clearLogs() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear logs",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))

                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No logs available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val filteredLogs = if (enabledLogLevels.isEmpty()) {
                            logs.takeLast(200)
                        } else {
                            logs.filter { enabledLogLevels.contains(it.level) }.takeLast(200)
                        }
                        
                        items(filteredLogs) { logEntry ->
                            LogEntryItem(logEntry = logEntry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryItem(
    logEntry: LogManager.LogEntry
) {
    val backgroundColor = when (logEntry.level) {
        LogManager.LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        LogManager.LogLevel.WARN -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        LogManager.LogLevel.INFO -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        LogManager.LogLevel.DEBUG -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
    }

    val textColor = when (logEntry.level) {
        LogManager.LogLevel.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        LogManager.LogLevel.WARN -> MaterialTheme.colorScheme.onTertiaryContainer
        LogManager.LogLevel.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
        LogManager.LogLevel.DEBUG -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = logEntry.level.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(24.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = logEntry.timestamp,
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(80.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = "${logEntry.tag}: ${logEntry.message}",
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}