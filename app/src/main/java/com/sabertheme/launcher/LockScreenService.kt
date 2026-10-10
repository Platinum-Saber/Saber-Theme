package com.sabertheme.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.sabertheme.core.data.ScreenLock

/**
 * Double-tap to lock. Listens to no events and cannot read window content
 * (see res/xml/lock_service.xml); it only performs the system "lock screen"
 * action, which behaves like the power button (biometric unlock still works).
 */
class LockScreenService : AccessibilityService() {
    override fun onServiceConnected() {
        ScreenLock.lock = { performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ScreenLock.lock = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
