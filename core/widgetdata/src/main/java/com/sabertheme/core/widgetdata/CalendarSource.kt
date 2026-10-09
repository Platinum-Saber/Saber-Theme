package com.sabertheme.core.widgetdata

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Instances
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class CalendarEvent(
    val id: Long,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val color: Int,
)

/** Upcoming events (ongoing first), as of [now]. */
data class CalendarData(val now: LocalDateTime, val events: List<CalendarEvent>) {
    val today: LocalDate get() = now.toLocalDate()

    fun todayCount() = events.count { !it.start.toLocalDate().isAfter(today) }
}

/** One `Instances` row as the provider returns it (epoch millis; all-day rows are UTC midnights). */
data class CalendarRow(val eventId: Long, val title: String?, val begin: Long, val end: Long, val allDay: Boolean, val color: Int)

internal object CalendarMapper {
    fun toEvents(rows: List<CalendarRow>, zone: ZoneId, now: LocalDateTime, limit: Int): List<CalendarEvent> = rows
        .map { row ->
            val rowZone = if (row.allDay) ZoneOffset.UTC else zone
            CalendarEvent(
                id = row.eventId,
                title = row.title?.takeIf { it.isNotBlank() } ?: "(No title)",
                start = LocalDateTime.ofInstant(Instant.ofEpochMilli(row.begin), rowZone),
                end = LocalDateTime.ofInstant(Instant.ofEpochMilli(row.end), rowZone),
                allDay = row.allDay,
                color = row.color,
            )
        }
        .filter { it.end.isAfter(now) }
        .sortedWith(compareBy({ it.start }, { !it.allDay }))
        .take(limit)
}

/** Next events over the coming week from `CalendarContract.Instances` (`READ_CALENDAR`). */
@Singleton
class CalendarSource @Inject constructor(
    @ApplicationContext private val context: Context,
    permissions: WidgetPermissions,
    scope: WidgetScope,
) : WidgetDataSource<CalendarData> {

    override val state: Flow<WidgetState<CalendarData>> = permissions
        .gated(WidgetPermission.Calendar) {
            merge(providerChanges(), ticks()).conflate().map { WidgetState.Ready(query()) }
        }
        .shareIn(scope, WhileShown, replay = 1)

    private fun providerChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        send(Unit)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    /** Re-query periodically so finished events drop off. */
    private fun ticks(): Flow<Unit> = flow {
        while (true) {
            delay(TimeUnit.MINUTES.toMillis(5))
            emit(Unit)
        }
    }

    private suspend fun query(): CalendarData = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val nowMs = System.currentTimeMillis()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, nowMs - TimeUnit.DAYS.toMillis(1))
            ContentUris.appendId(it, nowMs + TimeUnit.DAYS.toMillis(7))
        }.build()
        val rows = buildList {
            context.contentResolver.query(
                uri,
                arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.DISPLAY_COLOR),
                "${Instances.VISIBLE} = 1",
                null,
                "${Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    add(CalendarRow(c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3), c.getInt(4) == 1, c.getInt(5)))
                }
            }
        }
        CalendarData(now, CalendarMapper.toEvents(rows, zone, now, limit = 8))
    }
}
