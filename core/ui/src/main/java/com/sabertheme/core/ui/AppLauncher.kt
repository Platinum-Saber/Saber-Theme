package com.sabertheme.core.ui

import android.app.ActivityOptions
import android.view.View
import androidx.compose.ui.geometry.Rect
import com.sabertheme.core.data.AppRepository
import com.sabertheme.core.data.LaunchStats
import com.sabertheme.core.model.AppKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import android.graphics.Rect as AndroidRect

/** Launches apps with a clip-reveal from the tapped tile's window bounds, and counts launches. */
@Singleton
class AppLauncher @Inject constructor(private val apps: AppRepository, private val stats: LaunchStats) {

    fun launch(key: AppKey, view: View, bounds: Rect): Boolean {
        val r = bounds.toAndroid()
        val options = ActivityOptions.makeClipRevealAnimation(view, r.left, r.top, r.width(), r.height()).toBundle()
        return apps.launch(key, r, options).also { if (it) stats.record(key) }
    }

    fun openAppInfo(key: AppKey, bounds: Rect) = apps.openAppInfo(key, bounds.toAndroid())
}

private fun Rect.toAndroid() = AndroidRect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
