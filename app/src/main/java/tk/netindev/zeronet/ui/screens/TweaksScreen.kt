package tk.netindev.zeronet.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.ClickableText
import tk.netindev.zeronet.service.config.Settings
import tk.netindev.zeronet.ui.theme.ZeronetTheme
import androidx.compose.foundation.background
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import tk.netindev.zeronet.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TweaksScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val operators = listOf("TIM", "VIVO")

    var selectedOperator by remember { mutableStateOf("") }
    var mnoHost by remember { mutableStateOf("") }
    var mnoPort by remember { mutableStateOf("") }
    var mnoPhoneNumber by remember { mutableStateOf("") }
    var mnoApiKey by remember { mutableStateOf("") }
    var showMnoHelpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        selectedOperator = settings.getString(Settings.MNO_FREE_INTERNET_OPERATOR_KEY)
        mnoHost = settings.getString(Settings.MNO_FREE_INTERNET_HOST_KEY)
        mnoPort = settings.getString(Settings.MNO_FREE_INTERNET_PORT_KEY)
        mnoPhoneNumber = settings.getString(Settings.MNO_FREE_INTERNET_PHONE_KEY)
        mnoApiKey = settings.getString(Settings.MNO_FREE_INTERNET_API_KEY)
    }

    LaunchedEffect(selectedOperator) {
        if (selectedOperator.isNotEmpty()) {
            when (selectedOperator) {
                "TIM" -> {
                    mnoHost = "tim-bot.example.com"
                    mnoPort = "8080"
                    mnoPhoneNumber = "+5511999999999"
                    mnoApiKey = "tim-api-key-here"
                }
                "VIVO" -> {
                    mnoHost = "vivo-bot.example.com"
                    mnoPort = "8080"
                    mnoPhoneNumber = "+5511999999999"
                    mnoApiKey = "vivo-api-key-here"
                }
            }

            settings.setString(Settings.MNO_FREE_INTERNET_OPERATOR_KEY, selectedOperator)
            settings.setString(Settings.MNO_FREE_INTERNET_HOST_KEY, mnoHost)
            settings.setString(Settings.MNO_FREE_INTERNET_PORT_KEY, mnoPort)
            settings.setString(Settings.MNO_FREE_INTERNET_PHONE_KEY, mnoPhoneNumber)
            settings.setString(Settings.MNO_FREE_INTERNET_API_KEY, mnoApiKey)
        }
    }

    BackHandler {
        onNavigateBack()
    }
    
    ZeronetTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Tweaks",
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                MnoFreeInternetContent(
                    selectedOperator = selectedOperator,
                    operators = operators,
                    onOperatorSelected = { operator ->
                        selectedOperator = operator
                    },
                    mnoHost = mnoHost,
                    onMnoHostChange = { 
                        mnoHost = it
                        settings.setString(Settings.MNO_FREE_INTERNET_HOST_KEY, it)
                    },
                    mnoPort = mnoPort,
                    onMnoPortChange = { 
                        mnoPort = it
                        settings.setString(Settings.MNO_FREE_INTERNET_PORT_KEY, it)
                    },
                    mnoPhoneNumber = mnoPhoneNumber,
                    onMnoPhoneNumberChange = { 
                        mnoPhoneNumber = it
                        settings.setString(Settings.MNO_FREE_INTERNET_PHONE_KEY, it)
                    },
                    mnoApiKey = mnoApiKey,
                    onMnoApiKeyChange = { 
                        mnoApiKey = it
                        settings.setString(Settings.MNO_FREE_INTERNET_API_KEY, it)
                    },
                    showHelpDialog = showMnoHelpDialog,
                    onShowHelpDialogChange = { showMnoHelpDialog = it }
                )
            }
        }
    }
}

