package com.sabertheme.core.widgetdata

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Widget text, matching the Figma samples ("09:41", "Friday, 9 October", "Fri 9 Oct"). */
object WidgetFormat {
    fun time(t: LocalTime, is24Hour: Boolean, locale: Locale = Locale.getDefault()): String =
        if (is24Hour) "%02d:%02d".format(Locale.ROOT, t.hour, t.minute) else DateTimeFormatter.ofPattern("h:mm", locale).format(t)

    fun hour(t: LocalTime, is24Hour: Boolean): String {
        val h = if (is24Hour) t.hour else (t.hour % 12).let { if (it == 0) 12 else it }
        return "%02d".format(Locale.ROOT, h)
    }

    fun minute(t: LocalTime): String = "%02d".format(Locale.ROOT, t.minute)

    fun longDate(d: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(d)

    fun shortDate(d: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("EEE d MMM", locale).format(d)

    fun weekday(d: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("EEEE", locale).format(d).uppercase(locale)

    /** "Today", "Tomorrow", else the short weekday. */
    fun day(d: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String = when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> DateTimeFormatter.ofPattern("EEE", locale).format(d)
    }

    /** "10:30 – 11:15" or "All day", prefixed with the day when it is not today. */
    fun eventTime(e: CalendarEvent, today: LocalDate, is24Hour: Boolean, locale: Locale = Locale.getDefault()): String {
        val date = e.start.toLocalDate()
        val span = if (e.allDay) {
            "All day"
        } else {
            "${time(e.start.toLocalTime(), is24Hour, locale)} – ${time(e.end.toLocalTime(), is24Hour, locale)}"
        }
        return if (!date.isAfter(today)) span else "${day(date, today, locale)} · $span"
    }

    fun alarm(at: LocalDateTime, now: LocalDateTime, is24Hour: Boolean, locale: Locale = Locale.getDefault()): String =
        "${time(at.toLocalTime(), is24Hour, locale)} · ${day(at.toLocalDate(), now.toLocalDate(), locale)}"

    fun highLow(high: Int?, low: Int?): String =
        listOfNotNull(high?.let { "H $it°" }, low?.let { "L $it°" }).joinToString("  ")
}
