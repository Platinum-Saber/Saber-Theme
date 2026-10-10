package com.sabertheme.core.data

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Moves launch counts out of the settings store (which backups copy) into
 * the device-only usage store, then deletes them from settings.
 */
internal class LaunchCountsMigration(private val settings: DataStore<Preferences>) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = settings.data.first().contains(LEGACY)

    override suspend fun migrate(currentData: Preferences): Preferences {
        val legacy = settings.data.first()[LEGACY] ?: return currentData
        // Keep counts already in the usage store; merge in the old ones.
        val merged = LaunchStats.decode(legacy) + LaunchStats.decode(currentData[LaunchStats.COUNTS])
        return currentData.toMutablePreferences().apply { this[LaunchStats.COUNTS] = LaunchStats.encode(merged) }
    }

    override suspend fun cleanUp() {
        settings.edit { it.remove(LEGACY) }
    }

    companion object {
        /** Same key name, in the settings store. */
        val LEGACY = stringPreferencesKey("launch_counts")
    }
}
