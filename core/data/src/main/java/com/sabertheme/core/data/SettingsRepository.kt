package com.sabertheme.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sabertheme.core.model.GlassSettings
import com.sabertheme.core.model.WallpaperChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Glass and wallpaper settings, persisted so One UI killing the launcher loses nothing. */
@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {

    val settings: Flow<GlassSettings> = store.data
        .map { prefs ->
            GlassSettings(
                intensity = prefs[INTENSITY] ?: 1f,
                wallpaper = decode(prefs[WALLPAPER]),
            )
        }
        .distinctUntilChanged()

    suspend fun setIntensity(value: Float) {
        store.edit { it[INTENSITY] = value.coerceIn(0f, 1f) }
    }

    suspend fun setWallpaper(choice: WallpaperChoice) {
        store.edit { it[WALLPAPER] = encode(choice) }
    }

    internal companion object {
        val INTENSITY = floatPreferencesKey("glass_intensity")
        val WALLPAPER = stringPreferencesKey("wallpaper")

        fun encode(choice: WallpaperChoice): String = when (choice) {
            is WallpaperChoice.Bundled -> "bundled:${choice.id}"
            is WallpaperChoice.Photo -> "photo:${choice.fileName}"
        }

        fun decode(value: String?): WallpaperChoice = when {
            value == null -> WallpaperChoice.Default
            value.startsWith("bundled:") -> WallpaperChoice.Bundled(value.removePrefix("bundled:"))
            value.startsWith("photo:") -> WallpaperChoice.Photo(value.removePrefix("photo:"))
            else -> WallpaperChoice.Default
        }
    }
}
