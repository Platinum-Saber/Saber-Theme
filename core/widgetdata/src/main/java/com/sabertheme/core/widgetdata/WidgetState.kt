package com.sabertheme.core.widgetdata

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** What a widget can show: its data, or why it has none yet. */
sealed interface WidgetState<out T> {
    data object Loading : WidgetState<Nothing>
    data class Ready<T>(val data: T) : WidgetState<T>
    data class NeedsPermission(val permission: WidgetPermission) : WidgetState<Nothing>
    data class Error(val message: String) : WidgetState<Nothing>
}

enum class WidgetPermission { Calendar, Location, NotificationListener, Videos }

interface WidgetDataSource<T> {
    /** Hot while collected; replays the latest state to new collectors. */
    val state: Flow<WidgetState<T>>
}

/**
 * One read for callers outside composition (exported widgets, workers): the
 * first state that is not Loading, or Loading after [timeoutMs]. Subscribing
 * starts the shared source; it stops again once nothing else collects it.
 */
suspend fun <T> WidgetDataSource<T>.snapshot(timeoutMs: Long = 20_000): WidgetState<T> =
    withTimeoutOrNull(timeoutMs) { state.first { it !is WidgetState.Loading } } ?: WidgetState.Loading
