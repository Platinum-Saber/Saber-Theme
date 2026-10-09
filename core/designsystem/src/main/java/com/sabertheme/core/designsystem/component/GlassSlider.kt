package com.sabertheme.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.theme.Saber

/**
 * 0..1 slider. While dragging it shows its own value; at rest it shows
 * [value]. [onValueChangeFinished] fires once on release, which is where
 * callers should persist.
 */
@Composable
fun GlassSlider(
    value: Float,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit = {},
    onValueChangeFinished: (Float) -> Unit,
) {
    val colors = Saber.colors
    var dragValue by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { at ->
                    val v = (at.x / size.width).coerceIn(0f, 1f)
                    change(v)
                    finish(v)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { at ->
                        dragging = true
                        dragValue = (at.x / size.width).coerceIn(0f, 1f)
                        change(dragValue)
                    },
                    onDragEnd = {
                        finish(dragValue)
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                ) { pointer, _ ->
                    dragValue = (pointer.position.x / size.width).coerceIn(0f, 1f)
                    change(dragValue)
                }
            },
    ) {
        val v = if (dragging) dragValue else latest
        val track = 6.dp.toPx()
        val y = size.height / 2f
        val r = CornerRadius(track / 2f)
        val thumb = 11.dp.toPx()
        drawRoundRect(colors.textTertiary, Offset(0f, y - track / 2f), Size(size.width, track), r)
        drawRoundRect(colors.accent, Offset(0f, y - track / 2f), Size(size.width * v, track), r)
        val x = (size.width * v).coerceIn(thumb, size.width - thumb)
        drawCircle(colors.glassHighlight.copy(alpha = 1f), thumb, Offset(x, y))
        drawCircle(colors.accent, thumb, Offset(x, y), style = Stroke(2.dp.toPx()))
    }
}
