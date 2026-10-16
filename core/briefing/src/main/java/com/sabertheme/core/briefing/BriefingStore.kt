package com.sabertheme.core.briefing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/** A mail after labelling: the preview is dropped, only what the briefing needs stays. */
@Serializable
data class LabelledEntry(
    val key: String,
    val sender: String,
    val subject: String,
    val postTime: Long,
    val verdict: MailVerdict,
)

@Serializable
internal data class BriefingState(
    /** Mails waiting for `:brain`, with their preview. */
    val queue: List<QueuedMail> = emptyList(),
    val labelled: List<LabelledEntry> = emptyList(),
    /** The last deadlines Moodle gave, so an offline run still has them. */
    val deadlines: List<StoredDeadline> = emptyList(),
    val briefing: Briefing? = null,
    /** Items she has already said aloud, so she doesn't repeat herself. */
    val spoken: List<String> = emptyList(),
    /** Module code → name, from Moodle; refreshed daily. */
    val courses: List<Course> = emptyList(),
    val coursesAtMs: Long = 0,
)

@Serializable
internal data class StoredDeadline(val key: String, val course: String, val task: String, val dueMs: Long, val url: String?, val code: String? = null)

/**
 * Everything the briefing keeps, shared by Home (adds mail, reads the briefing,
 * marks what she said) and `:brain` (labels and composes). Device-only: mail
 * previews live here until labelled, labelled mail at most [KEEP_MS].
 */
@Singleton
class BriefingStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = jsonStore(context, "briefing_state", BriefingState.serializer(), BriefingState())

    val briefing: Flow<Briefing?> = store.data.map { it.briefing }.distinctUntilChanged()

    /** The module index (code → name), for Settings. */
    val courses: Flow<List<Course>> = store.data.map { it.courses }.distinctUntilChanged()

    /** Adds mails not seen before; returns how many were new. */
    suspend fun enqueue(mails: List<QueuedMail>): Int {
        var added = 0
        store.updateData { state ->
            val known = state.queue.mapTo(HashSet()) { it.key } + state.labelled.map { it.key }
            val fresh = mails.filter { it.key !in known }.distinctBy { it.key }
            added = fresh.size
            if (fresh.isEmpty()) state else state.copy(queue = state.queue + fresh)
        }
        return added
    }

    suspend fun markSpoken(keys: Collection<String>) {
        if (keys.isEmpty()) return
        store.updateData { it.copy(spoken = (it.spoken + keys).distinct().takeLast(MAX_SPOKEN)) }
    }

    /** Briefing off: forget mail, deadlines and the briefing itself. */
    suspend fun clear() {
        store.updateData { BriefingState() }
    }

    internal suspend fun snapshot(): BriefingState = store.data.first()

    internal suspend fun update(transform: (BriefingState) -> BriefingState) {
        store.updateData(transform)
    }

    internal companion object {
        const val KEEP_MS = 48 * 3_600_000L
        const val MAX_SPOKEN = 200
    }
}
