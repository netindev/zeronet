package tk.netindev.zeronet.service.util

import android.content.Context
import android.net.TrafficStats
import android.os.Handler
import android.os.Process
import android.os.SystemClock
import tk.netindev.zeronet.service.config.Settings
import tk.netindev.zeronet.service.util.ConnectionStatsManager.setSpeeds
import java.util.ArrayDeque
import java.util.Deque
import kotlin.math.max

class SpeedMonitor(
    private val handler: Handler,
    private val callback: SpeedUpdateCallback,
    private val context: Context
) {
    private val lastUpKbps: Deque<Double?> = ArrayDeque<Double?>()
    private val lastDownKbps: Deque<Double?> = ArrayDeque<Double?>()
    private var lastRxBytes = -1L
    private var lastTxBytes = -1L
    private var lastTsMs = -1L

    private var connectionStartTime = 0L
    private var initialRxBytes = 0L
    private var initialTxBytes = 0L

    private var isMonitoring = false

    interface SpeedUpdateCallback {
        fun onSpeedUpdate(uploadKbps: Double, downloadKbps: Double)
    }

    fun startMonitoring() {
        if (isMonitoring) {
            return
        }

        val uid = Process.myUid()
        var rx = TrafficStats.getUidRxBytes(uid)
        var tx = TrafficStats.getUidTxBytes(uid)
        if (rx < 0 || tx < 0) {
            rx = 0L
            tx = 0L
        }

        lastRxBytes = rx
        lastTxBytes = tx
        lastTsMs = SystemClock.elapsedRealtime()

        initialRxBytes = rx
        initialTxBytes = tx
        connectionStartTime = System.currentTimeMillis()

        lastDownKbps.clear()
        lastUpKbps.clear()

        isMonitoring = true
        handler.removeCallbacks(speedRunnable)
        handler.postDelayed(speedRunnable, UPDATE_INTERVAL_MS.toLong())
    }

    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }

        handler.removeCallbacks(speedRunnable)
        lastDownKbps.clear()
        lastUpKbps.clear()
        isMonitoring = false
    }

    val sessionStats: SessionStats
        get() {
            if (!isMonitoring) {
                return SessionStats(0, 0, 0)
            }

            val uid = Process.myUid()
            var currentRx = TrafficStats.getUidRxBytes(uid)
            var currentTx = TrafficStats.getUidTxBytes(uid)
            if (currentRx < 0 || currentTx < 0) {
                currentRx = 0L
                currentTx = 0L
            }

            val sessionDuration =
                (System.currentTimeMillis() - connectionStartTime) / 1000
            val sessionDownload = max(0, currentRx - initialRxBytes)
            val sessionUpload = max(0, currentTx - initialTxBytes)

            return SessionStats(sessionDuration, sessionDownload, sessionUpload)
        }

    val currentUploadSpeed: Double
        get() = average(lastUpKbps)

    val currentDownloadSpeed: Double
        get() = average(lastDownKbps)

    fun updateStatistics() {
        if (!isMonitoring) {
            return
        }

        val stats = this.sessionStats
        if (stats.durationSeconds > 0) {
            val settings = Settings(context)

            val totalUptime = settings.getTotalUptimeSeconds() + stats.durationSeconds
            settings.setTotalUptimeSeconds(totalUptime)

            val totalDownload = settings.getTotalDownloadBytes() + stats.downloadBytes
            val totalUpload = settings.getTotalUploadBytes() + stats.uploadBytes

            settings.setTotalDownloadBytes(totalDownload)
            settings.setTotalUploadBytes(totalUpload)
        }
    }

    private val speedRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) {
                return
            }

            try {
                val uid = Process.myUid()
                var rx = TrafficStats.getUidRxBytes(uid)
                var tx = TrafficStats.getUidTxBytes(uid)
                if (rx < 0 || tx < 0) {
                    rx = 0L
                    tx = 0L
                }

                val now = SystemClock.elapsedRealtime()
                if (rx >= 0 && tx >= 0 && lastRxBytes >= 0 && lastTxBytes >= 0 && lastTsMs > 0) {
                    val dRx = rx - lastRxBytes
                    val dTx = tx - lastTxBytes
                    val dt = now - lastTsMs

                    if (dRx >= 0 && dTx >= 0 && dt > 0) {
                        val secs = dt / 1000.0
                        val downKbps = (dRx * 8.0) / (secs * 1000.0)
                        val upKbps = (dTx * 8.0) / (secs * 1000.0)

                        pushRolling(lastDownKbps, downKbps)
                        pushRolling(lastUpKbps, upKbps)

                        val avgDown = average(lastDownKbps)
                        val avgUp = average(lastUpKbps)

                        setSpeeds(avgUp, avgDown)

                        callback.onSpeedUpdate(avgUp, avgDown)
                    }
                }

                lastRxBytes = rx
                lastTxBytes = tx
                lastTsMs = now
            } catch (_: Throwable) {
            }

            if (isMonitoring) {
                handler.postDelayed(this, UPDATE_INTERVAL_MS.toLong())
            }
        }

        private fun pushRolling(q: Deque<Double?>, v: Double) {
            q.addLast(v)
            while (q.size > ROLLING_WINDOW_SIZE) {
                q.removeFirst()
            }
        }
    }

    private fun average(q: Deque<Double?>): Double {
        if (q.isEmpty()) return 0.0
        var sum = 0.0
        for (v in q) {
            sum += v!!
        }
        return sum / q.size
    }

    class SessionStats(val durationSeconds: Long, val downloadBytes: Long, val uploadBytes: Long)
    companion object {
        private const val ROLLING_WINDOW_SIZE = 5
        private const val UPDATE_INTERVAL_MS = 1000
    }
}
