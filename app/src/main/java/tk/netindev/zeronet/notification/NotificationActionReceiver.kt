package tk.netindev.zeronet.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent) {
        Log.d(TAG, "NotificationActionReceiver received action: ${intent.action}")
        val action = intent.action
        if (action == null) {
            Log.w(TAG, "Received intent with null action")
            return
        }
        if (ACTION_SERVICE_STOP == action) {
            Log.d(TAG, "Sending stop service broadcast")
            val stopTunnel =
                Intent("tk.netindev.zeronet.vpn.ZeroNetService::stopServiceBroadcast")
            LocalBroadcastManager.getInstance(context!!).sendBroadcast(stopTunnel)
            Log.d(TAG, "Stop service broadcast sent")
        } else {
            Log.w(TAG, "Unknown action received: $action")
        }
    }

    companion object {
        private const val TAG = "NotificationActionReceiver"
        const val ACTION_SERVICE_STOP: String = "tk.netindev.zeronet.vpn.ACTION_SERVICE_STOP"
    }
}