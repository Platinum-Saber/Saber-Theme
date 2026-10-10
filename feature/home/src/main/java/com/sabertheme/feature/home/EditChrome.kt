package com.sabertheme.feature.home

import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.sabertheme.core.designsystem.glass.GlassMotion
import com.sabertheme.core.designsystem.glass.GlassShape
import com.sabertheme.core.designsystem.glass.GlassSurface
import com.sabertheme.core.designsystem.glass.LocalGlassEnvironment
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.icons.AppGlyph
import com.sabertheme.core.icons.UiGlyph
import com.sabertheme.core.model.DropTarget
import kotlin.math.roundToInt

// Edit-mode chrome per the Figma "Edit mode" frame.

/** "Page 2 of 3" + Done; during a drag it becomes the Remove drop zone. */
@Composable
internal fun EditTopBar(page: Int, pageCount: Int, drag: DragState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Saber.colors
    Box(modifier.fillMaxWidth().height(40.dp)) {
        if (drag.active != null) {
            val hot = drag.preview?.target == DropTarget.Remove
            GlassSurface(
                Modifier
                    .align(Alignment.Center)
                    .height(40.dp)
                    .onGloballyPositioned { drag.removeZone = it },
                shape = GlassShape.Pill,
                material = if (hot) GlassMaterial.Thick else GlassMaterial.Regular,
            ) {
                Row(Modifier.padding(horizontal = 18.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(UiGlyph.TRASH.drawable, 18.dp, if (hot) colors.accent else colors.textPrimary)
                    Spacer(Modifier.width(8.dp))
                    BasicText("Remove", style = Saber.type.labelMedium.copy(color = if (hot) colors.accent else colors.textPrimary))
                }
            }
        } else {
            BasicText(
                "Page ${page + 1} of $pageCount",
                Modifier.align(Alignment.CenterStart),
                style = Saber.type.labelMedium.copy(color = colors.textSecondary),
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.accent)
                    .clickable(onClick = onDone)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                BasicText("Done", style = Saber.type.labelMedium.copy(color = Color.White))
            }
        }
    }
}

/**
 * One thumbnail per page (current one outlined in accent), then a dashed "+"
 * that adds a page. Long-press a thumbnail and drag it sideways to move that
 * page; the others slide aside to show where it will land.
 */
@Composable
internal fun PageThumbnails(
    page: Int,
    pageCount: Int,
    onSelect: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Saber.colors
    val reduced = LocalGlassEnvironment.current.reducedMotion
    val view = LocalView.current
    val pitch = with(LocalDensity.current) { (THUMB_WIDTH + THUMB_GAP).toPx() }
    val move by rememberUpdatedState(onMove)
    var lifted by remember { mutableStateOf<Int?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    fun target(from: Int) = (from + (dragX / pitch).roundToInt()).coerceIn(0, pageCount - 1)
    val landing = lifted?.let(::target)
    LaunchedEffect(landing) {
        if (landing != null && landing != lifted) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(THUMB_GAP), verticalAlignment = Alignment.CenterVertically) {
        repeat(pageCount) { i ->
            val selected = i == page
            val isLifted = i == lifted
            val from = lifted
            val slots = when {
                from == null || landing == null || isLifted -> 0
                i in (from + 1)..landing -> -1
                i in landing until from -> 1
                else -> 0
            }
            val shift by animateFloatAsState(slots * pitch, GlassMotion.morph(reduced), label = "thumbShift")
            val scale by animateFloatAsState(if (isLifted) THUMB_LIFT else 1f, GlassMotion.press(reduced), label = "thumbLift")
            val accent = selected || isLifted
            Box(
                Modifier
                    .size(THUMB_WIDTH, THUMB_HEIGHT)
                    .zIndex(if (isLifted) 1f else 0f)
                    .graphicsLayer {
                        translationX = if (isLifted) dragX.coerceIn(-i * pitch, (pageCount - 1 - i) * pitch) else shift
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.glassTint.copy(alpha = if (accent) 0.5f else 0.25f))
                    .border(if (accent) 2.dp else 1.dp, if (accent) colors.accent else colors.glassBorder, RoundedCornerShape(8.dp))
                    .semantics {
                        contentDescription = "Page ${i + 1}"
                        customActions = listOfNotNull(
                            CustomAccessibilityAction("Move page left") { move(i, i - 1); true }.takeIf { i > 0 },
                            CustomAccessibilityAction("Move page right") { move(i, i + 1); true }.takeIf { i < pageCount - 1 },
                        )
                    }
                    .pointerInput(i, pageCount) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                lifted = i
                                dragX = 0f
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            },
                            onDragEnd = {
                                val to = target(i)
                                lifted = null
                                dragX = 0f
                                if (to != i) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    move(i, to)
                                }
                            },
                            onDragCancel = {
                                lifted = null
                                dragX = 0f
                            },
                        ) { change, amount ->
                            change.consume()
                            dragX += amount.x
                        }
                    }
                    .clickable { onSelect(i) },
            )
        }
        Box(
            Modifier
                .size(THUMB_WIDTH, THUMB_HEIGHT)
                .semantics { contentDescription = "Add page" }
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    colors.glassBorder,
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
                )
            }
            Glyph(UiGlyph.PLUS.drawable, 16.dp, colors.textSecondary)
        }
    }
}

private val THUMB_WIDTH = 28.dp
private val THUMB_HEIGHT = 48.dp
private val THUMB_GAP = 10.dp
private const val THUMB_LIFT = 1.15f

/** Thick glass bar: Wallpaper, Widgets, Settings. */
@Composable
internal fun EditToolbar(onWallpaper: () -> Unit, onWidgets: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier.fillMaxWidth().height(64.dp), shape = GlassShape.Rounded(24.dp), material = GlassMaterial.Thick) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            ToolbarButton(UiGlyph.WALLPAPER.drawable, "Wallpaper", onWallpaper)
            ToolbarButton(UiGlyph.WIDGETS.drawable, "Widgets", onWidgets)
            ToolbarButton(AppGlyph.SETTINGS.drawable, "Settings", onSettings)
        }
    }
}

@Composable
private fun ToolbarButton(@DrawableRes glyph: Int, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Glyph(glyph, 22.dp, Saber.colors.glyph)
        BasicText(label, style = Saber.type.captionIcon.copy(color = Saber.colors.textPrimary))
    }
}

/** Shown in the middle of an empty page while editing. */
@Composable
internal fun RemovePageButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier.height(40.dp), shape = GlassShape.Pill, material = GlassMaterial.Regular, onClick = onClick) {
        Row(Modifier.padding(horizontal = 18.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Glyph(UiGlyph.TRASH.drawable, 18.dp, Saber.colors.textPrimary)
            Spacer(Modifier.width(8.dp))
            BasicText("Remove empty page", style = Saber.type.labelMedium.copy(color = Saber.colors.textPrimary))
        }
    }
}

@Composable
private fun Glyph(@DrawableRes res: Int, size: Dp, tint: Color) {
    Image(painterResource(res), null, Modifier.size(size), colorFilter = ColorFilter.tint(tint))
}
