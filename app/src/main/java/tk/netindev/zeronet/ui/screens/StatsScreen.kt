package tk.netindev.zeronet.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tk.netindev.zeronet.service.config.Settings
import tk.netindev.zeronet.service.util.ConnectionStatsManager
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }

    BackHandler {
        onNavigateBack()
    }

    var totalUptime by remember { mutableStateOf(0L) }
    var totalDownload by remember { mutableStateOf(0L) }
    var totalUpload by remember { mutableStateOf(0L) }
    var isBatteryOptimized by remember { mutableStateOf(false) }
    var connectionTestResult by remember { mutableStateOf("") }
    var isTestingConnection by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isBatteryOptimized = isBatteryOptimizationDisabled(context)
    }

    // Persisted totals from Settings (saved when connection ends)
    var persistedUptime by remember { mutableStateOf(settings.getTotalUptimeSeconds()) }
    var persistedDownload by remember { mutableStateOf(settings.getTotalDownloadBytes()) }
    var persistedUpload by remember { mutableStateOf(settings.getTotalUploadBytes()) }

    // Live session stats from ConnectionStatsManager (updated every second while connected)
    val liveStats by ConnectionStatsManager.stats.collectAsState(initial = ConnectionStatsManager.Stats())

    // Poll Settings every second so we see updated totals after connection ends
    LaunchedEffect(Unit) {
        while (true) {
            persistedUptime = settings.getTotalUptimeSeconds()
            persistedDownload = settings.getTotalDownloadBytes()
            persistedUpload = settings.getTotalUploadBytes()
            kotlinx.coroutines.delay(1000)
        }
    }

    // Display = persisted + current session when connected (connectedSinceEpochMs > 0)
    val isConnected = liveStats.connectedSinceEpochMs > 0L
    totalUptime = persistedUptime + if (isConnected) liveStats.sessionDurationSeconds else 0L
    totalDownload = persistedDownload + if (isConnected) liveStats.sessionDownloadBytes else 0L
    totalUpload = persistedUpload + if (isConnected) liveStats.sessionUploadBytes else 0L
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Statistics",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black,
                titleContentColor = Color.White
            )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(
                title = "Total Uptime",
                icon = Icons.Default.Schedule,
                content = {
                    Text(
                        text = formatDuration(totalUptime),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            )

            StatCard(
                title = "Data Usage",
                icon = Icons.Default.Storage,
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.ArrowDownward,
                                    contentDescription = "Download",
                                    tint = Color(0xFF2196F3),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Download:",
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = formatBytes(totalDownload),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF2196F3)
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.ArrowUpward,
                                    contentDescription = "Upload",
                                    tint = Color(0xFFF44336),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Upload:",
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = formatBytes(totalUpload),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFF44336)
                            )
                        }
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = formatBytes(totalDownload + totalUpload),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )

            StatCard(
                title = "Battery Optimization",
                icon = Icons.Default.BatterySaver,
                content = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBatteryOptimized) "Optimized" else "Not Optimized",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            color = if (isBatteryOptimized) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        )
                        
                        if (!isBatteryOptimized) {
                            Button(
                                onClick = {
                                    val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF9800)
                                ),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "Fix",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            )

            StatCard(
                title = "Connection Test",
                icon = Icons.Default.NetworkCheck,
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (connectionTestResult.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (connectionTestResult.contains("Connected")) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = "Test Result",
                                    tint = if (connectionTestResult.contains("Connected")) Color(0xFF4CAF50) else Color(0xFFF44336),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = connectionTestResult,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (connectionTestResult.contains("Connected")) Color(0xFF4CAF50) else Color(0xFFF44336)
                                )
                            }
                        }
                        
                        Button(
                            onClick = {
                                isTestingConnection = true
                                connectionTestResult = ""
                            },
                            enabled = !isTestingConnection,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testing...")
                            } else {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test Connection")
                            }
                        }
                    }
                }
            )

            StatCard(
                title = "App Information",
                icon = Icons.Default.Info,
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Version:",
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "1.0.0",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Build:",
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "1",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            )
        }
    }

    LaunchedEffect(isTestingConnection) {
        if (isTestingConnection) {
            connectionTestResult = testConnection()
            isTestingConnection = false
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = tk.netindev.zeronet.ui.theme.DarkGray
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            
            content()
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
        minutes > 0 -> String.format("%d:%02d", minutes, secs)
        else -> String.format("%ds", secs)
    }
}

private fun formatBytes(bytes: Long): String {
    val df = DecimalFormat("#.##")
    return when {
        bytes >= 1024 * 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        bytes >= 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024.0))} MB"
        bytes >= 1024 -> "${df.format(bytes / 1024.0)} KB"
        else -> "$bytes B"
    }
}

private fun isBatteryOptimizationDisabled(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private suspend fun testConnection(): String = withContext(Dispatchers.IO) {
    return@withContext try {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url("http://clients3.google.com/generate_204")
            .get()
            .build()
        
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                "Connected ✓"
            } else {
                "Connection Failed (${response.code})"
            }
        }
    } catch (e: Exception) {
        "Connection Failed: ${e.message}"
    }
}
