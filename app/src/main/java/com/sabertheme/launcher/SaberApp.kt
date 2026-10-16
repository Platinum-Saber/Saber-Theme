package com.sabertheme.launcher

import android.app.Application
import android.content.Context
import android.os.StrictMode
import android.util.Log
import androidx.work.Configuration
import com.sabertheme.core.briefing.BriefingBridge
import com.sabertheme.core.data.SettingsRepository
import com.sabertheme.widgets.glance.SaberGlanceWidgets
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SaberApp : Application(), Configuration.Provider {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var briefing: BriefingBridge

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** WorkManager schedules from the main process only; `:brain` just runs briefing work. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setDefaultProcessName(packageName).build()

    override fun onCreate() {
        super.onCreate()
        // `:brain` (briefing runs) needs only Hilt; widgets and the briefing bridge are Home's.
        if (getProcessName() != packageName) return
        if (BuildConfig.DEBUG) enableStrictMode()
        scope.launch { SaberGlanceWidgets.onAppStart(this@SaberApp) }
        publishWidgetPreviewsOnce()
        briefing.start(scope, settings.settings.map { it.briefing })
    }

    /** Generated widget-picker previews are rate-limited, so publish them once per install or update. */
    private fun publishWidgetPreviewsOnce() {
        val installed = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        val prefs = getSharedPreferences("glance", Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_PREVIEWS, 0L) == installed) return
        scope.launch {
            try {
                SaberGlanceWidgets.publishPreviews(this@SaberApp)
                prefs.edit().putLong(KEY_PREVIEWS, installed).apply()
            } catch (e: Exception) {
                Log.w(TAG, "Widget previews not published", e)
            }
        }
    }

    /**
     * Debug only: logs (tag StrictMode) leaked streams, cursors, receivers and
     * activities, unsafe intent launches, cleartext traffic and network on the
     * main thread. Check with `adb logcat -d | grep StrictMode`.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectNetwork().detectCustomSlowCalls().penaltyLog().build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .detectContentUriWithoutPermission()
                .detectFileUriExposure()
                .detectCleartextNetwork()
                .detectUnsafeIntentLaunch()
                .penaltyLog()
                .build(),
        )
    }

    private companion object {
        const val TAG = "SaberApp"
        const val KEY_PREVIEWS = "previews_published_for"
    }
}
