package com.sabertheme.widgets.glance

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.sabertheme.core.widgetdata.CalendarData
import com.sabertheme.core.widgetdata.CalendarEvent
import com.sabertheme.core.widgetdata.WidgetFormat
import com.sabertheme.core.widgetdata.WidgetState
import com.sabertheme.core.widgetdata.snapshot

/** Saber Calendar for other launchers: today's date and the next events (`READ_CALENDAR`). */
class CalendarGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(GlanceTokens.S2x2, GlanceTokens.S4x2))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val calendar = context.widgetSources().calendar
        val initial = calendar.snapshot()
        val is24Hour = DateFormat.is24HourFormat(context)
        provideContent {
            val state by calendar.state.collectAsState(initial)
            CalendarWidget(context, state, is24Hour)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { CalendarWidget(context, WidgetState.Ready(GlanceSamples.calendar), is24Hour = true) }
    }
}

class CalendarGlanceReceiver : SaberGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarGlanceWidget()
}

@Composable
private fun CalendarWidget(context: Context, state: WidgetState<CalendarData>, is24Hour: Boolean) {
    val wide = currentLayout() == GlanceLayout.Wide
    val open = actionStartActivity(
        Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(System.currentTimeMillis().toString()).build()),
    )
    GlanceStateFrame(context, state, wide, onClick = open) { calendar ->
        if (wide) {
            Column(GlanceModifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(calendar.today.dayOfMonth.toString(), style = GlanceTokens.title())
                    Spacer(GlanceModifier.width(8.dp))
                    Text(WidgetFormat.weekday(calendar.today), style = GlanceTokens.label(color = GlanceTokens.accent), modifier = GlanceModifier.padding(bottom = 3.dp))
                }
                if (calendar.events.isEmpty()) {
                    Spacer(GlanceModifier.height(6.dp))
                    Text("No upcoming events", style = GlanceTokens.label())
                }
                calendar.events.take(3).forEach {
                    Spacer(GlanceModifier.height(6.dp))
                    EventRow(it, calendar, is24Hour)
                }
            }
        } else {
            Column(GlanceModifier.fillMaxSize()) {
                Text(WidgetFormat.weekday(calendar.today), style = GlanceTokens.label(color = GlanceTokens.accent))
                Text(calendar.today.dayOfMonth.toString(), style = GlanceTokens.display())
                Spacer(GlanceModifier.defaultWeight())
                val first = calendar.events.firstOrNull()
                if (first == null) {
                    Text("No upcoming events", style = GlanceTokens.label())
                } else {
                    EventRow(first, calendar, is24Hour)
                    val shownToday = if (!first.start.toLocalDate().isAfter(calendar.today)) 1 else 0
                    val more = calendar.todayCount() - shownToday
                    if (more > 0) Text("+$more more today", style = GlanceTokens.label(11, GlanceTokens.textTertiary))
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent, calendar: CalendarData, is24Hour: Boolean) {
    Row {
        val bar = if (event.color != 0) ColorProvider(Color(event.color).copy(alpha = 1f)) else GlanceTokens.accent
        Box(GlanceModifier.width(3.dp).height(30.dp).cornerRadius(1.5.dp).background(bar)) {}
        Spacer(GlanceModifier.width(8.dp))
        Column {
            Text(event.title, style = GlanceTokens.label(color = GlanceTokens.textPrimary), maxLines = 1)
            Text(WidgetFormat.eventTime(event, calendar.today, is24Hour), style = GlanceTokens.label(11), maxLines = 1)
        }
    }
}
