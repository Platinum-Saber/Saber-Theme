package com.sabertheme.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalSaberColors = staticCompositionLocalOf { DarkSaberColors }
val LocalSaberTypography = staticCompositionLocalOf { DefaultSaberTypography }

/** Light/dark follows the wallpaper, not the system theme. */
@Composable
fun SaberTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkSaberColors else LightSaberColors
    CompositionLocalProvider(
        LocalSaberColors provides colors,
        LocalSaberTypography provides DefaultSaberTypography,
        content = content,
    )
}

object Saber {
    val colors: SaberColors
        @Composable @ReadOnlyComposable get() = LocalSaberColors.current
    val type: SaberTypography
        @Composable @ReadOnlyComposable get() = LocalSaberTypography.current
}
