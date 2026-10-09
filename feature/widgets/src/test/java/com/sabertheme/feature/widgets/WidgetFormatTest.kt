package com.sabertheme.feature.widgets

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

class WidgetFormatTest {

    private val uk = Locale.UK
    private val friday = LocalDate.of(2026, 10, 9)

    @Test
    fun `24 hour time is zero padded`() {
        assertThat(WidgetFormat.time(LocalTime.of(9, 41), is24Hour = true, uk)).isEqualTo("09:41")
        assertThat(WidgetFormat.time(LocalTime.of(21, 5), is24Hour = true, uk)).isEqualTo("21:05")
    }

    @Test
    fun `12 hour time drops the leading zero`() {
        assertThat(WidgetFormat.time(LocalTime.of(21, 5), is24Hour = false, uk)).isEqualTo("9:05")
        assertThat(WidgetFormat.time(LocalTime.of(0, 30), is24Hour = false, uk)).isEqualTo("12:30")
    }

    @Test
    fun `stacked clock hour and minute`() {
        assertThat(WidgetFormat.hour(LocalTime.of(9, 41), is24Hour = true)).isEqualTo("09")
        assertThat(WidgetFormat.hour(LocalTime.of(0, 41), is24Hour = false)).isEqualTo("12")
        assertThat(WidgetFormat.hour(LocalTime.of(13, 41), is24Hour = false)).isEqualTo("01")
        assertThat(WidgetFormat.minute(LocalTime.of(9, 5))).isEqualTo("05")
    }

    @Test
    fun `dates match the Figma samples`() {
        assertThat(WidgetFormat.longDate(friday, uk)).isEqualTo("Friday, 9 October")
        assertThat(WidgetFormat.shortDate(friday, uk)).isEqualTo("Fri 9 Oct")
        assertThat(WidgetFormat.weekday(friday, uk)).isEqualTo("FRIDAY")
    }

    @Test
    fun `alarm says today, tomorrow or the weekday`() {
        val now = friday.atTime(9, 41)
        assertThat(WidgetFormat.alarm(friday.plusDays(1).atTime(6, 30), now, is24Hour = true, uk)).isEqualTo("06:30 · Tomorrow")
        assertThat(WidgetFormat.alarm(friday.atTime(22, 0), now, is24Hour = true, uk)).isEqualTo("22:00 · Today")
        assertThat(WidgetFormat.alarm(friday.plusDays(3).atTime(7, 0), now, is24Hour = true, uk)).isEqualTo("07:00 · Mon")
    }

    @Test
    fun `event time prefixes later days`() {
        fun event(start: LocalDateTime, allDay: Boolean = false) = CalendarEvent(1, "x", start, start.plusMinutes(45), allDay, 0)
        assertThat(WidgetFormat.eventTime(event(friday.atTime(10, 30)), friday, true, uk)).isEqualTo("10:30 – 11:15")
        assertThat(WidgetFormat.eventTime(event(friday.plusDays(1).atTime(10, 30)), friday, true, uk)).isEqualTo("Tomorrow · 10:30 – 11:15")
        assertThat(WidgetFormat.eventTime(event(friday.atStartOfDay(), allDay = true), friday, true, uk)).isEqualTo("All day")
    }

    @Test
    fun `high and low`() {
        assertThat(WidgetFormat.highLow(27, 19)).isEqualTo("H 27°  L 19°")
        assertThat(WidgetFormat.highLow(null, null)).isEmpty()
    }
}
