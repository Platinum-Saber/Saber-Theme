package com.sabertheme.feature.home

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabertheme.core.data.SettingsRepository
import com.sabertheme.core.data.WallpaperStore
import com.sabertheme.core.designsystem.glass.BackdropSource
import com.sabertheme.core.designsystem.wallpaper.AuroraWallpaper
import com.sabertheme.core.model.GlassSettings
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
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val wallpaperStore: WallpaperStore,
) : ViewModel() {

    /** Null until DataStore has been read, so the default wallpaper never flashes. */
    val settings: StateFlow<GlassSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val photos: StateFlow<List<String>> = wallpaperStore.photos

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    fun backdropSource(choice: WallpaperChoice): BackdropSource = when (choice) {
        is WallpaperChoice.Bundled -> BackdropSource.Aurora(AuroraWallpaper.byId(choice.id))
        is WallpaperChoice.Photo -> BackdropSource.Photo(choice.fileName) { wallpaperStore.load(choice.fileName) }
    }

    suspend fun photoThumbnail(fileName: String, width: Int) = wallpaperStore.thumbnail(fileName, width)

    fun selectWallpaper(choice: WallpaperChoice) {
        viewModelScope.launch { settingsRepository.setWallpaper(choice) }
    }

    fun importPhoto(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            try {
                selectWallpaper(WallpaperChoice.Photo(wallpaperStore.import(uri)))
            } catch (e: Exception) {
                Log.w(TAG, "Wallpaper import failed", e)
            } finally {
                _importing.value = false
            }
        }
    }

    fun deletePhoto(fileName: String) {
        viewModelScope.launch {
            if (settings.value?.wallpaper == WallpaperChoice.Photo(fileName)) {
                settingsRepository.setWallpaper(WallpaperChoice.Default)
            }
            wallpaperStore.delete(fileName)
        }
    }

    fun setIntensity(value: Float) {
        viewModelScope.launch { settingsRepository.setIntensity(value) }
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
