package com.sabertheme.feature.home

import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabertheme.core.data.AppRepository
import com.sabertheme.core.data.HomeRepository
import com.sabertheme.core.data.SettingsRepository
import com.sabertheme.core.data.WallpaperStore
import com.sabertheme.core.designsystem.glass.BackdropSource
import com.sabertheme.core.designsystem.wallpaper.AuroraWallpaper
import com.sabertheme.core.icons.AppIconSource
import com.sabertheme.core.icons.IconMapper
import com.sabertheme.core.model.AppEntry
import com.sabertheme.core.model.AppKey
import com.sabertheme.core.model.GlassSettings
import com.sabertheme.core.model.HomeItem
import com.sabertheme.core.model.WallpaperChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
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
    private val appRepository: AppRepository,
    homeRepository: HomeRepository,
) : ViewModel() {

    private val iconCache = HashMap<AppKey, AppIconSource>()

    /** Null until apps and layout are loaded. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val home: StateFlow<HomeUiState?> = homeRepository
        .home { pkg -> IconMapper.glyphFor(pkg)?.key }
        .mapLatest { home ->
            suspend fun app(key: AppKey): HomeApp? {
                val entry = home.apps[key] ?: return null
                return HomeApp(entry, icon(entry))
            }
            HomeUiState(
                dock = home.layout.dock.mapNotNull { app(it) },
                pages = home.layout.pages.map { page ->
                    page.items.mapNotNull { placed ->
                        // Apps of a locked profile keep their slot but have no entry to draw yet.
                        val cell = when (val item = placed.item) {
                            is HomeItem.App -> app(item.key)?.let(HomeCell::App)
                            is HomeItem.Folder -> HomeCell.Folder(item.folderId, item.name, item.apps.mapNotNull { app(it) })
                                .takeIf { it.apps.isNotEmpty() }
                            is HomeItem.Widget -> HomeCell.Widget(item)
                        } ?: return@mapNotNull null
                        PlacedCell(cell, placed.col, placed.row, placed.spanX, placed.spanY, placed.item.id)
                    }
                },
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private suspend fun icon(entry: AppEntry): AppIconSource =
        iconCache[entry.key] ?: IconMapper.resolveSuspending(entry, appRepository).also { iconCache[entry.key] = it }

    fun launch(key: AppKey, sourceBounds: Rect?, options: Bundle?) = appRepository.launch(key, sourceBounds, options)

    fun openAppInfo(key: AppKey, sourceBounds: Rect?) = appRepository.openAppInfo(key, sourceBounds)

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

private suspend fun IconMapper.resolveSuspending(entry: AppEntry, apps: AppRepository): AppIconSource {
    glyphFor(entry.packageName)?.let { return AppIconSource.Glyph(it) }
    val mono = apps.monochromeIcon(entry.key)
    return resolve(entry.packageName, entry.label) { mono }
}
