package com.sabertheme.feature.settings

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabertheme.core.data.SettingsRepository
import com.sabertheme.core.data.WallpaperStore
import com.sabertheme.core.model.GlassSettings
import com.sabertheme.core.model.IconStyle
import com.sabertheme.core.model.MascotOutfit
import com.sabertheme.core.model.WallpaperChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val wallpaperStore: WallpaperStore,
) : ViewModel() {

    val settings: StateFlow<GlassSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, GlassSettings())

    val photos: StateFlow<List<String>> = wallpaperStore.photos

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    suspend fun photoThumbnail(fileName: String, width: Int) = wallpaperStore.thumbnail(fileName, width)

    fun selectWallpaper(choice: WallpaperChoice) {
        viewModelScope.launch { settingsRepository.setWallpaper(choice) }
    }

    fun importPhoto(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            try {
                settingsRepository.setWallpaper(WallpaperChoice.Photo(wallpaperStore.import(uri)))
            } catch (e: Exception) {
                Log.w(TAG, "Wallpaper import failed", e)
            } finally {
                _importing.value = false
            }
        }
    }

    fun deletePhoto(fileName: String) {
        viewModelScope.launch {
            if (settings.value.wallpaper == WallpaperChoice.Photo(fileName)) {
                settingsRepository.setWallpaper(WallpaperChoice.Default)
            }
            wallpaperStore.delete(fileName)
        }
    }

    fun setIntensity(value: Float) {
        viewModelScope.launch { settingsRepository.setIntensity(value) }
    }

    fun setTiltEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTiltEnabled(enabled) }
    }

    fun setIconStyle(style: IconStyle) {
        viewModelScope.launch { settingsRepository.setIconStyle(style) }
    }

    fun setShowLabels(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowLabels(show) }
    }

    fun setMascotEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMascotEnabled(enabled) }
    }

    fun setMascotOutfit(outfit: MascotOutfit) {
        viewModelScope.launch { settingsRepository.setMascotOutfit(outfit) }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
