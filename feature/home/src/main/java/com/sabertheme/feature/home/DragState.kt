package com.sabertheme.feature.home

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.glass.GlassMotion
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.model.DragSource
import com.sabertheme.core.model.DropTarget
import com.sabertheme.core.model.HomeItem
import com.sabertheme.core.model.HomeLayout
import com.sabertheme.core.model.WidgetSize
import com.sabertheme.core.ui.AppTile
import com.sabertheme.core.ui.CELL_WIDTH
import com.sabertheme.core.ui.FolderIcon
import com.sabertheme.core.ui.HomeAppIcon
import com.sabertheme.core.ui.LauncherApp
import com.sabertheme.core.ui.TILE_SIZE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the floating copy of a dragged item draws. */
internal sealed interface DragVisual {
    /** [withLabel]: a home cell (tile + label); otherwise a bare dock tile. */
    data class App(val app: LauncherApp, val withLabel: Boolean) : DragVisual

    data class Folder(val folder: HomeCell.Folder) : DragVisual

    data class Widget(val widget: HomeItem.Widget, val size: WidgetSize) : DragVisual
}

internal class ActiveDrag(
    val source: DragSource,
    /** Hidden in place while dragging, by [HomeItem.id]. */
    val itemId: String,
    val visual: DragVisual,
    val pointer: PointerId,
    /** Finger minus the visual's top-left, root px. */
    val grab: Offset,
    /** Unscaled layout size of the visual. */
    val size: IntSize,
    /** Root px per layout px where it was picked up (0.8 in edit mode). */
    val scale: Float,
    /** Visual top-left in root px at pickup. */
    val origin: Offset,
) {
    val spanX get() = (visual as? DragVisual.Widget)?.size?.spanX ?: 1
    val spanY get() = (visual as? DragVisual.Widget)?.size?.spanY ?: 1
    val isApp get() = visual is DragVisual.App
}

/** The current drop target and where it is on screen (root px). */
internal data class DropPreview(val target: DropTarget, val rect: Rect)

/**
 * Drag and drop on home. Items start a drag with [pickup]; the root
 * [tracker] then owns the pointer, moves the ghost and resolves the target
 * against the registered page, dock and remove-zone coordinates. Drops
 * commit through [onDrop] and the ghost springs into place (or back home).
 */
