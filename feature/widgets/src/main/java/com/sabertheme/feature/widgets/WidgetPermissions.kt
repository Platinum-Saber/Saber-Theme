package com.sabertheme.feature.widgets

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Grants are re-read on [recheck] (permission result, activity resume). */
@Singleton
class WidgetPermissions @Inject constructor(@ApplicationContext private val context: Context) {
    private val generation = MutableStateFlow(0)

    fun recheck() = generation.update { it + 1 }

    fun granted(permission: WidgetPermission): Boolean = when (permission) {
        WidgetPermission.Calendar -> has(Manifest.permission.READ_CALENDAR)
        WidgetPermission.Location -> has(Manifest.permission.ACCESS_COARSE_LOCATION)
        WidgetPermission.NotificationListener ->
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }

    /** [ready] while [permission] is granted, [WidgetState.NeedsPermission] otherwise. */
    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun <T> gated(permission: WidgetPermission, ready: () -> Flow<WidgetState<T>>): Flow<WidgetState<T>> =
        generation
            .map { granted(permission) }
            .distinctUntilChanged()
            .flatMapLatest { if (it) ready() else flowOf(WidgetState.NeedsPermission(permission)) }

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/** Process-wide scope the sources share their flows in. */
@Singleton
class WidgetScope @Inject constructor() : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)
