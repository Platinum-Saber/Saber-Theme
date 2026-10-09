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

    private companion object {
        val LAYOUT = stringPreferencesKey("home_layout")
    }
}
