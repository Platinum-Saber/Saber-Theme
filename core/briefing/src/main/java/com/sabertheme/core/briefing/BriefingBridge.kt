package com.sabertheme.core.briefing

import android.content.Context
import com.sabertheme.core.widgetdata.GmailMessage
import com.sabertheme.core.widgetdata.GmailNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Home's side of the briefing (main process only): while it is on, new Gmail
 * notifications go to the queue and wake a run in `:brain`; switching it off
 * stops the runs and forgets everything it kept.
 */
@Singleton
class BriefingBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: BriefingStore,
) {
    fun start(scope: CoroutineScope, enabled: Flow<Boolean>) {
        scope.launch {
            enabled.distinctUntilChanged().collect { on ->
                if (on) {
                    GmailNotifications.onNew = { mails -> scope.launch { enqueue(mails) } }
                    enqueue(GmailNotifications.messages.value)
                    Briefings.schedule(context)
                    Briefings.runSoon(context, delayMs = 0)
                } else {
                    GmailNotifications.onNew = null
                    Briefings.cancel(context)
                    store.clear()
                }
            }
        }
    }

    /** A run right away: a source was just connected or allowed in Settings. */
    fun refreshNow() = Briefings.runSoon(context, delayMs = 0)

    /** A fresh run when Home comes back and the briefing is older than [maxAgeMs]. */
    suspend fun refreshIfStale(builtAtMs: Long?, maxAgeMs: Long = 30 * 60_000L) {
        if (builtAtMs == null || System.currentTimeMillis() - builtAtMs > maxAgeMs) Briefings.runSoon(context, delayMs = 0)
    }

    private suspend fun enqueue(mails: List<GmailMessage>) {
        val added = store.enqueue(mails.map { QueuedMail(it.key, it.sender, it.subject, it.preview, it.postTime) })
        if (added > 0) Briefings.runSoon(context)
    }
}
