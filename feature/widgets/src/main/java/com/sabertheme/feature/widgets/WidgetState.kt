package com.sabertheme.feature.widgets

import kotlinx.coroutines.flow.Flow

/** What a widget can show: its data, or why it has none yet. */
sealed interface WidgetState<out T> {
    data object Loading : WidgetState<Nothing>
    data class Ready<T>(val data: T) : WidgetState<T>
    data class NeedsPermission(val permission: WidgetPermission) : WidgetState<Nothing>
    data class Error(val message: String) : WidgetState<Nothing>
}

enum class WidgetPermission { Calendar, Location, NotificationListener }

interface WidgetDataSource<T> {
    /** Hot while collected; replays the latest state to new collectors. */
    val state: Flow<WidgetState<T>>
}
