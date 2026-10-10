package com.sabertheme.core.widgetdata

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.media.MediaBrowserService
import android.view.KeyEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts an app's last playback when it has no media session, the way the
 * system media panel does: its MediaBrowserService (recent root) → a
 * MEDIA_BUTTON play broadcast → opening the app. Each step only runs when the
 * previous one has not got the app playing within [STEP_MS]. Main thread only.
 */
@Singleton
class MediaResumer @Inject constructor(@ApplicationContext private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var browser: MediaBrowser? = null
    private var attempt = 0

    fun resume(pkg: String, isPlaying: () -> Boolean) {
        val id = ++attempt
        browser?.disconnect()
        browser = null
        fun waiting() = id == attempt && !isPlaying()

        fun openApp() {
            if (!waiting()) return
            context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }

        var pressed = false
        fun pressPlay() {
            if (pressed || !waiting()) return
            pressed = true
            if (sendPlayButton(pkg)) main.postDelayed(::openApp, STEP_MS) else openApp()
        }

        val service = component(Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(pkg), services = true)
        if (service == null) {
            pressPlay()
            return
        }
        val hints = Bundle().apply { putBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT, true) }
        val callback = object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                val token = browser?.sessionToken ?: return
                runCatching { MediaController(context, token).transportControls.play() }
            }

            override fun onConnectionFailed() = pressPlay()
        }
        browser = MediaBrowser(context, service, callback, hints).also {
            runCatching { it.connect() }.onFailure { pressPlay() }
        }
        main.postDelayed(::pressPlay, STEP_MS)
        // Playing apps run their own foreground service; the binding is only needed to start.
        main.postDelayed({
            if (id == attempt) {
                browser?.disconnect()
                browser = null
            }
        }, RELEASE_MS)
    }

    private fun sendPlayButton(pkg: String): Boolean {
        val receiver = component(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg), services = false) ?: return false
        val now = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val event = KeyEvent(now, now, action, KeyEvent.KEYCODE_MEDIA_PLAY, 0)
            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(receiver).putExtra(Intent.EXTRA_KEY_EVENT, event))
        }
        return true
    }

    private fun component(intent: Intent, services: Boolean): ComponentName? {
        val pm = context.packageManager
        val info = if (services) {
            pm.queryIntentServices(intent, 0).firstOrNull()?.serviceInfo
        } else {
            pm.queryBroadcastReceivers(intent, 0).firstOrNull()?.activityInfo
        }
        return info?.let { ComponentName(it.packageName, it.name) }
    }

    private companion object {
        const val STEP_MS = 3_000L
        const val RELEASE_MS = 15_000L
    }
}