@Composable
private fun MnoFreeInternetContent(
    selectedOperator: String,
    operators: List<String>,
    onOperatorSelected: (String) -> Unit,
    mnoHost: String,
    onMnoHostChange: (String) -> Unit,
    mnoPort: String,
    onMnoPortChange: (String) -> Unit,
    mnoPhoneNumber: String,
    onMnoPhoneNumberChange: (String) -> Unit,
    mnoApiKey: String,
    onMnoApiKeyChange: (String) -> Unit,
    showHelpDialog: Boolean,
    onShowHelpDialogChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Free Internet",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = { onShowHelpDialogChange(true) },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Help,
                    contentDescription = "Help",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        SimpleOperatorDropdown(
            selectedOperator = selectedOperator,
            operators = operators,
            onOperatorSelected = onOperatorSelected
        )
        
        OutlinedTextField(
            value = mnoHost,
            onValueChange = onMnoHostChange,
            label = { 
                Text(
                    text = "Host",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ) 
            },
            placeholder = { Text("example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Text
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        
        OutlinedTextField(
            value = mnoPort,
            onValueChange = onMnoPortChange,
            label = { 
                Text(
                    text = "Port",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ) 
            },
            placeholder = { Text("8080") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        
        OutlinedTextField(
            value = mnoPhoneNumber,
            onValueChange = onMnoPhoneNumberChange,
            label = { 
                Text(
                    text = "Phone Number",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ) 
            },
            placeholder = { Text("+5511999999999") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Phone
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        
        OutlinedTextField(
            value = mnoApiKey,
            onValueChange = onMnoApiKeyChange,
            label = { 
                Text(
                    text = "API Key",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ) 
            },
            placeholder = { Text("your-api-key-here") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Text
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        if (selectedOperator.isNotBlank() && mnoHost.isNotBlank() && mnoPort.isNotBlank() &&
            mnoPhoneNumber.isNotBlank() && mnoApiKey.isNotBlank()
        ) {
            Button(
                onClick = {
                    // TODO
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Run ${selectedOperator} Autobot",
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        
    }

    if (showHelpDialog) {
        Dialog(
            onDismissRequest = { onShowHelpDialogChange(false) },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Free Internet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        
                        IconButton(onClick = { onShowHelpDialogChange(false) }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val context = LocalContext.current
                    val mnoAnnotatedText = buildAnnotatedString {
                        append("What does this configuration do?\n")
                        append("It automatically runs a bot that completes all advertisement tasks for the selected MNO (Mobile Network Operator). So you don't need to worry about anything else.\n\n")
                        append("How do I fill in the requested information?\n")
                        append("1. Select your MNO (TIM or VIVO) from the dropdown\n")
                        append("2. The configuration will be auto-filled based on your selection\n")
                        append("3. Adjust the values as needed for your specific setup\n")
                        append("4. Go to the link ")
                        
                        pushStringAnnotation(
                            tag = "URL",
                            annotation = "https://github.com/netindev/zeronet-MNO-bot"
                        )
                        withStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append("github.com/netindev/zeronet-MNO-bot")
                        }
                        pop()
                        
                        append(", run your own external server and be happy!")
                    }
                    
                    ClickableText(
                        text = mnoAnnotatedText,
                        onClick = { offset ->
                            mnoAnnotatedText.getStringAnnotations(
                                tag = "URL",
                                start = offset,
                                end = offset
                            ).firstOrNull()?.let { annotation ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                context.startActivity(intent)
                            }
                        },
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            lineHeight = 20.sp
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onShowHelpDialogChange(false) },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Close",
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleOperatorDropdown(
    selectedOperator: String,
    operators: List<String>,
    onOperatorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var operatorExpanded by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {}
        
        Spacer(modifier = Modifier.height(8.dp))
        
        ExposedDropdownMenuBox(
            expanded = operatorExpanded,
            onExpandedChange = { operatorExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedOperator,
                onValueChange = { },
                readOnly = true,
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
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = operatorExpanded)
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
                expanded = operatorExpanded,
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

@Composable
private fun getOperatorLogo(operator: String): Int {
    return when (operator) {
        "TIM" -> R.drawable.tim
        "VIVO" -> R.drawable.vivo
        else -> R.drawable.ic_zeronet
    }
}
