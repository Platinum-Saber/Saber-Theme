package com.sabertheme.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sabertheme.core.model.AppKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Launch counts per app, for the drawer's Suggested row. */
@Singleton
class LaunchStats @Inject constructor(private val store: DataStore<Preferences>) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val counts: Flow<Map<AppKey, Int>> = store.data
        .map { decode(it[COUNTS]) }
        .distinctUntilChanged()

    /** Fire-and-forget; launching must not wait on disk. */
    fun record(key: AppKey) {
        scope.launch {
            store.edit { prefs ->
                val counts = decode(prefs[COUNTS])
                prefs[COUNTS] = encode(counts + (key to (counts[key] ?: 0) + 1))
            }
        }
    }

    companion object {
        /** Most-launched first, ties by key, at most [limit]. */
        fun top(counts: Map<AppKey, Int>, limit: Int): List<AppKey> =
            counts.entries.sortedWith(compareByDescending<Map.Entry<AppKey, Int>> { it.value }.thenBy { it.key.encode() })
                .take(limit)
                .map { it.key }

        private val COUNTS = stringPreferencesKey("launch_counts")

        /** Bounds the stored map; the least-launched apps drop off. */
        const val MAX_ENTRIES = 64

        internal fun encode(counts: Map<AppKey, Int>): String = counts.entries
            .sortedByDescending { it.value }
            .take(MAX_ENTRIES)
            .joinToString("\n") { "${it.key.encode()}\t${it.value}" }

        internal fun decode(value: String?): Map<AppKey, Int> = value.orEmpty().lineSequence()
            .mapNotNull { line ->
                val tab = line.lastIndexOf('\t')
                if (tab < 0) return@mapNotNull null
                val key = AppKey.decode(line.substring(0, tab)) ?: return@mapNotNull null
                val count = line.substring(tab + 1).toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                key to count
            }
            .toMap()
    }
}
