package com.sabertheme.core.briefing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** A Gmail notification waiting to be labelled; previews never leave the phone. */
@Serializable
data class QueuedMail(
    val key: String,
    val sender: String,
    val subject: String,
    val preview: String,
    val postTime: Long,
)

/** What the parcel line says, when the mail is about one. */
@Serializable
data class DeliveryInfo(val item: String, val status: String, val eta: String)

@Serializable
data class MailVerdict(val label: MailLabel, val delivery: DeliveryInfo? = null)

/** The prompt that labelled 4 of 5 test mails correctly on the S23 (temperature 0.25). */
internal object MailPrompt {
    fun build(mail: QueuedMail, today: String): String = """
        Classify one email for a student's phone assistant. Reply with ONLY one line of JSON:
        {"label":"...","reason":"<max 8 words>","action":"<max 8 words or none>","delivery":null}
        If label is delivery, replace null with {"item":"","status":"","eta":""}.

        Labels:
        urgent = must act today or tomorrow
        reply = a person is waiting for my answer
        delivery = a parcel is shipped or arriving
        bill = money is due
        info = useful to know, no action
        promo = ads, newsletters, discounts, automated notices

        Example:
        EMAIL: From: noreply@spotify.com | Subject: New Music Friday | 30 new tracks picked for you.
        {"label":"promo","reason":"music newsletter","action":"none","delivery":null}

        Today is $today.
        EMAIL: From: ${oneLine(mail.sender)} | Subject: ${oneLine(mail.subject)} | ${oneLine(mail.preview).take(MAX_PREVIEW)}
    """.trimIndent()

    private fun oneLine(s: String) = s.replace(Regex("\\s+"), " ").trim()

    private const val MAX_PREVIEW = 600
}

/** Pulls the verdict out of the model's reply, tolerating chatter around the JSON. */
internal object LabelParser {
    fun parse(reply: String): MailVerdict? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = runCatching { BriefingJson.parseToJsonElement(reply.substring(start, end + 1)) as? JsonObject }.getOrNull()
            ?: return null
        val label = obj.string("label")?.let { name -> MailLabel.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } }
            ?: return null
        val delivery = (obj["delivery"] as? JsonObject)?.let {
            DeliveryInfo(it.string("item").orEmpty(), it.string("status").orEmpty(), it.string("eta").orEmpty())
        }?.takeIf { it.item.isNotBlank() }
        return MailVerdict(label, if (label == MailLabel.Delivery) delivery else null)
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.jsonPrimitive?.contentOrNull
}

/**
 * Cheap rules around the model: [before] settles obvious senders without
 * loading it (the model called a Medium newsletter "info"); [fallback] labels
 * everything when the model file is missing.
 */
internal object MailRules {

    private val PromoSender = Regex("""^(newsletter|newsletters|promo|promotions|marketing|deals|offers|digest)[^@]*@""", RegexOption.IGNORE_CASE)
    private val InfoSender = Regex("""@(github\.com|gitlab\.com|accounts\.google\.com)$|^(security|notifications?)@""", RegexOption.IGNORE_CASE)

    fun before(mail: QueuedMail): MailLabel? {
        val sender = address(mail.sender)
        return when {
            PromoSender.containsMatchIn(sender) -> MailLabel.Promo
            InfoSender.containsMatchIn(sender) -> MailLabel.Info
            else -> null
        }
    }

    private val Urgent = Regex("""\b(urgent|deadline|due (today|tomorrow|tonight)|closes? (today|tonight)|last chance to register)\b""", RegexOption.IGNORE_CASE)
    private val Reply = Regex("""\b(please (reply|confirm|respond|let (me|us) know)|reply with|rsvp)\b""", RegexOption.IGNORE_CASE)
    private val Delivery = Regex("""\b(shipped|out for delivery|delivered|arriving|in transit|tracking)\b""", RegexOption.IGNORE_CASE)
    private val Bill = Regex("""\b(amount due|bill|invoice|payment due)\b""", RegexOption.IGNORE_CASE)
    private val Promo = Regex("""\b(\d+% off|sale|discount|coupon|promo code|unsubscribe)\b""", RegexOption.IGNORE_CASE)

    fun fallback(mail: QueuedMail): MailVerdict {
        before(mail)?.let { return MailVerdict(it) }
        val text = "${mail.subject} ${mail.preview}"
        val label = when {
            Delivery.containsMatchIn(text) -> MailLabel.Delivery
            Reply.containsMatchIn(text) -> MailLabel.Reply
            Urgent.containsMatchIn(text) -> MailLabel.Urgent
            Bill.containsMatchIn(text) -> MailLabel.Bill
            Promo.containsMatchIn(text) -> MailLabel.Promo
            else -> MailLabel.Info
        }
        return MailVerdict(label, if (label == MailLabel.Delivery) DeliveryInfo(mail.subject, "", "") else null)
    }

    /**
     * What she says for a sender: "TechStart HR <hr@techstart.io>" → "TechStart HR";
     * a bare address becomes its domain's name ("no-reply@daraz.lk" → "daraz").
     */
    fun displayName(sender: String): String {
        val name = sender.substringBefore('<').trim().trim('"', '\'')
        if (name.isNotEmpty() && '@' !in name) return name
        return address(sender).substringAfter('@').substringBefore('.').ifBlank { sender.trim() }
    }

    /** "Daraz <no-reply@daraz.lk>" → "no-reply@daraz.lk"; a bare name stays as it is. */
    fun address(sender: String): String =
        Regex("<([^>]+)>").find(sender)?.groupValues?.get(1)?.trim() ?: sender.trim()

    /** Whether a delivery line means it comes today. */
    fun arrivesToday(info: DeliveryInfo): Boolean =
        "today" in info.eta.lowercase() || "out for delivery" in info.status.lowercase()
}
