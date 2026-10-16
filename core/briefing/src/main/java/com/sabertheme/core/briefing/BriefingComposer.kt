package com.sabertheme.core.briefing

import com.sabertheme.core.briefing.BriefingInput.Deadline
import com.sabertheme.core.briefing.BriefingInput.Delivery
import com.sabertheme.core.briefing.BriefingInput.Event
import com.sabertheme.core.briefing.BriefingInput.LabelledMail
import com.sabertheme.core.briefing.BriefingInput.MissedCall
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.random.Random

/**
 * Ranks a run's facts and phrases them in her voice: terse and analytic, like
 * a sage reporting to its master ("Notice.", "Warning.", "End of report.").
 * Pure: no model, no clock, no Android, so every rule is tested. The model
 * only labels mail upstream; when it phrased the briefing it just read the
 * facts back, so the words are written here. Module codes are never spoken.
 */
internal object BriefingComposer {

    const val MAX_SPOKEN_WORDS = 50
    private const val SPOKEN_FACTS = 4
    private const val HOUR_MS = 3_600_000L

    /** Deadlines further out than this stay off the briefing. */
    const val DEADLINE_WINDOW_MS = 3 * 24 * HOUR_MS

    private val Openers = listOf("Notice.", "Report.")
    private const val CLOSER = "End of report."
    const val ALL_QUIET = "Report. There are no pending matters, Master."

    private val Clock = DateTimeFormatter.ofPattern("HH:mm")
    private val SpokenClock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val DayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /** An item plus the clause she says for it. */
    private class Line(val item: BriefingItem, val speech: String)

    fun compose(
        input: BriefingInput,
        now: Instant,
        zone: ZoneId,
        alreadySpoken: Set<String> = emptySet(),
        seed: Int = 0,
    ): Briefing {
        val nowMs = now.toEpochMilli()
        val today = now.atZone(zone).toLocalDate()
        val lines = buildList {
            input.deadlines.filter { it.dueMs > nowMs && it.dueMs - nowMs <= DEADLINE_WINDOW_MS }
                .forEach { add(deadline(it, nowMs, zone, today)) }
            input.mails.forEach { mail(it)?.let(::add) }
            input.deliveries.forEach { add(delivery(it)) }
            input.events.forEach { event(it, nowMs, zone, today)?.let(::add) }
            addAll(calls(input.calls, zone))
        }.sortedWith(compareBy<Line> { it.item.urgency }.thenBy { it.item.atMs ?: Long.MAX_VALUE })

        val urgent = lines.filter { it.item.urgency == Urgency.Now && it.item.key !in alreadySpoken }
        return Briefing(
            builtAtMs = nowMs,
            items = lines.map { it.item },
            spoken = spoken(lines, Random(seed)),
            urgentKeys = urgent.map { it.item.key },
            urgentSpoken = if (urgent.isEmpty()) "" else urgentSpoken(urgent),
        )
    }

    private fun deadline(d: Deadline, nowMs: Long, zone: ZoneId, today: LocalDate): Line {
        val left = d.dueMs - nowMs
        val urgency = if (left < 24 * HOUR_MS) Urgency.Now else Urgency.Soon
        val detail = listOfNotNull("Due ${whenText(d.dueMs, zone, today, Clock)}", d.code).joinToString(" · ")
        val due = if (left < 6 * HOUR_MS) inHours(left) else whenText(d.dueMs, zone, today, SpokenClock)
        return Line(
            BriefingItem(d.key, ItemKind.Deadline, "${d.course} · ${d.task}", detail, urgency, d.dueMs, d.url),
            "${d.task} for ${d.course} is due $due",
        )
    }

    private fun inHours(ms: Long): String {
        val hours = (ms + HOUR_MS / 2) / HOUR_MS
        return when {
            hours < 1 -> "within the hour"
            hours == 1L -> "in one hour"
            else -> "in $hours hours"
        }
    }

    private fun mail(m: LabelledMail): Line? {
        val (urgency, speech) = when (m.label) {
            MailLabel.Urgent -> Urgency.Today to "urgent mail from ${m.sender}: ${m.subject}"
            MailLabel.Reply -> Urgency.Today to "${m.sender} awaits your reply regarding ${m.subject}"
            MailLabel.Bill -> Urgency.Soon to "a bill from ${m.sender}: ${m.subject}"
            // Deliveries arrive as their own facts; info and promo never surface.
            MailLabel.Delivery, MailLabel.Info, MailLabel.Promo -> return null
        }
        return Line(BriefingItem(m.key, ItemKind.Mail, m.sender, m.subject, urgency, m.atMs), speech)
    }

