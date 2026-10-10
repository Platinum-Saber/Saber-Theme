package com.sabertheme.widgets.glance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Calendar edits redraw the calendar widget. The calendar provider sends
 * `PROVIDER_CHANGED` from its own app (flagged for background receivers),
 * so this is exported; all it can do is trigger a redraw.
 */
class CalendarChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PROVIDER_CHANGED || intent.data?.authority != CalendarContract.AUTHORITY) return
        val pending = goAsync()
        scope.launch {
            try {
                CalendarGlanceWidget().updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
