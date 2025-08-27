package tk.netindev.zeronet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.netindev.zeronet.ui.theme.ZeronetTheme
import tk.netindev.zeronet.tunnel.TunnelManager
import tk.netindev.zeronet.tunnel.TunnelService
import tk.netindev.zeronet.tunnel.PayloadConfig
import tk.netindev.zeronet.tunnel.ZeroNetVpnService
import android.net.VpnService
import android.content.Intent
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var tunnelManager: TunnelManager
    
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startTunnelWithPayload()
        }
    }
    
    private var selectedPayloadForTunnel: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        tunnelManager = TunnelManager(this)
        
        setContent {
            ZeronetTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        ZeronetApp(
                            tunnelManager = tunnelManager,
                            onStartTunnel = { selectedPayload -> requestVpnPermission(selectedPayload) },
                            onStopTunnel = { stopTunnel() }
                        )
                    }
                }
            }
        }
    }
    
    private fun requestVpnPermission(payloadName: String) {
        selectedPayloadForTunnel = payloadName
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startTunnelWithPayload()
        }
    }
    
    private fun startTunnelWithPayload() {
        selectedPayloadForTunnel?.let { payloadName ->
            val payloadInfo = PayloadConfig.getPayloadByName(payloadName)
            if (payloadInfo != null) {
                // Start the tunnel service first
                TunnelService.startService(this)
                
                // Start the VPN service
                startVpnService()
                
                // Start the tunnel manager
                tunnelManager.startTunnelWithPayload(payloadInfo.payload)
            }
        }
    }
    
    private fun startVpnService() {
        try {
            val intent = Intent(this, ZeroNetVpnService::class.java)
            startService(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start VPN service: ${e.message}")
        }
    }
    
    private fun stopTunnel() {
        tunnelManager.stopTunnel()
        TunnelService.stopService(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeronetApp(
    tunnelManager: TunnelManager,
    onStartTunnel: (String) -> Unit,
    onStopTunnel: () -> Unit
) {
    var selectedOperator by remember { mutableStateOf("") }
    var selectedPayload by remember { mutableStateOf("") }
    var isLogVisible by remember { mutableStateOf(false) }
    var logMessages by remember { mutableStateOf(listOf<String>()) }
    var operatorExpanded by remember { mutableStateOf(false) }
    var payloadExpanded by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }
    var scrollState by remember { mutableStateOf(0) }
    var autoTimAds by remember { mutableStateOf(false) }
    
    val operators = listOf("TIM", "VIVO", "CLARO")
    val payloads = PayloadConfig.payloads.map { it.name }
    
    // Observe tunnel logs
    LaunchedEffect(Unit) {
        tunnelManager.logMessages.collect { tunnelLogs ->
            logMessages = tunnelLogs
        }
    }
    
    // Observe tunnel state
    LaunchedEffect(Unit) {
        tunnelManager.connectionState.collect { state ->
            when (state) {
                TunnelManager.ConnectionState.CONNECTED -> {
                    isRunning = true
                    logMessages = logMessages + "Tunnel connected successfully!"
                }
                TunnelManager.ConnectionState.DISCONNECTED -> {
                    isRunning = false
                    logMessages = logMessages + "Tunnel disconnected"
                }
                TunnelManager.ConnectionState.ERROR -> {
                    isRunning = false
                    logMessages = logMessages + "Tunnel error occurred"
                }
                else -> { /* Other states */ }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Main content centered
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = "App Icon",
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ZeroNet",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text(
                text = "by netindev",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Operator Dropdown
        Text(
            text = "MNO",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        
        ExposedDropdownMenuBox(
            expanded = operatorExpanded,
            onExpandedChange = { operatorExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedOperator,
                onValueChange = { selectedOperator = it },
                readOnly = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = operatorExpanded) },
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
                expanded = operatorExpanded,
                onDismissRequest = { operatorExpanded = false },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                operators.forEach { operator ->
                    DropdownMenuItem(
                        text = { 
                            Text(
                                text = operator,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ) 
                        },
                        onClick = { 
                            selectedOperator = operator
                            operatorExpanded = false
                        }
                    )
                }
            }
        }
        
        // Auto TIM Ads Checkbox
        if (selectedOperator == "TIM") {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = autoTimAds,
                    onCheckedChange = { autoTimAds = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Auto TIM Ads",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Payload Dropdown
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
                value = selectedPayload,
                onValueChange = { selectedPayload = it },
                readOnly = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = payloadExpanded) },
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
                payloads.forEach { payload ->
                    DropdownMenuItem(
                        text = { 
                            Text(
                                text = payload,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ) 
                        },
                        onClick = { 
                            selectedPayload = payload
                            payloadExpanded = false
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Start/Stop Button
        Button(
            onClick = {
                if (!isRunning) {
                    if (selectedOperator.isNotEmpty() && selectedPayload.isNotEmpty()) {
                        val timAdsStatus = if (selectedOperator == "TIM" && autoTimAds) ", Auto TIM Ads: ON" else ""
                        val message = "Started with MVNO: $selectedOperator, Payload: $selectedPayload$timAdsStatus"
                        logMessages = logMessages + message
                        logMessages = logMessages + "Operation is now running..."
                        logMessages = logMessages + "Processing data..."
                        isLogVisible = true
                        isRunning = true
                        onStartTunnel(selectedPayload)
                    } else {
                        logMessages = logMessages + "Please select operator and payload"
                    }
                } else {
                    val message = "Stopping operation..."
                    logMessages = logMessages + message
                    logMessages = logMessages + "Operation stopped"
                    isRunning = false
                    onStopTunnel()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            if (!isRunning) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Start",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (isRunning) "Stop" else "Start",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        
        }
        
        // Log Section at bottom
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                // Log Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Log",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    IconButton(
                        onClick = { isLogVisible = !isLogVisible }
                    ) {
                        Icon(
                            imageVector = if (isLogVisible) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (isLogVisible) "Hide Log" else "Show Log",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                // Log Content
                AnimatedVisibility(
                    visible = isLogVisible,
                    enter = slideInVertically(
                        animationSpec = tween(300),
                        initialOffsetY = { -it }
                    ),
                    exit = slideOutVertically(
                        animationSpec = tween(300),
                        targetOffsetY = { -it }
                    )
                ) {
                    val scrollState = rememberLazyListState()
                    
                    // Auto-scroll to bottom when new messages are added
                    LaunchedEffect(logMessages.size) {
                        if (logMessages.isNotEmpty()) {
                            scrollState.animateScrollToItem(logMessages.size - 1)
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (logMessages.isEmpty()) {
                            Text(
                                text = "No log messages yet",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        } else {
                            LazyColumn(
                                state = scrollState,
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(logMessages.size) { index ->
                                    Text(
                                        text = logMessages[index],
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


