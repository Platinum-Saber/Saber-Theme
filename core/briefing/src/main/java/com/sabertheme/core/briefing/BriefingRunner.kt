package com.sabertheme.core.briefing

import android.os.SystemClock
import android.util.Log
import com.sabertheme.core.widgetdata.CalendarSource
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One briefing run, in `:brain`: label queued mail, read calendar, calls and
 * Moodle, compose, store. Each source failing alone just leaves its part out.
 * Logs carry counts and timings only.
 */
@Singleton
class BriefingRunner @Inject constructor(
    private val store: BriefingStore,
    private val labeler: MailLabeler,
    private val calendar: CalendarSource,
    private val calls: CallLogSource,
    private val moodle: MoodleClient,
) {
    suspend fun run() {
        val started = SystemClock.elapsedRealtime()
        labelQueue()

        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val nowMs = now.toEpochMilli()

        val deadlines = try {
            moodle.deadlines(nowMs)?.also { fresh ->
                store.update { s -> s.copy(deadlines = fresh.map { StoredDeadline(it.key, it.course, it.task, it.dueMs, it.url) }) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Moodle skipped: ${e.javaClass.simpleName}")
            null
        } ?: store.snapshot().deadlines.map { BriefingInput.Deadline(it.key, it.course, it.task, it.dueMs, it.url) }

        val events = calendar.upcoming(hours = 24).map {
            BriefingInput.Event("event:${it.id}:${it.start}", it.title, it.start.atZone(zone).toInstant().toEpochMilli(), it.allDay)
        }
        val missed = calls.missedSince(nowMs - DAY_MS)

        val state = store.snapshot()
        val recent = state.labelled.filter { nowMs - it.postTime < DAY_MS }
        val input = BriefingInput(
            deadlines = deadlines,
            mails = recent.map { BriefingInput.LabelledMail(it.key, it.sender, it.subject, it.verdict.label, it.postTime) },
            deliveries = recent.mapNotNull { e ->
                val info = e.verdict.delivery ?: return@mapNotNull null
                BriefingInput.Delivery(e.key, info.item, info.status, info.eta, MailRules.arrivesToday(info), e.postTime)
            },
            events = events,
            calls = missed,
        )
        val briefing = BriefingComposer.compose(input, now, zone, state.spoken.toSet(), seed = LocalDate.now(zone).dayOfYear)
        store.update { it.copy(briefing = briefing) }
        Log.i(
            TAG,
            "Briefing: ${briefing.items.size} items, ${briefing.urgentKeys.size} urgent " +
                "(${deadlines.size} deadlines, ${recent.size} mails, ${events.size} events, ${missed.size} calls) " +
                "in ${SystemClock.elapsedRealtime() - started} ms",
        )
    }

    /** Labels until the queue is empty, so mail that lands mid-run isn't left for the hourly run. */
    private suspend fun labelQueue() {
        repeat(MAX_ROUNDS) {
            val pending = store.snapshot().queue
            if (pending.isEmpty()) return
            val verdicts = labeler.label(pending)
            val cutoff = System.currentTimeMillis() - BriefingStore.KEEP_MS
            store.update { s ->
                val done = s.queue.filter { it.key in verdicts }
                s.copy(
                    queue = s.queue.filter { it.key !in verdicts && it.postTime > cutoff },
                    labelled = (s.labelled + done.map { LabelledEntry(it.key, it.sender, it.subject, it.postTime, verdicts.getValue(it.key)) })
                        .filter { it.postTime > cutoff },
                )
            }
        }
    }

    private companion object {
        const val TAG = "SaberBrain"
        const val DAY_MS = 24 * 3_600_000L
        const val MAX_ROUNDS = 3
    }
}
