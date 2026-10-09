package com.sabertheme.core.data

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
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
import kotlinx.coroutines.flow.map
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

    /**
     * Apps plus the serials of profiles that are present but locked or paused
     * (work profile off, Secure Folder locked): their apps may be missing
     * from the list without having been uninstalled.
     */
    data class Installed(val apps: List<AppEntry>, val lockedProfiles: Set<Long>)

    val installed: Flow<Installed> = callbackFlow {
        fun publish() {
            trySend(query())
        }
        val profileReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = publish()
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
        val main = Handler(Looper.getMainLooper())
        launcherApps.registerCallback(callback, main)
        context.registerReceiver(profileReceiver, PROFILE_EVENTS, null, main, Context.RECEIVER_NOT_EXPORTED)
        publish()
        awaitClose {
            launcherApps.unregisterCallback(callback)
            context.unregisterReceiver(profileReceiver)
        }
    }.conflate().flowOn(Dispatchers.Default)

    val apps: Flow<List<AppEntry>> = installed.map { it.apps }

    private fun query(): Installed {
        val self = context.packageName
        val result = mutableListOf<AppEntry>()
        val seen = HashSet<AppKey>()
        val locked = HashSet<Long>()
        for (user in launcherApps.profiles) {
            val serial = userManager.getSerialNumberForUser(user)
            if (userManager.isQuietModeEnabled(user) || !userManager.isUserUnlocked(user)) locked += serial
            val list = try {
                launcherApps.getActivityList(null, user)
            } catch (e: SecurityException) {
                Log.w(TAG, "Profile $serial not readable", e)
                locked += serial
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
        return Installed(result, locked)
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

    /** False for system apps (and unknown keys): those can only be disabled, from app info. */
    fun canUninstall(key: AppKey): Boolean {
        val info = infos[key] ?: return false
        return info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0
    }

    /** System uninstall dialog for [key]'s package in its own profile. */
    fun uninstall(key: AppKey) {
        val info = infos[key] ?: return
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", key.packageName, null))
            .putExtra(Intent.EXTRA_USER, info.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No uninstaller for ${key.packageName}", e)
        }
    }

    private companion object {
        const val TAG = "AppRepository"

        val PROFILE_EVENTS = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            addAction(Intent.ACTION_PROFILE_ACCESSIBLE)
            addAction(Intent.ACTION_PROFILE_INACCESSIBLE)
        }
    }
}
