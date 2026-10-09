package com.sabertheme.core.widgetdata

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarMapperTest {

    private val zone = ZoneId.of("Asia/Colombo")
    private val now = LocalDateTime.of(2026, 10, 9, 9, 41)

    private fun at(t: LocalDateTime, z: ZoneId = zone) = t.atZone(z).toInstant().toEpochMilli()

    private fun timed(id: Long, start: LocalDateTime, minutes: Long, title: String? = "Event $id") =
        CalendarRow(id, title, at(start), at(start.plusMinutes(minutes)), allDay = false, color = 0)

    @Test
    fun `maps timed rows to local times`() {
        val events = CalendarMapper.toEvents(listOf(timed(1, now.withHour(10).withMinute(30), 45)), zone, now, limit = 3)
        assertThat(events.single().start).isEqualTo(now.withHour(10).withMinute(30))
        assertThat(events.single().end).isEqualTo(now.withHour(11).withMinute(15))
    }

    @Test
    fun `all-day rows keep their UTC date in any zone`() {
        val day = LocalDate.of(2026, 10, 10)
        val row = CalendarRow(1, "Holiday", at(day.atStartOfDay(), ZoneOffset.UTC), at(day.plusDays(1).atStartOfDay(), ZoneOffset.UTC), true, 0)
        val event = CalendarMapper.toEvents(listOf(row), ZoneId.of("America/Los_Angeles"), now, limit = 3).single()
        assertThat(event.start).isEqualTo(day.atStartOfDay())
        assertThat(event.allDay).isTrue()
    }

    @Test
    fun `drops finished events, keeps ongoing ones, sorts and limits`() {
        val rows = listOf(
            timed(1, now.withHour(14), 60),
            timed(2, now.withHour(8), 60), // ended 08:00–09:00
            timed(3, now.withHour(9), 60), // ongoing
            timed(4, now.withHour(16), 30),
        )
        val events = CalendarMapper.toEvents(rows, zone, now, limit = 2)
        assertThat(events.map { it.id }).containsExactly(3L, 1L).inOrder()
    }

    @Test
    fun `blank titles get a placeholder`() {
        val events = CalendarMapper.toEvents(listOf(timed(1, now.plusHours(1), 30, title = "  ")), zone, now, limit = 1)
        assertThat(events.single().title).isEqualTo("(No title)")
    }

    @Test
    fun `today count ignores later days`() {
        val data = CalendarData(
            now,
            CalendarMapper.toEvents(listOf(timed(1, now.plusHours(1), 30), timed(2, now.plusDays(1), 30)), zone, now, limit = 5),
        )
        assertThat(data.todayCount()).isEqualTo(1)
    }
}
