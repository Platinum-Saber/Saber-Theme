package com.sabertheme.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sabertheme.core.model.GlassSettings
import com.sabertheme.core.model.IconStyle
import com.sabertheme.core.model.MascotOutfit
import com.sabertheme.core.model.WallpaperChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Glass, wallpaper and icon settings, persisted so One UI killing the launcher loses nothing. */
@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {

    val settings: Flow<GlassSettings> = store.data
        .map { prefs ->
            GlassSettings(
                intensity = prefs[INTENSITY] ?: 1f,
                wallpaper = decode(prefs[WALLPAPER]),
                iconStyle = decodeIconStyle(prefs[ICON_STYLE]),
                showLabels = prefs[SHOW_LABELS] ?: true,
                tiltEnabled = prefs[TILT] ?: true,
                mascotEnabled = prefs[MASCOT] ?: true,
                mascotOutfit = MascotOutfit.entries.firstOrNull { it.name == prefs[MASCOT_OUTFIT] } ?: MascotOutfit.Armor,
                mascotMessageCloud = prefs[MASCOT_MESSAGES] ?: true,
                doubleTapLock = prefs[DOUBLE_TAP_LOCK] ?: true,
            )
        }
        .distinctUntilChanged()

    suspend fun setIntensity(value: Float) {
        store.edit { it[INTENSITY] = value.coerceIn(0f, 1f) }
    }

    suspend fun setWallpaper(choice: WallpaperChoice) {
        store.edit { it[WALLPAPER] = encode(choice) }
    }

    suspend fun setIconStyle(style: IconStyle) {
        store.edit { it[ICON_STYLE] = style.name }
    }

    suspend fun setShowLabels(show: Boolean) {
        store.edit { it[SHOW_LABELS] = show }
    }

    suspend fun setTiltEnabled(enabled: Boolean) {
        store.edit { it[TILT] = enabled }
    }

    suspend fun setMascotEnabled(enabled: Boolean) {
        store.edit { it[MASCOT] = enabled }
    }

    suspend fun setMascotOutfit(outfit: MascotOutfit) {
        store.edit { it[MASCOT_OUTFIT] = outfit.name }
    }

    suspend fun setMascotMessageCloud(enabled: Boolean) {
        store.edit { it[MASCOT_MESSAGES] = enabled }
    }

    suspend fun setDoubleTapLock(enabled: Boolean) {
        store.edit { it[DOUBLE_TAP_LOCK] = enabled }
    }

    internal companion object {
        val INTENSITY = floatPreferencesKey("glass_intensity")
        val WALLPAPER = stringPreferencesKey("wallpaper")
        val ICON_STYLE = stringPreferencesKey("icon_style")
        val SHOW_LABELS = booleanPreferencesKey("show_labels")
        val TILT = booleanPreferencesKey("tilt")
        val MASCOT = booleanPreferencesKey("mascot")
        val MASCOT_OUTFIT = stringPreferencesKey("mascot_outfit")
        val MASCOT_MESSAGES = booleanPreferencesKey("mascot_messages")
        val DOUBLE_TAP_LOCK = booleanPreferencesKey("double_tap_lock")

        fun decodeIconStyle(value: String?): IconStyle = IconStyle.entries.firstOrNull { it.name == value } ?: IconStyle.Tile

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
