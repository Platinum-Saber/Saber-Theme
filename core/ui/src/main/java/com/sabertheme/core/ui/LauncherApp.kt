package com.sabertheme.core.ui

import androidx.compose.runtime.Immutable
import com.sabertheme.core.icons.AppIconSource
import com.sabertheme.core.model.AppEntry

/** An app ready to draw: its entry plus resolved icon. */
@Immutable
data class LauncherApp(val entry: AppEntry, val icon: AppIconSource) {
    val key get() = entry.key
}
