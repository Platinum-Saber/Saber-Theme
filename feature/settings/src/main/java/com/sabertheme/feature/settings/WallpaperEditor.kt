package com.sabertheme.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.glass.GlassBackdrop
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.model.PhotoFraming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Full-screen wallpaper preview: the photo exactly as Home will show it, with
 * faint Home guides. Drag to move, pinch to zoom, double-tap to switch between
 * fill and fit; zooming out below fill shows a blurred copy behind, as Home does.
 */
@Composable
internal fun WallpaperEditor(request: WallpaperEditRequest, viewModel: SettingsViewModel) {
    val images by produceState<Pair<ImageBitmap, ImageBitmap>?>(null, request.fileName) {
        value = withContext(Dispatchers.Default) {
            val photo = viewModel.loadPhoto(request.fileName) ?: return@withContext null
            photo.asImageBitmap() to GlassBackdrop.blurredCopy(photo).asImageBitmap()
        }
    }
    var framing by remember(request.fileName) { mutableStateOf(viewModel.framing(request.fileName)) }
    var guides by remember { mutableStateOf(true) }
    BackHandler { viewModel.cancelEdit() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val sw = constraints.maxWidth.toFloat()
        val sh = constraints.maxHeight.toFloat()
        val (photo, blurred) = images ?: return@BoxWithConstraints
        val pw = photo.width.toFloat()
        val ph = photo.height.toFloat()
        fun set(f: PhotoFraming) {
            framing = f.clamp(pw, ph, sw, sh)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Wallpaper preview. Drag to move, pinch to zoom, double-tap to fit." }
                .pointerInput(pw, ph, sw, sh) {
                    detectTapGestures(onDoubleTap = {
                        val fit = PhotoFraming.minZoom(pw, ph, sw, sh)
                        set(framing.copy(zoom = if (framing.zoom > fit + 0.01f) fit else 1f))
                    })
                }
                .pointerInput(pw, ph, sw, sh) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f || pan != Offset.Zero) {
                                val scale = PhotoFraming.coverScale(pw, ph, sw, sh) * framing.zoom
                                // Panning moves the photo with the finger; the centre point moves the other way.
                                set(
                                    framing.copy(
                                        cx = framing.cx - pan.x / (pw * scale),
                                        cy = framing.cy - pan.y / (ph * scale),
                                        zoom = framing.zoom * zoom,
                                    ),
                                )
                                event.changes.forEach { if (abs(pan.x) + abs(pan.y) > 0f || zoom != 1f) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            if (framing.showsFill(pw, ph, sw, sh)) {
                val cover = PhotoFraming.coverScale(blurred.width.toFloat(), blurred.height.toFloat(), sw, sh)
                val dw = blurred.width * cover
                val dh = blurred.height * cover
                drawImage(
                    blurred,
                    dstOffset = IntOffset(((sw - dw) / 2f).toInt(), ((sh - dh) / 2f).toInt()),
                    dstSize = IntSize(dw.toInt(), dh.toInt()),
                )
                drawRect(Color.Black.copy(alpha = 70f / 255f))
            }
            val (l, t, r, b) = framing.destination(pw, ph, sw, sh).toList()
            drawImage(photo, dstOffset = IntOffset(l.toInt(), t.toInt()), dstSize = IntSize((r - l).toInt(), (b - t).toInt()))
            if (guides) drawHomeGuides(sw, sh)
        }

        // Controls.
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    zoomLabel(framing.zoom, PhotoFraming.minZoom(pw, ph, sw, sh)),
                    Modifier.weight(1f),
                    style = Saber.type.labelMedium.copy(color = Color.White),
                )
                Pill(if (guides) "Hide guides" else "Show guides") { guides = !guides }
            }
            Box(Modifier.weight(1f))
            BasicText(
                "Drag to move · pinch to zoom · double-tap to fit",
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp),
                style = Saber.type.captionIcon.copy(color = Color.White.copy(alpha = 0.85f)),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Cancel", Modifier.weight(1f)) { viewModel.cancelEdit() }
                Pill("Reset", Modifier.weight(1f)) { set(PhotoFraming()) }
                Pill("Set wallpaper", Modifier.weight(1.4f), primary = true) { viewModel.applyEdit(framing) }
            }
        }
    }
}

private fun zoomLabel(zoom: Float, fit: Float): String = when {
    zoom <= fit + 0.005f -> "Fit · whole image"
    zoom < 0.995f -> "Zoomed out"
    zoom <= 1.005f -> "Fill"
    else -> "Zoom ${"%.1f".format(zoom)}×"
}

@Composable
private fun Pill(label: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (primary) Saber.colors.accent else Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = Saber.type.labelMedium.copy(color = Color.White))
    }
}

/**
 * Faint outlines of Home's layout so the user sees what sits over the photo:
 * a 4 x 5 page grid, page dots, the search pill and the dock. Proportions
 * follow HomeScreen (18 dp side margin, 48 dp pill, 68 dp dock area).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeGuides(w: Float, h: Float) {
    val dp = density
    val line = Color.White.copy(alpha = 0.55f)
    val fill = Color.White.copy(alpha = 0.08f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(6 * dp, 5 * dp))
    val side = 18 * dp
    val dockH = 84 * dp
    val dockTop = h - 34 * dp - dockH
    val pillTop = dockTop - 14 * dp - 48 * dp
    val gridTop = 64 * dp
    val gridBottom = pillTop - 46 * dp
    val cols = 4
    val rows = 5
    val cellW = (w - 2 * side) / cols
    val cellH = (gridBottom - gridTop) / rows
    val tile = minOf(cellW, cellH) * 0.72f
    for (r in 0 until rows) for (c in 0 until cols) {
        val cx = side + cellW * (c + 0.5f)
        val cy = gridTop + cellH * (r + 0.5f) - 8 * dp
        drawRoundRect(fill, Offset(cx - tile / 2, cy - tile / 2), Size(tile, tile), CornerRadius(tile * 0.28f))
        drawRoundRect(line, Offset(cx - tile / 2, cy - tile / 2), Size(tile, tile), CornerRadius(tile * 0.28f), style = Stroke(1 * dp, pathEffect = dash))
    }
    for (i in -1..1) drawCircle(line, 3 * dp, Offset(w / 2 + i * 12 * dp, pillTop - 22 * dp))
    drawRoundRect(fill, Offset(side, pillTop), Size(w - 2 * side, 48 * dp), CornerRadius(24 * dp))
    drawRoundRect(line, Offset(side, pillTop), Size(w - 2 * side, 48 * dp), CornerRadius(24 * dp), style = Stroke(1 * dp, pathEffect = dash))
    drawRoundRect(fill, Offset(side, dockTop), Size(w - 2 * side, dockH), CornerRadius(28 * dp))
    drawRoundRect(line, Offset(side, dockTop), Size(w - 2 * side, dockH), CornerRadius(28 * dp), style = Stroke(1 * dp, pathEffect = dash))
}

/** A photo being framed; [fresh] imports are deleted again on Cancel. */
data class WallpaperEditRequest(val fileName: String, val fresh: Boolean)
