package com.sabertheme.widgets.glance

import com.sabertheme.core.widgetdata.AlarmData
import com.sabertheme.core.widgetdata.CalendarData
import com.sabertheme.core.widgetdata.CalendarEvent
import com.sabertheme.core.widgetdata.HourForecast
import com.sabertheme.core.widgetdata.WeatherCondition
import com.sabertheme.core.widgetdata.WeatherData
import java.time.LocalDateTime
import java.time.ZoneId

/** Figma sample data for the widget-picker previews (same values as the in-launcher picker). */
internal object GlanceSamples {
    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 9, 9, 41)

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
}
