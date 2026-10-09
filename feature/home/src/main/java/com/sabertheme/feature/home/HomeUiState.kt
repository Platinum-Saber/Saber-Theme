package com.sabertheme.feature.home

import androidx.compose.runtime.Immutable
import com.sabertheme.core.icons.AppIconSource
import com.sabertheme.core.model.AppEntry

@Immutable
data class HomeApp(val entry: AppEntry, val icon: AppIconSource) {
    val key get() = entry.key
}

@Immutable
sealed interface HomeCell {
    data class App(val app: HomeApp) : HomeCell
    data class Folder(val id: String, val name: String, val apps: List<HomeApp>) : HomeCell
}

@Immutable
data class HomeUiState(
    val dock: List<HomeApp>,
    val pages: List<List<HomeCell>>,
)
