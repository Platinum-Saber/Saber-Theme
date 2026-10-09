package com.sabertheme.feature.home

import androidx.compose.runtime.Immutable
import com.sabertheme.core.icons.AppIconSource
import com.sabertheme.core.model.AppEntry
import com.sabertheme.core.model.HomeItem

@Immutable
data class HomeApp(val entry: AppEntry, val icon: AppIconSource) {
    val key get() = entry.key
}

@Immutable
sealed interface HomeCell {
    data class App(val app: HomeApp) : HomeCell
    data class Folder(val id: String, val name: String, val apps: List<HomeApp>) : HomeCell
    data class Widget(val widget: HomeItem.Widget) : HomeCell
}

/** [cell] at grid position ([col], [row]) spanning [spanX] x [spanY] cells. */
@Immutable
data class PlacedCell(val cell: HomeCell, val col: Int, val row: Int, val spanX: Int, val spanY: Int, val id: String)

@Immutable
data class HomeUiState(
    val dock: List<HomeApp>,
    val pages: List<List<PlacedCell>>,
)
