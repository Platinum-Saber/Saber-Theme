package com.sabertheme.core.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Locks the phone through Saber's accessibility service (`LockScreenService`
 * in :app), the only way a launcher can turn the screen off while keeping
 * fingerprint / face unlock. The user enables the service once in Android's
 * accessibility settings; it reads nothing.
 */
object ScreenLock {
    /** Set by the service while it is connected. */
    @Volatile var lock: (() -> Boolean)? = null

    private const val SERVICE = "com.sabertheme.launcher.LockScreenService"

    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context.packageName, SERVICE)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** True when the screen was locked. */
    fun lockNow(): Boolean = lock?.invoke() == true

    /** Opens accessibility settings, at Saber's service where the system supports it. */
    fun openSettings(context: Context) {
        val component = ComponentName(context.packageName, SERVICE).flattenToString()
        val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val list = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(details) }.onFailure { runCatching { context.startActivity(list) } }
    }
}
