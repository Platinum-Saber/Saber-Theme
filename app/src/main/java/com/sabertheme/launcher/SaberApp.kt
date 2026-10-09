package com.sabertheme.launcher

import android.app.Application
import android.content.Context
import android.util.Log
import com.sabertheme.widgets.glance.SaberGlanceWidgets
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class SaberApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        scope.launch { SaberGlanceWidgets.onAppStart(this@SaberApp) }
        publishWidgetPreviewsOnce()
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

    private companion object {
        const val TAG = "SaberApp"
        const val KEY_PREVIEWS = "previews_published_for"
    }
}
