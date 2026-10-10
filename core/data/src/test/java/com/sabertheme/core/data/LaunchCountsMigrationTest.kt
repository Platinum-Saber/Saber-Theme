package com.sabertheme.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.sabertheme.core.model.AppKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** In memory: DataStore's file storage can't replace files on Windows, where these tests run. */
private class MemoryStore(initial: Preferences) : DataStore<Preferences> {
    val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

class LaunchCountsMigrationTest {
    private val layout = stringPreferencesKey("home_layout")
    private fun key(pkg: String) = AppKey(pkg, "$pkg.Main", 0)

    @Test
    fun movesCountsOutOfSettingsAndKeepsTheRest() = runBlocking<Unit> {
        val settings = MemoryStore(
            mutablePreferencesOf(
                LaunchCountsMigration.LEGACY to LaunchStats.encode(mapOf(key("a") to 3)),
                layout to "kept",
            ),
        )
        val migration = LaunchCountsMigration(settings)

        assertThat(migration.shouldMigrate(emptyPreferences())).isTrue()
        val usage = migration.migrate(emptyPreferences())
        migration.cleanUp()

        assertThat(LaunchStats.decode(usage[LaunchStats.COUNTS])).containsExactly(key("a"), 3)
        assertThat(settings.state.value.contains(LaunchCountsMigration.LEGACY)).isFalse()
        assertThat(settings.state.value[layout]).isEqualTo("kept")
        // Done once: nothing left to move.
        assertThat(migration.shouldMigrate(usage)).isFalse()
    }

    @Test
    fun mergesWithCountsAlreadyInUsage() = runBlocking<Unit> {
        val settings = MemoryStore(
            mutablePreferencesOf(LaunchCountsMigration.LEGACY to LaunchStats.encode(mapOf(key("a") to 3, key("b") to 1))),
        )
        val existing = mutablePreferencesOf(LaunchStats.COUNTS to LaunchStats.encode(mapOf(key("b") to 5)))

        val usage = LaunchCountsMigration(settings).migrate(existing)

        assertThat(LaunchStats.decode(usage[LaunchStats.COUNTS])).containsExactly(key("a"), 3, key("b"), 5)
    }

    @Test
    fun nothingToMoveLeavesBothAlone() = runBlocking<Unit> {
        val settings = MemoryStore(mutablePreferencesOf(layout to "kept"))
        val migration = LaunchCountsMigration(settings)

        assertThat(migration.shouldMigrate(emptyPreferences())).isFalse()
        assertThat(migration.migrate(emptyPreferences()).contains(LaunchStats.COUNTS)).isFalse()
        assertThat(settings.state.value[layout]).isEqualTo("kept")
    }
}
