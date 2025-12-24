package tk.netindev.zeronet.service.util

import android.util.Log

object AppLog {

    @JvmOverloads
    fun v(tag: String, message: String, throwable: Throwable? = null) {
        Log.v(tag, message, throwable)
        LogManager.addAndroidLog(Log.VERBOSE, tag, message, throwable)
    }

    @JvmOverloads
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        Log.d(tag, message, throwable)
        LogManager.addAndroidLog(Log.DEBUG, tag, message, throwable)
    }

    @JvmOverloads
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        Log.i(tag, message, throwable)
        LogManager.addAndroidLog(Log.INFO, tag, message, throwable)
    }

    @JvmOverloads
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        LogManager.addAndroidLog(Log.WARN, tag, message, throwable)
    }

    @JvmOverloads
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        LogManager.addAndroidLog(Log.ERROR, tag, message, throwable)
    }
}
