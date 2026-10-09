package com.sabertheme.feature.home

import android.net.Uri
import android.util.Log
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabertheme.core.data.HomeRepository
import com.sabertheme.core.data.LayoutRepository
import com.sabertheme.core.data.SettingsRepository
import com.sabertheme.core.data.WallpaperStore
import com.sabertheme.core.designsystem.glass.BackdropSource
import com.sabertheme.core.designsystem.wallpaper.AuroraWallpaper
import com.sabertheme.core.icons.IconMapper
import com.sabertheme.core.model.AppKey
import com.sabertheme.core.model.DragSource
import com.sabertheme.core.model.DropTarget
import com.sabertheme.core.model.GlassSettings
import com.sabertheme.core.model.HomeItem
import com.sabertheme.core.model.HomeLayout
import com.sabertheme.core.model.HomeLayoutPolicy
import com.sabertheme.core.model.WallpaperChoice
import com.sabertheme.core.model.WidgetSize
import com.sabertheme.core.model.WidgetType
import com.sabertheme.core.ui.AppActions
import com.sabertheme.core.ui.AppIconResolver
import com.sabertheme.core.ui.AppLauncher
import com.sabertheme.core.ui.LauncherApp
import com.sabertheme.core.ui.MenuOrigin
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val wallpaperStore: WallpaperStore,
    private val iconResolver: AppIconResolver,
    private val appLauncher: AppLauncher,
    private val appActions: AppActions,
    private val layoutRepository: LayoutRepository,
    homeRepository: HomeRepository,
) : ViewModel() {

    /**
     * Edits show at once through this override; it clears itself when the
     * saved layout catches up, so a drop never flashes back to its origin.
     */
    private val pending = MutableStateFlow<HomeLayout?>(null)

    /** The layout on screen, edits included. Main thread only. */
    private var shown: HomeLayout? = null

    /** Page the pager rests on; new widgets go there first. */
    var currentPage = 0

    /** Pages home should scroll to, e.g. where a new widget landed. */
    val focusPage = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    /** Null until apps and layout are loaded. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val home: StateFlow<HomeUiState?> = combine(homeRepository.home { pkg -> IconMapper.glyphFor(pkg)?.key }, pending) { home, edit ->
        if (edit != null && edit == home.layout) pending.value = null
        val layout = edit ?: home.layout
        shown = layout
        home.copy(layout = layout)
    }
        .mapLatest { home ->
            suspend fun app(key: AppKey): LauncherApp? {
                val entry = home.apps[key] ?: return null
                return iconResolver.app(entry)
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

    fun launch(key: AppKey, view: View, bounds: Rect) = appLauncher.launch(key, view, bounds)

    /** Applies [source] → [target]; false when the drop is not allowed. */
    fun drop(source: DragSource, target: DropTarget): Boolean =
        edit { HomeLayoutPolicy.drop(it, source, target, newFolderId = newId()) }

    fun renameFolder(folderId: String, name: String) {
        edit { HomeLayoutPolicy.renameFolder(it, folderId, name) }
    }

    /** Index of the new page. */
    fun addPage(): Int {
        edit(HomeLayoutPolicy::addPage)
        return (shown?.pages?.size ?: 1) - 1
    }

    fun removePage(index: Int) {
        edit { HomeLayoutPolicy.removePage(it, index) }
    }

    /** At the first free spot from the current page on (a new page when all are full); returns its page. */
    fun addWidget(type: WidgetType, size: WidgetSize): Int? {
        val widget = HomeItem.Widget(newId(), type)
        if (!edit { HomeLayoutPolicy.add(it, widget, size.spanX, size.spanY, fromPage = currentPage) }) return null
        return shown?.let { HomeLayoutPolicy.find(it, widget.id)?.first }?.also { focusPage.tryEmit(it) }
    }

    private fun edit(transform: (HomeLayout) -> HomeLayout): Boolean {
        val current = shown ?: return false
        val next = transform(current)
        if (next == current) return false
        shown = next
        pending.value = next
        viewModelScope.launch { layoutRepository.save(next) }
        return true
    }

    private fun newId() = UUID.randomUUID().toString().take(8)

    fun appMenu(app: LauncherApp, bounds: Rect) = appActions.menu(app.key, bounds, MenuOrigin.Home, onHome = true)

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
