package tk.netindev.zeronet

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import tk.netindev.zeronet.service.util.AppLog
import tk.netindev.zeronet.ui.ZeronetApp
import tk.netindev.zeronet.ui.theme.ZeronetTheme
import tk.netindev.zeronet.vpn.ZeroNetService

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            selectedPayloadForTunnel?.let {
                startTunnelWithPayload()
            }
        }
    }

    val phoneStatePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private var selectedPayloadForTunnel: String? = null
    private var selectedOperatorForTunnel: String? = null

    private var isRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                            onStartTunnel = { selectedPayload, selectedOperator ->
                                requestVpnPermission(selectedPayload, selectedOperator)
                            },
                            onStopTunnel = { stopTunnel() },
                            context = this@MainActivity,
                            onRequestPhoneStatePermission = {
                                phoneStatePermissionLauncher.launch(android.Manifest.permission.READ_PHONE_STATE)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestVpnPermission(payloadName: String, operator: String) {
        selectedPayloadForTunnel = payloadName
        selectedOperatorForTunnel = operator

        if (isActiveVpn()) {
            AppLog.d("MainActivity", "Another VPN service is already running, stop it first")
            return
        }

        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startTunnelWithPayload()
        }
    }

    private fun isActiveVpn(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun startTunnelWithPayload() {
        selectedPayloadForTunnel?.let {
            val intent = Intent(this, ZeroNetService::class.java)
            startService(intent)
            isRunning = true
        }
    }

    private fun stopTunnel() {
        val intent = Intent(this, ZeroNetService::class.java)
        stopService(intent)
        isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRunning) {
            stopTunnel()
        }
    }
}
