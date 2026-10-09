package com.sabertheme.widgets.glance

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.Text
import com.sabertheme.core.icons.UiGlyph
import com.sabertheme.core.widgetdata.AlarmData
import com.sabertheme.core.widgetdata.WidgetFormat
import com.sabertheme.core.widgetdata.WidgetState
import com.sabertheme.core.widgetdata.snapshot
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Saber Clock for other launchers: time and date are TextClocks (the host
 * redraws them every minute without waking us), plus the next alarm.
 */
class ClockGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(GlanceTokens.S2x2, GlanceTokens.S4x1, GlanceTokens.S4x2))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val alarms = context.widgetSources().alarm
        val initial = alarms.snapshot()
        val is24Hour = DateFormat.is24HourFormat(context)
        provideContent {
            val alarm by alarms.state.collectAsState(initial)
            ClockContent(context, (alarm as? WidgetState.Ready)?.data, is24Hour)
        }
    }
}

class ClockGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockGlanceWidget()
}

@Composable
private fun ClockContent(context: Context, alarm: AlarmData?, is24Hour: Boolean) {
    val layout = currentLayout()
    val alarmText = alarm?.triggerAt?.let {
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
        WidgetFormat.alarm(at, LocalDateTime.now(), is24Hour)
    }
    val open = GlanceModifier.clickable(actionStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS)))
    GlanceFrame(wide = layout != GlanceLayout.Square, modifier = open) {
        when (layout) {
            GlanceLayout.Wide -> Column(
                GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AndroidRemoteViews(RemoteViews(context.packageName, R.layout.glance_clock_wide), GlanceModifier.fillMaxWidth().wrapContentHeight())
                if (alarmText != null) {
                    Spacer(GlanceModifier.height(4.dp))
                    AlarmLine(alarmText)
                }
            }
            GlanceLayout.Row -> Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                AndroidRemoteViews(RemoteViews(context.packageName, R.layout.glance_clock_row), GlanceModifier.fillMaxWidth().wrapContentHeight())
            }
            GlanceLayout.Square -> Column(
                GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AndroidRemoteViews(RemoteViews(context.packageName, R.layout.glance_clock_square), GlanceModifier.fillMaxWidth().wrapContentHeight())
            }
        }
    }
}

@Composable
private fun AlarmLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlanceGlyph(UiGlyph.ALARM.drawable, 14.dp)
        Spacer(GlanceModifier.width(6.dp))
        Text(text, style = GlanceTokens.label())
    }
}
