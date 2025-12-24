package tk.netindev.zeronet.service.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

object LogManager {
    
    private const val TAG = "LogManager"
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val logMessages = CopyOnWriteArrayList<LogEntry>()
    private val listeners = CopyOnWriteArrayList<LogListener>()

    private const val MAX_LOG_ENTRIES = 1000

    data class LogEntry(
        val timestamp: String,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val fullMessage: String
    )

    enum class LogLevel(val priority: Int, val displayName: String) {
        DEBUG(Log.DEBUG, "D"),
        INFO(Log.INFO, "I"),
        WARN(Log.WARN, "W"),
        ERROR(Log.ERROR, "E")
    }

    interface LogListener {
        fun onLogAdded(entry: LogEntry)
        fun onLogsCleared()
    }

    fun addLog(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        val fullMessage = if (throwable != null) {
            "$message\n${Log.getStackTraceString(throwable)}"
        } else {
            message
        }
        
        val entry = LogEntry(timestamp, level, tag, message, fullMessage)
        
        synchronized(logMessages) {
            logMessages.add(entry)

            if (logMessages.size > MAX_LOG_ENTRIES) {
                logMessages.removeAt(0)
            }
        }

        listeners.forEach { listener ->
            try {
                listener.onLogAdded(entry)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying log listener", e)
            }
        }
    }

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        addLog(LogLevel.DEBUG, tag, message, throwable)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        addLog(LogLevel.INFO, tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        addLog(LogLevel.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        addLog(LogLevel.ERROR, tag, message, throwable)
    }

    fun addAndroidLog(level: Int, tag: String, message: String, throwable: Throwable? = null) {
        val logLevel = when (level) {
            Log.DEBUG -> LogLevel.DEBUG
            Log.INFO -> LogLevel.INFO
            Log.WARN -> LogLevel.WARN
            Log.ERROR -> LogLevel.ERROR
            else -> LogLevel.INFO
        }
        addLog(logLevel, tag, message, throwable)
    }

    fun getAllLogs(): List<LogEntry> {
        return synchronized(logMessages) {
            logMessages.toList()
        }
    }

    fun clearLogs() {
        synchronized(logMessages) {
            logMessages.clear()
        }

        listeners.forEach { listener ->
            try {
                listener.onLogsCleared()
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying log listener", e)
            }
        }
    }

    fun addLogListener(listener: LogListener) {
        listeners.add(listener)
    }
}
