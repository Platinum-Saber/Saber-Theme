package com.sabertheme.core.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import com.sabertheme.core.model.AppEntry
import com.sabertheme.core.model.AppKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Launchable apps across every profile (main, work, Secure Folder) via
 * [LauncherApps], updated live through [LauncherApps.Callback].
 */
@Singleton
class AppRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val infos = ConcurrentHashMap<AppKey, LauncherActivityInfo>()

    val apps: Flow<List<AppEntry>> = callbackFlow {
        fun publish() {
            trySend(query())
        }
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = publish()
            override fun onPackageAdded(packageName: String, user: UserHandle) = publish()
            override fun onPackageChanged(packageName: String, user: UserHandle) = publish()
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = publish()
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = publish()
            override fun onPackagesSuspended(packageNames: Array<out String>, user: UserHandle) = publish()
            override fun onPackagesUnsuspended(packageNames: Array<out String>, user: UserHandle) = publish()
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        publish()
        awaitClose { launcherApps.unregisterCallback(callback) }
    }.conflate().flowOn(Dispatchers.Default)

    private fun query(): List<AppEntry> {
        val self = context.packageName
        val result = mutableListOf<AppEntry>()
        val seen = HashSet<AppKey>()
        for (user in launcherApps.profiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val list = try {
                launcherApps.getActivityList(null, user)
            } catch (e: SecurityException) {
                Log.w(TAG, "Profile $serial not readable", e)
                continue
            }
            for (info in list) {
                val cn = info.componentName
                if (cn.packageName == self) continue
                val entry = AppEntry(cn.packageName, cn.className, info.label.toString(), serial)
                if (seen.add(entry.key)) {
                    infos[entry.key] = info
                    result += entry
                }
            }
        }
        infos.keys.retainAll(seen)
        return result
    }

    /** The adaptive icon's monochrome layer, if the app ships one. */
    suspend fun monochromeIcon(key: AppKey): Drawable? = withContext(Dispatchers.Default) {
        val icon = infos[key]?.getIcon(0) as? AdaptiveIconDrawable
        icon?.monochrome
    }

    /** Starts [key]; [sourceBounds] (window px) lets the system animate from the icon. */
    fun launch(key: AppKey, sourceBounds: Rect?, options: Bundle?): Boolean {
        val info = infos[key] ?: return false
        return try {
            launcherApps.startMainActivity(ComponentName(key.packageName, key.className), info.user, sourceBounds, options)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Launch failed for ${key.encode()}", e)
            false
        }
    }

    fun openAppInfo(key: AppKey, sourceBounds: Rect?) {
        val info = infos[key] ?: return
        launcherApps.startAppDetailsActivity(info.componentName, info.user, sourceBounds, null)
    }

    private companion object {
        const val TAG = "AppRepository"
    }
}
