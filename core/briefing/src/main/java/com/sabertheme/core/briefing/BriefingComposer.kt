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
 * Ranks a run's facts and phrases them in her voice. Pure: no model, no clock,
 * no Android, so every rule is tested. The model only labels mail upstream;
 * when it phrased the briefing it just read the facts back, so the words are
 * written here.
 */
internal object BriefingComposer {

    const val MAX_SPOKEN_WORDS = 50
    private const val SPOKEN_FACTS = 4
    private const val HOUR_MS = 3_600_000L

    private val Openers = listOf(
        "Master, I bring news of %s.",
        "Hear me, Master! I count %s.",
        "Your knight reports %s.",
    )
    private val Closers = listOf("I stand ready.", "Onward, Master!", "Fight well.")
    const val ALL_QUIET = "All is quiet, Master. Nothing demands your blade."

    private val Clock = DateTimeFormatter.ofPattern("HH:mm")
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
            input.deadlines.filter { it.dueMs > nowMs }.forEach { add(deadline(it, nowMs, zone, today)) }
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
        val days = ChronoUnit.DAYS.between(today, local(d.dueMs, zone).toLocalDate())
        val urgency = when {
            d.dueMs - nowMs < 24 * HOUR_MS -> Urgency.Now
            days <= 3 -> Urgency.Soon
            else -> Urgency.Later
        }
        val due = whenText(d.dueMs, zone, today)
        return Line(
            BriefingItem(d.key, ItemKind.Deadline, "${d.course} · ${d.task}", "Due $due", urgency, d.dueMs, d.url),
            "${d.task} for ${d.course} is due $due",
        )
    }

    private fun mail(m: LabelledMail): Line? {
        val (urgency, speech) = when (m.label) {
            MailLabel.Urgent -> Urgency.Today to "an urgent mail from ${m.sender}: ${m.subject}"
            MailLabel.Reply -> Urgency.Today to "${m.sender} awaits your reply about ${m.subject}"
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
        if (d.arrivesToday) "your ${d.item} arrives today" else "your ${d.item} is on its way",
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
            if (e.allDay) "${e.title} today" else "${e.title} at $at",
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
                "${latest.who} called $times",
            )
        }

    private fun spoken(lines: List<Line>, random: Random): String {
        if (lines.isEmpty()) return ALL_QUIET
        val opener = Openers.random(random).format(count(lines.size))
        val closer = Closers.random(random)
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
        return (listOf("Master, quickly!") + facts).joinToString(" ")
    }

    private fun count(n: Int) = when (n) {
        1 -> "one matter"
        2 -> "two matters"
        3 -> "three matters"
        4 -> "four matters"
        else -> "$n matters"
    }

    private fun whenText(ms: Long, zone: ZoneId, today: LocalDate): String {
        val at = local(ms, zone)
        return when (ChronoUnit.DAYS.between(today, at.toLocalDate())) {
            0L -> "today at ${at.format(Clock)}"
            1L -> "tomorrow at ${at.format(Clock)}"
            in 2..6 -> "on ${at.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
            else -> "on ${at.format(DayMonth)}"
        }
    }

    private fun local(ms: Long, zone: ZoneId): LocalDateTime = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()

    private fun wordCount(s: String) = s.split(' ').count { it.isNotBlank() }
}