@Stable
internal class DragState(
    private val density: Density,
    private val scope: CoroutineScope,
    private val view: View,
) {
    var active by mutableStateOf<ActiveDrag?>(null)
        private set
    var preview by mutableStateOf<DropPreview?>(null)
        private set
    var landing by mutableStateOf(false)
        private set

    // Ghost transform; read only in the layer block.
    var ghostPos by mutableStateOf(Offset.Zero)
        private set
    var ghostScale by mutableFloatStateOf(1f)
        private set
    var ghostAlpha by mutableFloatStateOf(1f)
        private set

    var root: LayoutCoordinates? = null
    val pages = HashMap<Int, LayoutCoordinates>()
    var dock: LayoutCoordinates? = null
    var dockCount = 0
    var removeZone: LayoutCoordinates? = null

    // Wired by HomeScreen each composition.
    var cellsOf: (page: Int) -> List<PlacedCell> = { emptyList() }
    var currentPage: () -> Int = { 0 }
    var onFlip: (direction: Int) -> Unit = {}
    var onDrop: (DragSource, DropTarget) -> Boolean = { _, _ -> false }
    var reducedMotion = false

    private var flipJob: Job? = null
    private val edge = with(density) { 28.dp.toPx() }

    val hiddenId: String? get() = active?.itemId

    fun start(drag: ActiveDrag) {
        if (active != null) return
        active = drag
        preview = null
        ghostPos = drag.origin
        ghostScale = drag.scale
        ghostAlpha = 1f
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        scope.launch {
            animate(drag.scale, drag.scale * LIFT, animationSpec = GlassMotion.press(reducedMotion)) { v, _ -> if (!landing) ghostScale = v }
        }
    }

    fun move(finger: Offset) {
        val drag = active ?: return
        if (landing) return
        ghostPos = finger - drag.grab
        val next = resolve(drag, finger)
        if (next != preview) preview = next
        val width = root?.size?.width ?: return
        val side = when {
            finger.x < edge -> -1
            finger.x > width - edge -> 1
            else -> 0
        }
        if (side == 0) {
            flipJob?.cancel()
            flipJob = null
        } else if (flipJob == null) {
            flipJob = scope.launch {
                while (true) {
                    delay(FLIP_DELAY_MS)
                    onFlip(side)
                }
            }
        }
    }

    fun release() {
        val drag = active ?: return
        if (landing) return
        flipJob?.cancel()
        flipJob = null
        val target = preview
        preview = null
        landing = true
        scope.launch {
            val ok = target != null && onDrop(drag.source, target.target)
            view.performHapticFeedback(if (ok) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT)
            val (dest, scale, alpha) = if (ok && target != null) landingFor(drag, target) else Triple(drag.origin, drag.scale, 1f)
            val spec: AnimationSpec<Float> = GlassMotion.morph(reducedMotion)
            val from = ghostPos
            val fromScale = ghostScale
            val fromAlpha = ghostAlpha
            animate(0f, 1f, animationSpec = spec) { t, _ ->
                ghostPos = from + (dest - from) * t
                ghostScale = fromScale + (scale - fromScale) * t
                ghostAlpha = (fromAlpha + (alpha - fromAlpha) * t).coerceIn(0f, 1f)
            }
            active = null
            landing = false
        }
    }

    /** Drops mid-gesture (pointer cancelled, mode left): straight back home. */
    fun cancel() {
        if (active != null && !landing) {
            preview = null
            release()
        }
    }

    private fun resolve(drag: ActiveDrag, finger: Offset): DropPreview? {
        val root = root ?: return null
        removeZone?.takeIf { it.isAttached }?.let { zone ->
            val box = root.localBoundingBoxOf(zone)
            if (box.inflate(with(density) { 12.dp.toPx() }).contains(finger)) return DropPreview(DropTarget.Remove, box)
        }
        dock?.takeIf { it.isAttached }?.let { dockCoords ->
            val box = root.localBoundingBoxOf(dockCoords)
            if (box.contains(finger)) {
                if (!drag.isApp) return null
                val count = dockCount - if (drag.source is DragSource.Dock) 1 else 0
                if (count >= HomeLayout.DOCK_SIZE) return null
                val slot = box.width / (count + 1)
                val index = ((finger.x - box.left) / slot).toInt().coerceIn(0, count)
                val x = box.left + slot * index
                return DropPreview(DropTarget.Dock(index), Rect(x, box.top, x + slot, box.bottom))
            }
        }
        val page = currentPage()
        val coords = pages[page]?.takeIf { it.isAttached } ?: return null
        val grid = HomeGrid(coords.size.width.toFloat(), density)
        val local = coords.localPositionOf(root, finger)
        if (local.y > coords.size.height || local.y < -grid.rowHeight / 2) return null
        val others = cellsOf(page).filter { it.id != drag.itemId }
        if (drag.spanX == 1 && drag.spanY == 1) {
            val (col, row) = grid.cellAt(local.copy(y = local.y.coerceAtLeast(0f))) ?: return null
            val occupant = others.firstOrNull { it.covers(col, row) }
                ?: return DropPreview(DropTarget.Cell(page, col, row), coords.toRoot(root, grid.tileRect(col, row)))
            val folderable = drag.isApp && occupant.spanX == 1 && occupant.spanY == 1 && occupant.cell !is HomeCell.Widget
            if (folderable && grid.tileDistance(local, occupant.col, occupant.row) < ONTO_RADIUS) {
                return DropPreview(DropTarget.Onto(occupant.id), coords.toRoot(root, grid.tileRect(occupant.col, occupant.row)))
            }
            return null
        }
        val (col, row) = grid.spanAt(coords.localPositionOf(root, ghostPos), drag.spanX, drag.spanY)
        if (others.any { it.overlaps(col, row, drag.spanX, drag.spanY) }) return null
        return DropPreview(DropTarget.Cell(page, col, row), coords.toRoot(root, grid.widgetRect(col, row, drag.spanX, drag.spanY)))
    }

    /** Ghost top-left, scale and alpha that land it on [preview]. */
    private fun landingFor(drag: ActiveDrag, preview: DropPreview): Triple<Offset, Float, Float> {
        val rect = preview.rect
        if (drag.visual is DragVisual.Widget) return Triple(rect.topLeft, rect.width / drag.size.width, 1f)
        val tile = with(density) { TILE_SIZE.toPx() }
        val tileCenter = when (val v = drag.visual) {
            is DragVisual.App -> if (v.withLabel) labelledTileCenter() else Offset(tile / 2, tile / 2)
            else -> labelledTileCenter()
        }
        return when (preview.target) {
            is DropTarget.Cell -> {
                val scale = rect.width / tile
                Triple(rect.center - tileCenter * scale, scale, 1f)
            }
            is DropTarget.Onto -> {
                val scale = rect.width / tile * 0.4f
                Triple(rect.center - tileCenter * scale, scale, 0f)
            }
            is DropTarget.Dock -> Triple(rect.center - tileCenter, 1f, 1f)
            DropTarget.Remove -> Triple(rect.center - tileCenter * 0.2f, 0.2f, 0f)
        }
    }

    private fun labelledTileCenter() = with(density) { Offset(CELL_WIDTH.toPx() / 2, 2.dp.toPx() + TILE_SIZE.toPx() / 2) }

    private fun LayoutCoordinates.toRoot(root: LayoutCoordinates, r: Rect) =
        Rect(root.localPositionOf(this, r.topLeft), root.localPositionOf(this, r.bottomRight))

    private companion object {
        const val LIFT = 1.06f
        const val ONTO_RADIUS = 0.42f
        const val FLIP_DELAY_MS = 650L
    }
}