    private fun delivery(d: Delivery): Line = Line(
        BriefingItem(
            d.key, ItemKind.Delivery, d.item,
            listOf(d.status, d.eta).filter { it.isNotBlank() }.joinToString(" · "),
            if (d.arrivesToday) Urgency.Today else Urgency.Later, d.atMs,
        ),
        if (d.arrivesToday) "the ${d.item} arrives today" else "the ${d.item} is in transit",
    )

    private fun event(e: Event, nowMs: Long, zone: ZoneId, today: LocalDate): Line? {
        val start = local(e.startMs, zone)
        val urgency = when {
            e.allDay -> if (start.toLocalDate() == today) Urgency.Soon else Urgency.Later
            e.startMs < nowMs -> return null
            e.startMs - nowMs <= 2 * HOUR_MS -> Urgency.Today
            start.toLocalDate() == today -> Urgency.Soon
            else -> Urgency.Later
        }
        val at = if (e.allDay) "All day" else start.format(Clock)
        return Line(
            BriefingItem(e.key, ItemKind.Event, e.title, at, urgency, e.startMs),
            if (e.allDay) "${e.title} is today" else "${e.title} begins at ${start.format(SpokenClock)}",
        )
    }

    /** One item per number; two or more misses within an hour is pressing. */
    private fun calls(calls: List<MissedCall>, zone: ZoneId): List<Line> =
        calls.groupBy { it.number }.map { (_, group) ->
            val sorted = group.sortedBy { it.atMs }
            val latest = sorted.last()
            val repeated = sorted.zipWithNext().any { (a, b) -> b.atMs - a.atMs <= HOUR_MS }
            val times = when (sorted.size) { 1 -> "once"; 2 -> "twice"; else -> "${sorted.size} times" }
            Line(
                BriefingItem(
                    key = "call:${latest.number}:${latest.atMs}", kind = ItemKind.MissedCall, title = latest.who,
                    detail = "Missed $times · ${local(latest.atMs, zone).format(Clock)}",
                    urgency = if (repeated) Urgency.Now else Urgency.Today, atMs = latest.atMs, link = latest.number,
                ),
                "a missed call from ${latest.who}" + if (sorted.size > 1) ", $times" else "",
            )
        }

    private fun spoken(lines: List<Line>, random: Random): String {
        if (lines.isEmpty()) return ALL_QUIET
        val opener = "${Openers.random(random)} ${count(lines.size)}"
        val closer = CLOSER
        var words = wordCount(opener) + wordCount(closer)
        val facts = mutableListOf<String>()
        for (line in lines.take(SPOKEN_FACTS)) {
            val fact = line.speech.replaceFirstChar { it.uppercase() } + "."
            if (words + wordCount(fact) > MAX_SPOKEN_WORDS) break
            facts += fact
            words += wordCount(fact)
        }
        return (listOf(opener) + facts + closer).joinToString(" ")
    }

    /** Unasked, so short: an alarm word, then the pressing facts. */
    private fun urgentSpoken(urgent: List<Line>): String {
        val facts = mutableListOf<String>()
        var words = 2
        for (line in urgent.take(SPOKEN_FACTS)) {
            val fact = line.speech.replaceFirstChar { it.uppercase() } + "."
            if (words + wordCount(fact) > MAX_SPOKEN_WORDS) break
            facts += fact
            words += wordCount(fact)
        }
        return (listOf("Warning.") + facts).joinToString(" ")
    }

    private val Numbers = listOf("Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

    private fun count(n: Int) =
        if (n == 1) "One matter requires your attention, Master."
        else "${Numbers.getOrElse(n - 2) { n.toString() }} matters require your attention, Master."

    private fun whenText(ms: Long, zone: ZoneId, today: LocalDate, clock: DateTimeFormatter): String {
        val at = local(ms, zone)
        val time = at.format(clock)
        return when (ChronoUnit.DAYS.between(today, at.toLocalDate())) {
            0L -> "today at $time"
            1L -> "tomorrow at $time"
            in 2..6 -> "on ${at.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} at $time"
            else -> "on ${at.format(DayMonth)}"
        }
    }

    private fun local(ms: Long, zone: ZoneId): LocalDateTime = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()

    private fun wordCount(s: String) = s.split(' ').count { it.isNotBlank() }
}
