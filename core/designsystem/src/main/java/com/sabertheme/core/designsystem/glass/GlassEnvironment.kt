package com.sabertheme.core.designsystem.glass

import com.sabertheme.core.model.PhotoFraming
import android.graphics.Bitmap
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

    /** Direction toward the virtual light in screen space (y down), roughly unit length. */
    var light by mutableStateOf(DEFAULT_LIGHT)

    /** Wallpaper shift from device tilt, in px. */
    var tiltParallax by mutableStateOf(Offset.Zero)

    /** Wallpaper shift from pager scroll, in px. */
    var pageParallax by mutableStateOf(Offset.Zero)

    /** Total wallpaper offset in px, clamped to the backdrop's overscan. */
    val parallax: Offset
        get() {
            val limit = backdrop?.overscan ?: 0f
            val p = tiltParallax + pageParallax
            return Offset(p.x.coerceIn(-limit, limit), p.y.coerceIn(-limit, limit))
        }

    /** 0..1 effect strength: user slider x power/thermal policy. */
    var intensity by mutableFloatStateOf(1f)

    /** "Remove animations" is on: springs snap, no sensors. */
    var reducedMotion by mutableStateOf(false)

    /** Debug switches (Glass Lab). All on in normal use. */
    var effects by mutableStateOf(GlassEffects())

    /** Set by the effects controller; glass calls it on every touch. */
    var onInteraction: () -> Unit = {}

    /** [System.nanoTime] of the last touch anywhere on Home (the mascot sleeps after a quiet spell). */
    @Volatile var lastInteractionNanos: Long = System.nanoTime()

    /** Window position of the latest pointer on Home (the mascot looks at it); main thread only. */
    var touchPosition: Offset = Offset.Unspecified

    /** A finger is on Home now. */
    var touchDown: Boolean = false

    /** [System.nanoTime] of the latest finger-down; a new value means a new touch. */
    var touchDownNanos: Long = 0L

    /** Window position of [glassInteractionTracker]'s node. */
    internal var trackerOrigin: Offset = Offset.Zero

    companion object {
        val DEFAULT_LIGHT = Offset(-0.38f, -0.92f)
        val OVERSCAN = 28.dp
    }
}

data class GlassEffects(
    val refraction: Boolean = true,
    val tilt: Boolean = true,
    val press: Boolean = true,
    val adaptiveTint: Boolean = true,
)

val LocalGlassEnvironment = staticCompositionLocalOf { GlassEnvironment() }

@Composable
fun rememberGlassEnvironment(): GlassEnvironment = remember { GlassEnvironment() }

/** What to render as the wallpaper. [key] identifies it for caching. */
sealed interface BackdropSource {
    val key: String

    data class Aurora(val spec: AuroraWallpaper) : BackdropSource {
        override val key get() = spec.id
    }

    class Photo(val fileName: String, val framing: PhotoFraming, val load: suspend () -> Bitmap?) : BackdropSource {
        override val key get() = "$fileName@${PhotoFraming.encode(framing)}"
    }
}

/** Renders [source] for the current window into [environment] off the main thread. */
@Composable
fun BackdropLoader(environment: GlassEnvironment, source: BackdropSource) {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    LaunchedEffect(environment, source.key, size, density) {
        if (size.width == 0 || size.height == 0) return@LaunchedEffect
        val overscan = (GlassEnvironment.OVERSCAN.value * density).toInt()
        environment.backdrop = withContext(Dispatchers.Default) {
            GlassPrograms.prewarm()
            val photoSource = source as? BackdropSource.Photo
            val photo = photoSource?.load?.invoke()
            if (photo != null) {
                GlassBackdrop.render(photo, photoSource.framing, size.width, size.height, overscan, density).also { photo.recycle() }
            } else {
                val spec = (source as? BackdropSource.Aurora)?.spec ?: AuroraWallpaper.Night
                GlassBackdrop.render(spec, size.width, size.height, overscan, density)
            }
        }
    }
}
