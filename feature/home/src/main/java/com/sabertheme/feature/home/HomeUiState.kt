package com.sabertheme.feature.home

import androidx.compose.runtime.Immutable
import com.sabertheme.core.model.HomeItem
import com.sabertheme.core.ui.LauncherApp

@Immutable
sealed interface HomeCell {
    data class App(val app: LauncherApp) : HomeCell
    data class Folder(val id: String, val name: String, val apps: List<LauncherApp>) : HomeCell
    data class Widget(val widget: HomeItem.Widget) : HomeCell
}

/** [cell] at grid position ([col], [row]) spanning [spanX] x [spanY] cells. */
@Immutable
data class PlacedCell(val cell: HomeCell, val col: Int, val row: Int, val spanX: Int, val spanY: Int, val id: String)

@Immutable
data class HomeUiState(
    val dock: List<LauncherApp>,
    val pages: List<List<PlacedCell>>,
)
