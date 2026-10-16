package com.sabertheme.core.widgetdata

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One unread mail from a Gmail notification: [sender] as Gmail shows it,
 * [subject] and the body [preview] (empty when the notification has none),
 * [open] its tap action. [key] is the notification key, suffixed `#n` for
 * the n-th line of an InboxStyle bundle.
 */
data class GmailMessage(
    val key: String,
    val sender: String,
    val subject: String,
    val preview: String,
    val postTime: Long,
    val open: PendingIntent?,
)

/**
 * Gmail mails with unread notifications, newest first, fed by
 * [MediaListenerService]. Empty while listener access is off. A mail leaves
 * the list when its notification goes (read, archived or dismissed).
 */
object GmailNotifications {
    val PACKAGES = setOf("com.google.android.gm")

    private val byKey = MutableStateFlow<Map<String, List<GmailMessage>>>(emptyMap())
    private val sorted = MutableStateFlow<List<GmailMessage>>(emptyList())
    val messages: StateFlow<List<GmailMessage>> = sorted.asStateFlow()

    /**
     * Called with mails whose notification key wasn't listed before, on the
     * listener's thread. A reconnect replays every active mail, so consumers
     * dedupe by [GmailMessage.key].
     */
    @Volatile var onNew: ((List<GmailMessage>) -> Unit)? = null

    internal fun reset(active: List<StatusBarNotification>) {
        val fresh = active.filter { it.packageName in PACKAGES }
            .associate { it.key to it.toGmailMessages() }
            .filterValues { it.isNotEmpty() }
        val before = byKey.value
        byKey.value = fresh
        publish()
        notifyNew(fresh.filterKeys { it !in before }.values.flatten())
    }

    internal fun post(sbn: StatusBarNotification) {
        if (sbn.packageName !in PACKAGES) return
        val mails = sbn.toGmailMessages()
        val isNew = sbn.key !in byKey.value
        byKey.update { if (mails.isEmpty()) it - sbn.key else it + (sbn.key to mails) }
        publish()
        if (isNew) notifyNew(mails)
    }

    internal fun remove(key: String) {
        if (key !in byKey.value) return
        byKey.update { it - key }
        publish()
    }

    private fun notifyNew(mails: List<GmailMessage>) {
        if (mails.isNotEmpty()) onNew?.invoke(mails)
    }

    private fun publish() {
        sorted.value = byKey.value.values.flatten().sortedByDescending { it.postTime }
    }
}

private fun StatusBarNotification.toGmailMessages(): List<GmailMessage> {
    val n = notification
    val extras = n.extras
    val fields = GmailFields(
        key = key,
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
        textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() },
        isSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        isOngoing = isOngoing,
        postTime = postTime,
    )
    return GmailMapper.toMessages(fields, n.contentIntent)
}

/** The parts of a Gmail notification [GmailMapper] reads, as plain values. */
internal data class GmailFields(
    val key: String,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val textLines: List<String>?,
    val isSummary: Boolean,
    val isOngoing: Boolean,
    val postTime: Long,
)

/**
 * Gmail (as seen on the S23) posts one BigTextStyle notification
 * per mail: title = sender, text = subject, bigText = subject + "\n" + body,
 * subText = the account. Each account/label group also gets a summary, a copy
 * of the mail for one, InboxStyle with "sender   subject" lines for more;
 * summaries duplicate the children, so they're skipped. A non-summary
 * InboxStyle bundle still maps to one mail per line.
 */
internal object GmailMapper {
    /** Bounds what a long mail keeps in memory; the body tail adds little. */
    const val MAX_PREVIEW = 4_000

    fun toMessages(f: GmailFields, open: PendingIntent?): List<GmailMessage> {
        if (f.isSummary || f.isOngoing) return emptyList()
        val lines = f.textLines?.filter { it.isNotBlank() }
        if (!lines.isNullOrEmpty()) {
            return lines.mapIndexed { i, line ->
                val parts = line.split(LINE_SEPARATOR, limit = 2)
                val (sender, subject) = if (parts.size == 2) parts[0].trim() to parts[1].trim() else (f.title?.trim().orEmpty()) to line.trim()
                GmailMessage("${f.key}#$i", sender, subject, "", f.postTime, open)
            }
        }
        val sender = f.title?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val big = f.bigText?.takeIf { it.isNotBlank() }
        val subject: String
        val preview: String
        if (big != null) {
            val head = f.text?.trim()?.takeIf { it.isNotEmpty() && big.startsWith(it) }
            if (head != null) {
                subject = head
                preview = big.removePrefix(head)
            } else {
                subject = big.substringBefore('\n')
                preview = big.substringAfter('\n', "")
            }
        } else {
            subject = f.text?.trim().orEmpty()
            preview = ""
        }
        if (subject.isBlank() && preview.isBlank()) return emptyList()
        return listOf(GmailMessage(f.key, sender, subject.trim(), preview.trim().take(MAX_PREVIEW), f.postTime, open))
    }

    /** Between sender and subject in an InboxStyle line: space, NBSP, space. */
    private val LINE_SEPARATOR = Regex("""\s* \s*""")
}
