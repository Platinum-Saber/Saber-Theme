package com.sabertheme.core.ui

import androidx.compose.runtime.Immutable
import com.sabertheme.core.icons.AppIconSource
import com.sabertheme.core.model.AppEntry

/**
 * An app ready to draw: its entry, resolved icon and the dominant colour of
 * its real icon ([accent], opaque ARGB; null for grey icons).
 */
@Immutable
data class LauncherApp(val entry: AppEntry, val icon: AppIconSource, val accent: Int? = null) {
    val key get() = entry.key
}
