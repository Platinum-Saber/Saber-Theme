package com.sabertheme.core.ui

import androidx.compose.ui.geometry.Rect
import com.sabertheme.core.data.AppRepository
import com.sabertheme.core.data.LayoutRepository
import com.sabertheme.core.icons.AppGlyph
import com.sabertheme.core.icons.UiGlyph
import com.sabertheme.core.model.AppKey
import com.sabertheme.core.model.HomeItem
import com.sabertheme.core.model.HomeLayoutPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Where an app's long-press menu was opened, which decides Add vs Remove. */
enum class MenuOrigin { Home, Drawer }

/** The app long-press menu shared by home and drawer, and the layout edits behind it. */
@Singleton
class AppActions @Inject constructor(
    private val apps: AppRepository,
    private val layouts: LayoutRepository,
    private val launcher: AppLauncher,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Every app on home: dock, pages and folders. */
    val onHome: Flow<Set<AppKey>> = layouts.layout.map { it?.allKeys().orEmpty() }.distinctUntilChanged()

    /** First free spot from the apps page on, a new page when all are full; no-op if already on home. */
    fun addToHome(key: AppKey) {
        scope.launch { layouts.update { if (key in it.allKeys()) it else HomeLayoutPolicy.add(it, HomeItem.App(key)) } }
    }

    fun removeFromHome(key: AppKey) {
        scope.launch { layouts.update { HomeLayoutPolicy.removeApp(it, key) } }
    }

    /** Items for [key]'s menu; [onHome] is whether it is already on home. */
    fun menu(key: AppKey, bounds: Rect, origin: MenuOrigin, onHome: Boolean): List<MenuItem> = buildList {
        when (origin) {
            MenuOrigin.Home -> add(MenuItem(UiGlyph.CLOSE.drawable, "Remove from home") { removeFromHome(key) })
            MenuOrigin.Drawer -> add(
                MenuItem(UiGlyph.PLUS.drawable, "Add to home", enabled = !onHome, badge = "On home".takeIf { onHome }) { addToHome(key) },
            )
        }
        add(MenuItem(AppGlyph.SETTINGS.drawable, "App info") { launcher.openAppInfo(key, bounds) })
        if (apps.canUninstall(key)) add(MenuItem(UiGlyph.TRASH.drawable, "Uninstall") { apps.uninstall(key) })
    }
}
