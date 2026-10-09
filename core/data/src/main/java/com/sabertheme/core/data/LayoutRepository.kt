package com.sabertheme.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sabertheme.core.model.HomeLayout
import com.sabertheme.core.model.HomeLayoutCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Persisted home layout. Emits null until one has been saved (first run). */
@Singleton
class LayoutRepository @Inject constructor(private val store: DataStore<Preferences>) {

    val layout: Flow<HomeLayout?> = store.data
        .map { HomeLayoutCodec.decode(it[LAYOUT]) }
        .distinctUntilChanged()

    suspend fun save(layout: HomeLayout) {
        val encoded = HomeLayoutCodec.encode(layout)
        store.edit { if (it[LAYOUT] != encoded) it[LAYOUT] = encoded }
    }

    /** Atomically rewrites the saved layout; no-op before the first save. */
    suspend fun update(transform: (HomeLayout) -> HomeLayout) {
        store.edit { prefs ->
            val current = HomeLayoutCodec.decode(prefs[LAYOUT]) ?: return@edit
            val next = transform(current)
            if (next != current) prefs[LAYOUT] = HomeLayoutCodec.encode(next)
        }
    }

    private companion object {
        val LAYOUT = stringPreferencesKey("home_layout")
    }
}
