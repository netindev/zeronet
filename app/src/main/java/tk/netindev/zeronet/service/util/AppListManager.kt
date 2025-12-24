package tk.netindev.zeronet.service.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.netindev.zeronet.data.AppInfo

class AppListManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AppListManager"
    }

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            val packageManager = context.packageManager
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            
            installedApps.mapNotNull { appInfo ->
                try {
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = packageManager.getApplicationIcon(appInfo)
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    AppInfo(
                        packageName = appInfo.packageName,
                        appName = appName,
                        icon = icon,
                        isSystemApp = isSystemApp
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get info for app: ${appInfo.packageName}", e)
                    null
                }
            }.sortedBy { it.appName.lowercase() }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get installed apps", e)
            emptyList()
        }
    }

    suspend fun getUserApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        getInstalledApps().filter { !it.isSystemApp }
    }

    suspend fun getSystemApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        getInstalledApps().filter { it.isSystemApp }
    }

    suspend fun searchApps(query: String): List<AppInfo> = withContext(Dispatchers.IO) {
        val apps = getInstalledApps()
        val lowerQuery = query.lowercase()
        
        apps.filter { app ->
            app.appName.lowercase().contains(lowerQuery) ||
            app.packageName.lowercase().contains(lowerQuery)
        }
    }
}
