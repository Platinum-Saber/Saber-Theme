package com.sabertheme.core.widgetdata

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One unread chat: [chat] is the conversation (a contact or group name),
 * [sender] who wrote [text] when that differs (groups), [count] how many
 * messages the notification holds, [open] its tap action.
 */
data class ChatMessage(
    val key: String,
    val chat: String,
    val sender: String?,
    val text: String,
    val count: Int,
    val postTime: Long,
    val open: PendingIntent?,
    val packageName: String,
) {
    /**
     * Opens the chat from a tap on Home: the notification's own intent (we
     * lend it our visible-app right to start activities), else the app.
     */
    fun openChat(context: Context) {
        val options = ActivityOptions.makeBasic().apply {
            if (Build.VERSION.SDK_INT >= 36) {
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
            } else if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION")
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
        }
        val intent = open
        val sent = intent != null && runCatching { intent.send(context, 0, null, null, null, null, options.toBundle()) }.isSuccess
        if (!sent) {
            context.packageManager.getLaunchIntentForPackage(packageName)
                ?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
    }
}

/**
 * WhatsApp chats with unread notifications, newest first, fed by
 * [MediaListenerService]. Empty while listener access is off. A chat leaves
 * the list when its notification goes (read or dismissed on the phone).
 */
object ChatNotifications {
    val PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

    private val byKey = MutableStateFlow<Map<String, ChatMessage>>(emptyMap())
    private val sorted = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = sorted.asStateFlow()

    /** Cancels notifications by key; set while the listener is connected. */
    @Volatile internal var canceller: ((List<String>) -> Unit)? = null

    /** Dismisses these chats' notifications on the phone (not read in the app). */
    fun dismiss(keys: List<String>) {
        canceller?.invoke(keys)
        byKey.update { it - keys.toSet() }
        publish()
    }

    internal fun reset(active: List<StatusBarNotification>) {
        byKey.value = active.mapNotNull { it.toChatMessage() }.associateBy { it.key }
        publish()
    }

    internal fun post(sbn: StatusBarNotification) {
        if (sbn.packageName !in PACKAGES) return
        val message = sbn.toChatMessage()
        byKey.update { if (message == null) it - sbn.key else it + (sbn.key to message) }
        publish()
    }

    internal fun remove(key: String) {
        byKey.update { it - key }
        publish()
    }

    private fun publish() {
        sorted.value = byKey.value.values.sortedByDescending { it.postTime }
    }
}

private fun StatusBarNotification.toChatMessage(): ChatMessage? {
    if (packageName !in ChatNotifications.PACKAGES) return null
    val n = notification
    // The "N messages from M chats" summary and the "checking for messages" service notice.
    if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0 || isOngoing) return null
    val extras = n.extras
    val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
    val chat = (style?.conversationTitle ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString()
        ?.replace(MESSAGE_COUNT, "")?.takeIf { it.isNotBlank() } ?: return null
    val latest = style?.messages?.lastOrNull()
    val text = (latest?.text ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.takeIf { it.isNotBlank() } ?: return null
    val sender = latest?.person?.name?.toString()?.takeIf { it != chat }
    return ChatMessage(key, chat, sender, text, style?.messages?.size?.coerceAtLeast(1) ?: 1, postTime, n.contentIntent, packageName)
}

/** WhatsApp appends " (72 messages)" to group titles; the cloud has its own count. */
private val MESSAGE_COUNT = Regex("""\s*\(\d+ messages?\)$""")