private fun PlacedCell.covers(c: Int, r: Int) = c in col until col + spanX && r in row until row + spanY

private fun PlacedCell.overlaps(c: Int, r: Int, sx: Int, sy: Int) =
    col < c + sx && c < col + spanX && row < r + sy && r < row + spanY

/** Per-item holder for [pickup]: bounds plus the latest callbacks (the gesture outlives recompositions). */
internal class PickupRef {
    var coords: LayoutCoordinates? = null
    var editing: () -> Boolean = { false }
    var onStart: (PointerId, Offset, Rect, IntSize) -> Unit = { _, _, _, _ -> }
}

/**
 * Starts a drag on this item. In edit mode a move past touch slop picks it
 * up; otherwise the finger must first rest for the long-press timeout (the
 * item's own long-press menu shows meanwhile and is closed by [onStart]).
 */
internal fun Modifier.pickup(
    state: DragState,
    ref: PickupRef,
    editing: () -> Boolean,
    onStart: (pointer: PointerId, finger: Offset, bounds: Rect, size: IntSize) -> Unit,
): Modifier {
    ref.editing = editing
    ref.onStart = onStart
    return onGloballyPositioned { ref.coords = it }.pointerInput(state, ref) { detectPickup(state, ref) }
}

private suspend fun PointerInputScope.detectPickup(state: DragState, ref: PickupRef) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!ref.editing()) {
            val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull Unit
                    if (!change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        return@withTimeoutOrNull Unit
                    }
                }
            }
            if (early != null) return@awaitEachGesture
        }
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!change.pressed) return@awaitEachGesture
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                val coords = ref.coords?.takeIf { it.isAttached } ?: return@awaitEachGesture
                val root = state.root ?: return@awaitEachGesture
                change.consume()
                ref.onStart(down.id, root.localPositionOf(coords, change.position), root.localBoundingBoxOf(coords), coords.size)
                return@awaitEachGesture
            }
        }
    }
}

/** On the root: owns the dragging pointer before anything else sees it. */
internal fun Modifier.dragTracker(state: DragState): Modifier =
    onGloballyPositioned { state.root = it }.pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val drag = state.active ?: continue
                val change = event.changes.firstOrNull { it.id == drag.pointer } ?: continue
                change.consume()
                if (change.pressed) state.move(change.position) else state.release()
            }
        }
    }

/** The floating copy of the dragged item and the drop-target outline. */
@Composable
internal fun DragLayer(
    state: DragState,
    widgetContent: @Composable (HomeItem.Widget, WidgetSize, Modifier) -> Unit,
) {
    val drag = state.active ?: return
    val colors = Saber.colors
    val preview = state.preview
    if (preview != null && preview.target !is DropTarget.Remove) {
        Canvas(Modifier.fillMaxSize()) {
            val r = preview.rect
            val radius = CornerRadius(if (preview.target is DropTarget.Cell && drag.visual is DragVisual.Widget) 24.dp.toPx() else 18.dp.toPx())
            val solid = preview.target is DropTarget.Onto || preview.target is DropTarget.Dock
            drawRoundRect(colors.textPrimary.copy(alpha = 0.08f), r.topLeft, r.size, radius)
            drawRoundRect(
                if (solid) colors.accent else colors.textPrimary.copy(alpha = 0.6f),
                r.topLeft,
                r.size,
                radius,
                style = Stroke(1.5.dp.toPx(), pathEffect = if (solid) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))),
            )
        }
    }
    Box(
        Modifier.layout { measurable, _ ->
            val placeable = measurable.measure(Constraints.fixed(drag.size.width, drag.size.height))
            layout(0, 0) {
                placeable.placeWithLayer(0, 0) {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = state.ghostPos.x
                    translationY = state.ghostPos.y
                    scaleX = state.ghostScale
                    scaleY = state.ghostScale
                    alpha = state.ghostAlpha
                }
            }
        },
    ) {
        when (val v = drag.visual) {
            is DragVisual.App -> if (v.withLabel) {
                HomeAppIcon(v.app, onClick = {}, onLongClick = { _, _ -> })
            } else {
                AppTile(v.app, onClick = null, onLongClick = null)
            }
            is DragVisual.Folder -> FolderIcon(v.folder.name, v.folder.apps, onOpen = {})
            is DragVisual.Widget -> widgetContent(v.widget, v.size, Modifier.fillMaxSize())
        }
    }
}
