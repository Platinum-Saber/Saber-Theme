package com.sabertheme.widgets.glance

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import com.sabertheme.core.icons.UiGlyph
import com.sabertheme.core.widgetdata.AlarmData
import com.sabertheme.core.widgetdata.BatteryData
import com.sabertheme.core.widgetdata.WidgetFormat
import com.sabertheme.core.widgetdata.WidgetState
import com.sabertheme.core.widgetdata.snapshot
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Saber Battery for other launchers; refreshed by the background worker (no broadcast exists for level changes). */
class BatteryGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(GlanceTokens.S2x1, GlanceTokens.S2x2))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val battery = context.widgetSources().battery
        val initial = battery.snapshot()
        provideContent {
            val state by battery.state.collectAsState(initial)
            BatteryWidget(context, state)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { BatteryWidget(context, WidgetState.Ready(BatteryData(82, charging = false))) }
    }
}

class BatteryGlanceReceiver : SaberGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BatteryGlanceWidget()
}

@Composable
private fun BatteryWidget(context: Context, state: WidgetState<BatteryData>) {
    val row = currentLayout() == GlanceLayout.Row
    val open = context.openAction(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
    GlanceStateFrame(context, state, wide = false, onClick = open) { battery ->
        val status = if (battery.charging) "Charging" else "Phone"
        if (row) {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Ring(context, 42, battery.percent, 16)
                Spacer(GlanceModifier.width(12.dp))
                Column {
                    Text("${battery.percent}%", style = GlanceTokens.title(17))
                    Text(status, style = GlanceTokens.label(11))
                }
            }
        } else {
            Column(
                GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Ring(context, 76, battery.percent, 24)
                Spacer(GlanceModifier.height(8.dp))
                Text("${battery.percent}%", style = GlanceTokens.title())
                Text(status, style = GlanceTokens.label(11))
            }
        }
    }
}

@Composable
private fun Ring(context: Context, sizeDp: Int, percent: Int, glyphDp: Int) {
    Box(GlanceModifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Image(ringImage(context, sizeDp.dp, percent / 100f), contentDescription = "$percent percent", modifier = GlanceModifier.size(sizeDp.dp))
        GlanceGlyph(UiGlyph.DEVICE.drawable, glyphDp.dp, GlanceTokens.textPrimary)
    }
}

/** Saber Next alarm for other launchers; redrawn on NEXT_ALARM_CLOCK_CHANGED and by the worker. */
class AlarmGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(GlanceTokens.S2x1, GlanceTokens.S2x2))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val alarm = context.widgetSources().alarm
        val initial = alarm.snapshot()
        val is24Hour = DateFormat.is24HourFormat(context)
        provideContent {
            val state by alarm.state.collectAsState(initial)
            AlarmWidget(context, state, is24Hour)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { AlarmWidget(context, WidgetState.Ready(GlanceSamples.alarm), is24Hour = true) }
    }
}

class AlarmGlanceReceiver : SaberGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AlarmGlanceWidget()
}

@Composable
private fun AlarmWidget(context: Context, state: WidgetState<AlarmData>, is24Hour: Boolean) {
    val row = currentLayout() == GlanceLayout.Row
    val open = context.openAction(Intent(AlarmClock.ACTION_SHOW_ALARMS))
    GlanceStateFrame(context, state, wide = false, onClick = open) { alarm ->
        val at = alarm.triggerAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
        val time = at?.let { WidgetFormat.time(it.toLocalTime(), is24Hour) } ?: "No alarm"
        val day = at?.let { WidgetFormat.day(it.toLocalDate(), LocalDateTime.now().toLocalDate()) } ?: "Tap to set"
        if (row) {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                GlanceGlyph(UiGlyph.ALARM.drawable, 24.dp, GlanceTokens.textPrimary)
                Spacer(GlanceModifier.width(12.dp))
                Column {
                    Text(time, style = if (at != null) GlanceTokens.title() else GlanceTokens.title(17))
                    Text(day, style = GlanceTokens.label(11))
                }
            }
        } else {
            Column(GlanceModifier.fillMaxSize()) {
                GlanceGlyph(UiGlyph.ALARM.drawable, 28.dp, GlanceTokens.textPrimary)
                Spacer(GlanceModifier.defaultWeight())
                Text(time, style = if (at != null) GlanceTokens.display() else GlanceTokens.title())
                Text(day, style = GlanceTokens.label())
            }
        }
    }
}
