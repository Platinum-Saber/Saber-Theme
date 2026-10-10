package com.sabertheme.feature.settings

import com.sabertheme.core.model.PhotoFraming
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

    private val _editing = MutableStateFlow<WallpaperEditRequest?>(null)

    /** The photo open in the wallpaper editor, if any. */
    val editing: StateFlow<WallpaperEditRequest?> = _editing.asStateFlow()

    suspend fun loadPhoto(fileName: String) = wallpaperStore.load(fileName)

    fun framing(fileName: String): PhotoFraming = wallpaperStore.framing(fileName)

    fun editPhoto(fileName: String) {
        _editing.value = WallpaperEditRequest(fileName, fresh = false)
    }

    /** Saves the framing and makes the photo the wallpaper. */
    fun applyEdit(framing: PhotoFraming) {
        val request = _editing.value ?: return
        _editing.value = null
        viewModelScope.launch {
            wallpaperStore.saveFraming(request.fileName, framing)
            settingsRepository.setWallpaper(WallpaperChoice.Photo(request.fileName))
        }
    }

    /** Closes the editor; a photo imported just for it is removed again. */
    fun cancelEdit() {
        val request = _editing.value ?: return
        _editing.value = null
        if (request.fresh) viewModelScope.launch { wallpaperStore.delete(request.fileName) }
    }

    fun selectWallpaper(choice: WallpaperChoice) {
        viewModelScope.launch { settingsRepository.setWallpaper(choice) }
    }

    fun importPhoto(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            try {
                // Framed in the editor first; it only becomes the wallpaper on "Set wallpaper".
                _editing.value = WallpaperEditRequest(wallpaperStore.import(uri), fresh = true)
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
