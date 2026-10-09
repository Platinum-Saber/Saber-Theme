package com.sabertheme.widgets.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import com.sabertheme.core.widgetdata.HourForecast
import com.sabertheme.core.widgetdata.WeatherData
import com.sabertheme.core.widgetdata.WidgetFormat
import com.sabertheme.core.widgetdata.WidgetState
import com.sabertheme.core.widgetdata.snapshot
import java.util.Locale

/** Saber Weather for other launchers: Open-Meteo via the shared cache; tap to refresh. */
class WeatherGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(GlanceTokens.S2x2, GlanceTokens.S4x1, GlanceTokens.S4x2))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val weather = context.widgetSources().weather
        val initial = weather.snapshot()
        provideContent {
            val state by weather.state.collectAsState(initial)
            WeatherWidget(context, state)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { WeatherWidget(context, WidgetState.Ready(GlanceSamples.weather)) }
    }
}

class WeatherGlanceReceiver : SaberGlanceReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherGlanceWidget()
}

/** Fetches when the cache is stale, then redraws every Saber widget. */
class RefreshWeatherAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.widgetSources().weather.refresh()
        SaberGlanceWidgets.updateAll(context)
    }
}

@Composable
private fun WeatherWidget(context: Context, state: WidgetState<WeatherData>) {
    val layout = currentLayout()
    GlanceStateFrame(context, state, wide = layout != GlanceLayout.Square, onClick = actionRunCallback<RefreshWeatherAction>()) { weather ->
        val highLow = WidgetFormat.highLow(weather.high, weather.low)
        when (layout) {
            GlanceLayout.Row -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                GlanceGlyph(weather.condition.glyph.drawable, 28.dp, GlanceTokens.textPrimary)
                Spacer(GlanceModifier.width(10.dp))
                Text("${weather.temperature}°", style = GlanceTokens.title())
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(weather.condition.label, style = GlanceTokens.label(color = GlanceTokens.textPrimary), maxLines = 1)
                    Text(highLow, style = GlanceTokens.label(11), maxLines = 1)
                }
                weather.hourly.take(3).forEach { Hour(it) }
            }
            GlanceLayout.Wide -> Column(GlanceModifier.fillMaxSize()) {
                Header(weather)
                Spacer(GlanceModifier.defaultWeight())
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text("${weather.temperature}°", style = GlanceTokens.display())
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight().padding(bottom = 4.dp)) {
                        Text(weather.condition.label, style = GlanceTokens.label(color = GlanceTokens.textPrimary), maxLines = 1)
                        Text(highLow, style = GlanceTokens.label(11), maxLines = 1)
                    }
                    weather.hourly.take(4).forEach { Hour(it) }
                }
            }
            GlanceLayout.Square -> Column(GlanceModifier.fillMaxSize()) {
                Header(weather)
                Spacer(GlanceModifier.defaultWeight())
                Text("${weather.temperature}°", style = GlanceTokens.display())
                Text(weather.condition.label, style = GlanceTokens.label(color = GlanceTokens.textPrimary), maxLines = 1)
                Text(highLow, style = GlanceTokens.label(11), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Header(weather: WeatherData) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlanceGlyph(weather.condition.glyph.drawable, 28.dp, GlanceTokens.textPrimary)
        Spacer(GlanceModifier.defaultWeight())
        Text("Now", style = GlanceTokens.label(11, GlanceTokens.textTertiary))
    }
}

@Composable
private fun Hour(hour: HourForecast) {
    Column(GlanceModifier.padding(start = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%02d".format(Locale.ROOT, hour.hour), style = GlanceTokens.label(11, GlanceTokens.textTertiary))
        Spacer(GlanceModifier.height(2.dp))
        GlanceGlyph(hour.condition.glyph.drawable, 16.dp)
        Spacer(GlanceModifier.height(2.dp))
        Text("${hour.temperature}°", style = GlanceTokens.label(11, GlanceTokens.textPrimary))
    }
}
