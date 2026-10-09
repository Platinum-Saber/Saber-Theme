package com.sabertheme.core.designsystem.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.wallpaper.AuroraWallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything glass reacts to. Provided once at the activity root.
 *
 * The animated values are snapshot state that must only be read in draw or
 * graphicsLayer lambdas, so changing them never recomposes.
 */
@Stable
class GlassEnvironment {
    var backdrop by mutableStateOf<GlassBackdrop?>(null)

    /** Unit-ish direction pointing toward the virtual light, in screen space (y down). */
    var light by mutableStateOf(DEFAULT_LIGHT)

    /** Wallpaper offset in px; bounded by [GlassBackdrop.overscan]. */
    var parallax by mutableStateOf(Offset.Zero)

    /** 0..1 effect strength: user slider x power/thermal/idle policy. */
    var intensity by mutableFloatStateOf(1f)

    /** "Remove animations" is on: springs snap, no sensors. */
    var reducedMotion by mutableStateOf(false)

    companion object {
        val DEFAULT_LIGHT = Offset(-0.38f, -0.92f)
        val OVERSCAN = 28.dp
    }
}

val LocalGlassEnvironment = staticCompositionLocalOf { GlassEnvironment() }

/** Renders [wallpaper] for the current window into [environment] off the main thread. */
@Composable
fun BackdropLoader(environment: GlassEnvironment, wallpaper: AuroraWallpaper) {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    LaunchedEffect(environment, wallpaper, size, density) {
        if (size.width == 0 || size.height == 0) return@LaunchedEffect
        val overscan = (GlassEnvironment.OVERSCAN.value * density).toInt()
        environment.backdrop = withContext(Dispatchers.Default) {
            GlassBackdrop.render(wallpaper, size.width, size.height, overscan, density)
        }
    }
}

@Composable
fun rememberGlassEnvironment(): GlassEnvironment = remember { GlassEnvironment() }
