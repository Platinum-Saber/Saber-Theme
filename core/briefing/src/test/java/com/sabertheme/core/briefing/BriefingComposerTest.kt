package com.sabertheme.core.briefing

import com.google.common.truth.Truth.assertThat
import com.sabertheme.core.briefing.BriefingInput.Deadline
import com.sabertheme.core.briefing.BriefingInput.Delivery
import com.sabertheme.core.briefing.BriefingInput.Event
import com.sabertheme.core.briefing.BriefingInput.LabelledMail
import com.sabertheme.core.briefing.BriefingInput.MissedCall
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class BriefingComposerTest {

    private val zone = ZoneId.of("Asia/Colombo")
    private val nowLocal = LocalDateTime.of(2026, 10, 16, 8, 0) // Friday
    private val now = nowLocal.atZone(zone).toInstant()

    private fun at(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()

    private fun compose(input: BriefingInput, spoken: Set<String> = emptySet()) =
        BriefingComposer.compose(input, now, zone, spoken)

    private val assignment = Deadline("d1", "Mobile Computing", "Assignment 2", at(nowLocal.plusDays(1).withHour(23).withMinute(59)), "https://moodle/a2", "CS3042")
    private val quiz = Deadline("d2", "Machine Learning", "Quiz 4", at(nowLocal.plusDays(2).withHour(18)), null, "CS3051")
    private val report = Deadline("d3", "Software Architecture", "Design report", at(nowLocal.plusDays(11).withHour(12)), null, "CS3010")

    @Test
    fun `deadline under a day is Now, within three days Soon`() {
        val soon = assignment.copy(dueMs = at(nowLocal.plusHours(20)))
        val items = compose(BriefingInput(deadlines = listOf(quiz, soon))).items
        assertThat(items.map { it.urgency }).containsExactly(Urgency.Now, Urgency.Soon).inOrder()
        assertThat(items.first().detail).isEqualTo("Due tomorrow at 04:00 · CS3042")
    }

    @Test
    fun `deadlines more than three days out are left off`() {
        val justIn = report.copy(dueMs = at(nowLocal.plusHours(71)))
        val justOut = report.copy(key = "d4", dueMs = at(nowLocal.plusHours(73)))
        assertThat(compose(BriefingInput(deadlines = listOf(report, justOut, justIn))).items.map { it.key }).containsExactly("d3")
    }

    @Test
    fun `she says the module name, never its code`() {
        val briefing = compose(BriefingInput(deadlines = listOf(assignment, quiz)))
        assertThat(briefing.spoken).contains("Assignment 2 for Mobile Computing is due tomorrow at 11:59 PM.")
        assertThat(briefing.spoken).doesNotContain("CS3042")
        assertThat(briefing.items.first().title).isEqualTo("Mobile Computing · Assignment 2")
    }

    @Test
    fun `past deadlines are dropped`() {
        val gone = assignment.copy(dueMs = at(nowLocal.minusHours(1)))
        assertThat(compose(BriefingInput(deadlines = listOf(gone))).items).isEmpty()
    }

    @Test
    fun `deadline text names the weekday after tomorrow`() {
        val items = compose(BriefingInput(deadlines = listOf(quiz))).items
        assertThat(items.single().detail).isEqualTo("Due on Sunday at 18:00 · CS3051")
    }

    @Test
    fun `a deadline within six hours is counted down in hours`() {
        val close = assignment.copy(dueMs = at(nowLocal.plusHours(5)))
        assertThat(compose(BriefingInput(deadlines = listOf(close))).urgentSpoken)
            .isEqualTo("Warning. Assignment 2 for Mobile Computing is due in 5 hours.")
    }

    @Test
    fun `reply and urgent mail surface, promo info and delivery labels do not`() {
        val mails = MailLabel.entries.mapIndexed { i, label -> LabelledMail("m$i", "Sender $i", "Subject $i", label, at(nowLocal)) }
        val items = compose(BriefingInput(mails = mails)).items
        assertThat(items.map { it.key }).containsExactly("m0", "m1", "m3") // Urgent, Reply, Bill
        assertThat(items.first { it.key == "m3" }.urgency).isEqualTo(Urgency.Soon)
    }

    @Test
    fun `two missed calls within an hour merge into one pressing item`() {
        val calls = listOf(
            MissedCall("c1", "Amma", "+94771234567", at(nowLocal.minusMinutes(50))),
            MissedCall("c2", "Amma", "+94771234567", at(nowLocal.minusMinutes(48))),
            MissedCall("c3", "+94112345678", "+94112345678", at(nowLocal.minusMinutes(20))),
        )
        val items = compose(BriefingInput(calls = calls)).items
        assertThat(items).hasSize(2)
        assertThat(items[0].title).isEqualTo("Amma")
        assertThat(items[0].urgency).isEqualTo(Urgency.Now)
        assertThat(items[0].detail).isEqualTo("Missed twice · 07:12")
        assertThat(items[1].urgency).isEqualTo(Urgency.Today)
    }

    @Test
    fun `events within two hours are Today, later today Soon, finished dropped`() {
        val events = listOf(
            Event("e1", "Lecture", at(nowLocal.plusMinutes(90)), allDay = false),
            Event("e2", "Dinner", at(nowLocal.withHour(19).withMinute(30)), allDay = false),
            Event("e3", "Breakfast", at(nowLocal.minusHours(1)), allDay = false),
        )
        val items = compose(BriefingInput(events = events)).items
        assertThat(items.map { it.key to it.urgency }).containsExactly("e1" to Urgency.Today, "e2" to Urgency.Soon).inOrder()
        assertThat(items[0].detail).isEqualTo("09:30")
    }

    @Test
    fun `delivery arriving today ranks Today`() {
        val today = Delivery("p1", "USB-C hub", "Out for delivery", "today by 6 pm", arrivesToday = true, at(nowLocal))
        val later = Delivery("p2", "Kotlin book", "Shipped", "Oct 24 - Oct 29", arrivesToday = false, at(nowLocal))
        val items = compose(BriefingInput(deliveries = listOf(later, today))).items
        assertThat(items.map { it.key }).containsExactly("p1", "p2").inOrder()
        assertThat(items[0].detail).isEqualTo("Out for delivery · today by 6 pm")
    }

    @Test
    fun `items sort by urgency, then time`() {
        val input = BriefingInput(
            deadlines = listOf(quiz, assignment.copy(dueMs = at(nowLocal.plusHours(10)))),
            events = listOf(Event("e1", "Lecture", at(nowLocal.plusMinutes(90)), false)),
            mails = listOf(LabelledMail("m1", "TechStart", "Interview slot", MailLabel.Reply, at(nowLocal.minusHours(2)))),
        )
        assertThat(compose(input).items.map { it.key }).containsExactly("d1", "m1", "e1", "d2").inOrder()
    }

    @Test
    fun `spoken line names the top facts and stays under the word cap`() {
        val input = BriefingInput(
            deadlines = listOf(assignment.copy(dueMs = at(nowLocal.plusHours(15)))),
            mails = listOf(LabelledMail("m1", "TechStart", "the internship interview slot", MailLabel.Reply, at(nowLocal))),
            calls = listOf(MissedCall("c1", "Amma", "1", at(nowLocal.minusMinutes(30))), MissedCall("c2", "Amma", "1", at(nowLocal.minusMinutes(29)))),
            deliveries = listOf(Delivery("p1", "USB-C hub", "Out for delivery", "today", true, at(nowLocal))),
            events = listOf(Event("e1", "Mobile Computing lecture", at(nowLocal.plusMinutes(90)), false)),
        )
        val spoken = compose(input).spoken
        assertThat(spoken).containsMatch("^(Notice|Report)\\. Five matters require your attention, Master\\.")
        assertThat(spoken).contains("Assignment 2 for Mobile Computing is due today at 11:00 PM.")
        assertThat(spoken).contains("A missed call from Amma, twice.")
        assertThat(spoken).endsWith("End of report.")
        assertThat(spoken).contains("TechStart awaits your reply")
        assertThat(spoken.split(' ').count { it.isNotBlank() }).isAtMost(BriefingComposer.MAX_SPOKEN_WORDS)
        assertThat(spoken).doesNotContain("<")
    }

    @Test
    fun `announced urgent items stay in the briefing but are not announced again`() {
        val soon = assignment.copy(dueMs = at(nowLocal.plusHours(10)))
        val briefing = compose(BriefingInput(deadlines = listOf(soon)), spoken = setOf("d1"))
        assertThat(briefing.items).hasSize(1)
        assertThat(briefing.spoken).contains("Assignment 2 for Mobile Computing")
        assertThat(briefing.urgentKeys).isEmpty()
        assertThat(briefing.urgentSpoken).isEmpty()
    }

    @Test
    fun `urgent line names only the pressing items`() {
        val soon = assignment.copy(dueMs = at(nowLocal.plusHours(10)))
        val briefing = compose(BriefingInput(deadlines = listOf(soon, quiz)))
        assertThat(briefing.urgentSpoken).isEqualTo("Warning. Assignment 2 for Mobile Computing is due today at 6:00 PM.")
    }

    @Test
    fun `an empty day is all quiet`() {
        assertThat(compose(BriefingInput()).spoken).isEqualTo(BriefingComposer.ALL_QUIET)
    }

    @Test
    fun `urgent keys are the fresh Now items`() {
        val soon = assignment.copy(dueMs = at(nowLocal.plusHours(10)))
        assertThat(compose(BriefingInput(deadlines = listOf(soon, quiz))).urgentKeys).containsExactly("d1")
    }

    @Test
    fun `same seed gives the same words`() {
        val input = BriefingInput(deadlines = listOf(quiz))
        assertThat(BriefingComposer.compose(input, now, zone, seed = 7).spoken)
            .isEqualTo(BriefingComposer.compose(input, now, zone, seed = 7).spoken)
    }
}
