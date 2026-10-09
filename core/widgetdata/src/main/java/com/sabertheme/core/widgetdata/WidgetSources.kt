package com.sabertheme.core.widgetdata

import javax.inject.Inject
import javax.inject.Singleton

/** Every widget data source; the launcher and the exported widgets inject this once. */
@Singleton
class WidgetSources @Inject constructor(
    val permissions: WidgetPermissions,
    val clock: ClockSource,
    val alarm: AlarmSource,
    val battery: BatterySource,
    val calendar: CalendarSource,
    val weather: WeatherSource,
    val media: MediaSource,
)
