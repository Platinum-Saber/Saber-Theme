package com.sabertheme.feature.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sabertheme.core.model.WidgetSize
import com.sabertheme.core.model.WidgetType
import com.sabertheme.core.widgetdata.AlarmData
import com.sabertheme.core.widgetdata.BatteryData
import com.sabertheme.core.widgetdata.CalendarData
import com.sabertheme.core.widgetdata.CalendarEvent
import com.sabertheme.core.widgetdata.ClockData
import com.sabertheme.core.widgetdata.HourForecast
import com.sabertheme.core.widgetdata.MediaApp
import com.sabertheme.core.widgetdata.MediaData
import com.sabertheme.core.widgetdata.MediaState
import com.sabertheme.core.widgetdata.WeatherCondition
import com.sabertheme.core.widgetdata.WeatherData
import com.sabertheme.core.widgetdata.WidgetPermission
import java.time.LocalDateTime
import java.time.ZoneId

enum class WidgetCategory(val label: String) {
    Time("Time"),
    Weather("Weather"),
    Calendar("Calendar"),
    Device("Device"),
    Media("Media"),
}

/** One picker entry; sizes come from [WidgetType.sizes] (first is the default). */
data class WidgetCatalogEntry(
    val type: WidgetType,
    val title: String,
    val category: WidgetCategory,
    val needs: WidgetPermission?,
) {
    val sizes: List<WidgetSize> get() = type.sizes
}

object WidgetCatalog {
    val entries = listOf(
        WidgetCatalogEntry(WidgetType.Clock, "Clock", WidgetCategory.Time, needs = null),
        WidgetCatalogEntry(WidgetType.Alarm, "Next alarm", WidgetCategory.Time, needs = null),
        WidgetCatalogEntry(WidgetType.Weather, "Weather", WidgetCategory.Weather, WidgetPermission.Location),
        WidgetCatalogEntry(WidgetType.Calendar, "Calendar", WidgetCategory.Calendar, WidgetPermission.Calendar),
        WidgetCatalogEntry(WidgetType.Battery, "Battery", WidgetCategory.Device, needs = null),
        WidgetCatalogEntry(WidgetType.Media, "Now playing", WidgetCategory.Media, WidgetPermission.NotificationListener),
    )

    fun entry(type: WidgetType) = entries.first { it.type == type }
}

/** Static [type] widget at [size] with the Figma sample data, for the picker. */
@Composable
fun WidgetPreview(type: WidgetType, size: WidgetSize, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    WidgetFrame(size, modifier, onClick = onClick) {
        when (type) {
            WidgetType.Clock -> ClockContent(size, Samples.clock, Samples.alarm)
            WidgetType.Alarm -> AlarmContent(size, Samples.alarm, Samples.now, is24Hour = true)
            WidgetType.Weather -> WeatherContent(size, Samples.weather)
            WidgetType.Calendar -> CalendarContent(size, Samples.calendar, is24Hour = true)
            WidgetType.Battery -> BatteryContent(size, BatteryData(82, charging = false))
            WidgetType.Media -> MediaContent(size, Samples.media, {}, {}, {}, {}, {}, {})
        }
    }
}

private object Samples {
    private const val SPOTIFY = "com.spotify.music"
    private const val VLC = "org.videolan.vlc"

    val now: LocalDateTime = LocalDateTime.of(2026, 10, 9, 9, 41)
    val clock = ClockData(now, is24Hour = true)
    val alarm = AlarmData(now.plusDays(1).withHour(6).withMinute(30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    val weather = WeatherData(
        temperature = 24,
        condition = WeatherCondition.PartlyCloudy,
        high = 27,
        low = 19,
        hourly = listOf(
            HourForecast(11, WeatherCondition.Clear, 25),
            HourForecast(12, WeatherCondition.PartlyCloudy, 26),
            HourForecast(13, WeatherCondition.Rain, 24),
            HourForecast(14, WeatherCondition.Rain, 23),
        ),
    )
    val calendar = CalendarData(
        now,
        listOf(
            CalendarEvent(1, "Design review", now.withHour(10).withMinute(30), now.withHour(11).withMinute(15), false, 0),
            CalendarEvent(2, "Lunch with Sam", now.withHour(12).withMinute(30), now.withHour(13).withMinute(30), false, 0),
            CalendarEvent(3, "Gym", now.withHour(18).withMinute(0), now.withHour(19).withMinute(0), false, 0),
        ),
    )
    val media = MediaState(
        now = MediaData("Midnight City", "M83", "Spotify", art = null, playing = true, packageName = SPOTIFY),
        apps = listOf(MediaApp(SPOTIFY, "Spotify", playing = true, hasSession = true), MediaApp(VLC, "VLC", playing = false, hasSession = true)),
        selected = SPOTIFY,
    )
}
