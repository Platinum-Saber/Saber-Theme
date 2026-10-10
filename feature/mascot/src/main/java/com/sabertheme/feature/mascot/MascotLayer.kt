package com.sabertheme.feature.mascot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** Mascot height on Home; width follows the 100 x 140 rig box. */
internal val MASCOT_HEIGHT = 72.dp
internal val MASCOT_WIDTH = MASCOT_HEIGHT * (100f / 140f)

/**
 * Saber standing on the search bar. [anchor] is the search pill's window
 * bounds; she rests on its top edge near its right end. Fills the screen so
 * she can later be dragged anywhere, but only her own box takes touches.
 */
@Composable
fun MascotLayer(anchor: () -> Rect, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        Canvas(
            Modifier
                .offset {
                    val bar = anchor()
                    if (bar.isEmpty) return@offset IntOffset(-10_000, 0)
                    with(density) {
                        val w = MASCOT_WIDTH.toPx()
                        val h = MASCOT_HEIGHT.toPx()
                        // Boots sit a little into the bar's rim, so she stands on it.
                        IntOffset((bar.right - w - 22.dp.toPx() - origin.x).toInt(), (bar.top - h + 3.dp.toPx() - origin.y).toInt())
                    }
                }
                .size(MASCOT_WIDTH, MASCOT_HEIGHT),
        ) {
            drawSaber(Pose.Neutral, 0f)
        }
    }
}
