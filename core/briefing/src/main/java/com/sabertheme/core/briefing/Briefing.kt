package com.sabertheme.core.briefing

import kotlinx.serialization.Serializable

/** What a briefing line is about; decides its icon, tap target and phrasing. */
@Serializable
enum class ItemKind { Deadline, Delivery, Mail, Event, MissedCall }

/** How soon it matters, most pressing first; [Now] items may be spoken unasked. */
@Serializable
enum class Urgency { Now, Today, Soon, Later }

/** One line of her briefing, as her cloud shows it. [key] is stable across runs. */
@Serializable
data class BriefingItem(
    val key: String,
    val kind: ItemKind,
    val title: String,
    val detail: String,
    val urgency: Urgency,
    val atMs: Long? = null,
    val link: String? = null,
)

/**
 * The latest briefing: [spoken] is what she says when asked; [urgentSpoken]
 * covers only [urgentKeys], the pressing items she hasn't announced yet and
 * may say unasked.
 */
@Serializable
data class Briefing(
    val builtAtMs: Long,
    val items: List<BriefingItem>,
    val spoken: String,
    val urgentKeys: List<String>,
    val urgentSpoken: String = "",
)

/** Labels the mail labeller can give; see `MailLabeler`. */
@Serializable
enum class MailLabel { Urgent, Reply, Delivery, Bill, Info, Promo }

/** Raw facts one run gathered, before the composer ranks and phrases them. */
data class BriefingInput(
    val deadlines: List<Deadline> = emptyList(),
    val mails: List<LabelledMail> = emptyList(),
    val deliveries: List<Delivery> = emptyList(),
    val events: List<Event> = emptyList(),
    val calls: List<MissedCall> = emptyList(),
) {
    /** A Moodle action event that still needs a submission; [course] is the spoken name, [code] e.g. "EN3330". */
    data class Deadline(val key: String, val course: String, val task: String, val dueMs: Long, val url: String?, val code: String? = null)

    data class LabelledMail(val key: String, val sender: String, val subject: String, val label: MailLabel, val atMs: Long)

    /** [arrivesToday] comes from the labeller's eta, which is free text. */
    data class Delivery(val key: String, val item: String, val status: String, val eta: String, val arrivesToday: Boolean, val atMs: Long)

    data class Event(val key: String, val title: String, val startMs: Long, val allDay: Boolean)

    /** [who] is the contact name, or the number when it has none. */
    data class MissedCall(val key: String, val who: String, val number: String, val atMs: Long)
}
