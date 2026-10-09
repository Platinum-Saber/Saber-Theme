package com.sabertheme.core.designsystem.glass

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Touch state shared by the press modifier (scale) and the glass shader (bloom). */
@Stable
class GlassPressState {
    /** 0 at rest, 1 fully pressed; the release spring overshoots below 0. */
    val progress = Animatable(0f)

    /** Last touch point in local px. */
    var point by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberGlassPressState() = remember { GlassPressState() }

/** Max shrink when pressed; the release overshoot grows past 1 by the same spring. */
private const val PRESS_SCALE = 0.04f

/**
 * Press compresses the glass with a spring, blooms light from the finger and
 * ticks; release springs back with a small overshoot. Scale is applied in
 * the graphics layer, so the animation never recomposes.
 */
fun Modifier.glassPress(
    state: GlassPressState,
    view: View,
    reducedMotion: () -> Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
): Modifier = this
    .graphicsLayer {
        val s = 1f - PRESS_SCALE * state.progress.value
        scaleX = s
        scaleY = s
    }
    .pointerInput(state, onClick, onLongClick) {
        coroutineScope {
            detectTapGestures(
                onPress = { at ->
                    state.point = at
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    val reduced = reducedMotion()
                    launch { state.progress.animateTo(1f, GlassMotion.press(reduced)) }
                    tryAwaitRelease()
                    launch { state.progress.animateTo(0f, GlassMotion.release(reduced)) }
                },
                onTap = onClick?.let { click ->
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        click()
                    }
                },
                onLongPress = onLongClick?.let { long ->
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        long()
                    }
                },
            )
        }
    }

/**
 * Anisotropic stretch from motion: [velocity] (px/s) widens the glass along
 * the motion and thins it across, like liquid. Read in the graphics layer.
 */
fun Modifier.glassStretch(velocity: () -> Offset): Modifier = graphicsLayer {
    val v = velocity()
    val kx = (kotlin.math.abs(v.x) / STRETCH_VELOCITY).coerceAtMost(1f) * MAX_STRETCH
    val ky = (kotlin.math.abs(v.y) / STRETCH_VELOCITY).coerceAtMost(1f) * MAX_STRETCH
    scaleX = 1f + kx - ky / 2f
    scaleY = 1f + ky - kx / 2f
}

private const val STRETCH_VELOCITY = 9000f
private const val MAX_STRETCH = 0.05f
