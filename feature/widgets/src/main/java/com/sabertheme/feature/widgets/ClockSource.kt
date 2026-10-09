package com.sabertheme.feature.widgets

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

data class ClockData(val now: LocalDateTime, val is24Hour: Boolean)

/** Minute ticks plus time, zone and 12/24 h changes. */
@Singleton
class ClockSource @Inject constructor(@ApplicationContext context: Context, scope: WidgetScope) : WidgetDataSource<ClockData> {
    override val state: Flow<WidgetState<ClockData>> = context
        .broadcasts(Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
        .map { WidgetState.Ready(ClockData(LocalDateTime.now(), DateFormat.is24HourFormat(context))) }
        .shareIn(scope, WhileShown, replay = 1)
}

/** Epoch millis of the next alarm-clock alarm, or null. */
data class AlarmData(val triggerAt: Long?)

@Singleton
class AlarmSource @Inject constructor(@ApplicationContext context: Context, scope: WidgetScope) : WidgetDataSource<AlarmData> {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override val state: Flow<WidgetState<AlarmData>> = context
        .broadcasts(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        .map { WidgetState.Ready(AlarmData(alarms.nextAlarmClock?.triggerTime)) }
        .shareIn(scope, WhileShown, replay = 1)
}
