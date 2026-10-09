package com.sabertheme.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.glass.GlassMotion
import com.sabertheme.core.designsystem.glass.GlassShape
import com.sabertheme.core.designsystem.glass.GlassSurface
import com.sabertheme.core.designsystem.glass.LocalGlassEnvironment
import com.sabertheme.core.designsystem.glass.WallpaperLayer
import com.sabertheme.core.designsystem.glass.glassStretch
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.Radius
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.designsystem.theme.Space
import com.sabertheme.core.icons.AppGlyph
import com.sabertheme.core.icons.UiGlyph
import com.sabertheme.core.model.DragSource
import com.sabertheme.core.model.HomeItem
import com.sabertheme.core.model.HomeLayout
import com.sabertheme.core.model.WidgetSize
import com.sabertheme.core.ui.AppTile
import com.sabertheme.core.ui.CELL_HEIGHT
import com.sabertheme.core.ui.CELL_WIDTH
import com.sabertheme.core.ui.FolderIcon
import com.sabertheme.core.ui.GlassMenu
import com.sabertheme.core.ui.HomeAppIcon
import com.sabertheme.core.ui.LauncherApp
import com.sabertheme.core.ui.MenuItem
import com.sabertheme.core.ui.MenuRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val SIDE = 18.dp
/** Wallpaper drift per page, as a share of the overscan. */
private const val PAGE_PARALLAX = 0.45f
private const val MAX_CONTENT_BLUR_DP = 18f
private const val SWIPE_UP_DP = 48
/** Edit mode shows the page at 0.8 (Figma "Edit mode"). */
private const val EDIT_SHRINK = 0.2f

/**
 * Home: pages of widgets, apps and folders over the launcher's own wallpaper, with
 * page indicator, search pill and dock. Long-press empty space for the home
 * menu, an icon for its app menu; long-press and drag anything, or pick
 * "Edit home screen", to rearrange.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    viewModel: HomeViewModel,
    onOpenOptions: () -> Unit,
    onOpenDrawer: (withKeyboard: Boolean) -> Unit = {},
    onOpenWidgets: () -> Unit = {},
    backgroundBlur: () -> Float = { 0f },
    /** Bumped by the Home button: leaves edit mode and closes overlays. */
    resetSignal: Int = 0,
    widgetContent: @Composable (HomeItem.Widget, WidgetSize, Modifier) -> Unit = { widget, _, modifier -> WidgetPlaceholder(widget, modifier) },
) {
    val env = LocalGlassEnvironment.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { state.pages.size.coerceAtLeast(1) }
    val overlay = remember { Animatable(0f) }
    var openFolder by remember { mutableStateOf<OpenFolder?>(null) }
    var menu by remember { mutableStateOf<MenuRequest?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val edit = remember { Animatable(0f) }
    val pageVelocity = rememberPageVelocity(pager)

    val drag = remember { DragState(density, scope, view) }
    drag.reducedMotion = env.reducedMotion
    drag.cellsOf = { state.pages.getOrElse(it) { emptyList() } }
    drag.currentPage = { pager.currentPage }
    drag.dockCount = state.dock.size
    drag.onDrop = viewModel::drop
    drag.onFlip = { direction ->
        val target = pager.currentPage + direction
        if (target in 0 until pager.pageCount && !pager.isScrollInProgress) scope.launch { pager.animateScrollToPage(target) }
    }

    LaunchedEffect(editing) {
        if (!editing) drag.cancel()
        edit.animateTo(if (editing) 1f else 0f, GlassMotion.morph(env.reducedMotion))
    }
    LaunchedEffect(resetSignal) {
        if (resetSignal != 0) {
            editing = false
            openFolder = null
            menu = null
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { viewModel.currentPage = it }
    }
    LaunchedEffect(pager) {
        viewModel.focusPage.collect { page ->
            snapshotFlow { pager.pageCount }.first { it > page }
            pager.animateScrollToPage(page)
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { position ->
            val reach = (env.backdrop?.overscan ?: 0f) * PAGE_PARALLAX
            val pages = (pager.pageCount - 1).coerceAtLeast(1)
            // Centre the drift so the first and last page use opposite ends.
            env.pageParallax = Offset(reach - 2f * reach * position / pages, 0f)
        }
    }
    BackHandler(enabled = editing) { editing = false }

    fun launch(app: LauncherApp, bounds: Rect) {
        openFolder = null
        viewModel.launch(app.key, view, bounds)
    }

    fun openDrawer(withKeyboard: Boolean) {
        openFolder = null
        menu = null
        onOpenDrawer(withKeyboard)
    }

    fun appMenu(app: LauncherApp, bounds: Rect, at: Offset) {
        menu = MenuRequest(anchor = at, items = viewModel.appMenu(app, bounds))
    }

    fun homeMenu(at: Offset) {
        menu = MenuRequest(
            anchor = at,
            items = listOf(
                MenuItem(UiGlyph.EDIT.drawable, "Edit home screen") { editing = true },
                MenuItem(UiGlyph.WIDGETS.drawable, "Widgets", onClick = onOpenWidgets),
                MenuItem(UiGlyph.WALLPAPER.drawable, "Wallpaper & style", onClick = onOpenOptions),
                MenuItem(AppGlyph.SETTINGS.drawable, "Launcher settings", onClick = onOpenOptions),
            ),
        )
    }

    fun startDrag(source: DragSource, itemId: String, visual: DragVisual, pointer: PointerId, finger: Offset, bounds: Rect, size: IntSize) {
        menu = null
        openFolder = null
        editing = true
        drag.start(
            ActiveDrag(
                source = source,
                itemId = itemId,
                visual = visual,
                pointer = pointer,
                grab = finger - bounds.topLeft,
                size = size,
                scale = bounds.width / size.width,
                origin = bounds.topLeft,
            ),
        )
    }

    fun addPage() {
        val index = viewModel.addPage()
        scope.launch {
            snapshotFlow { pager.pageCount }.first { it > index }
            pager.animateScrollToPage(index)
        }
    }

    Box(Modifier.fillMaxSize().dragTracker(drag)) {
        WallpaperLayer()
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Live blur only while an overlay is up (cheap layer effect, not per-surface).
                    val r = maxOf(overlay.value, backgroundBlur()).coerceIn(0f, 1f) * MAX_CONTENT_BLUR_DP.dp.toPx()
                    renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
                }
                .pointerInput(Unit) { detectTapGestures(onLongPress = { if (!editing) homeMenu(it) }) }
                .pointerInput(Unit) {
                    // Swipe up anywhere on home opens the drawer (horizontal drags stay with the pager).
                    val trigger = SWIPE_UP_DP.dp.toPx()
                    var travel = 0f
                    var fired = false
                    detectVerticalDragGestures(onDragStart = { travel = 0f; fired = false }) { change, dy ->
                        travel += dy
                        if (!fired && !editing && drag.active == null && travel < -trigger) {
                            fired = true
                            change.consume()
                            openDrawer(false)
                        }
                    }
                }
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(
                    pager,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val s = 1f - EDIT_SHRINK * edit.value
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
                ) { index ->
                    HomePage(
                        index = index,
                        cells = state.pages.getOrElse(index) { emptyList() },
                        editing = editing,
                        editProgress = { edit.value },
                        drag = drag,
                        velocity = { pageVelocity.value },
                        widgetContent = widgetContent,
                        onLaunch = ::launch,
                        onAppMenu = ::appMenu,
                        onOpenFolder = { folder, bounds -> openFolder = OpenFolder(folder, bounds) },
                        onPickup = { placed, pointer, finger, bounds, size ->
                            val visual = when (val cell = placed.cell) {
                                is HomeCell.App -> DragVisual.App(cell.app, withLabel = true)
                                is HomeCell.Folder -> DragVisual.Folder(cell)
                                is HomeCell.Widget -> DragVisual.Widget(cell.widget, WidgetSize(placed.spanX, placed.spanY))
                            }
                            startDrag(DragSource.Page(placed.id), placed.id, visual, pointer, finger, bounds, size)
                        },
                        onRemovePage = if (state.pages.size > 1) ({ viewModel.removePage(index) }) else null,
                    )
                }
                if (editing) {
                    EditTopBar(
                        page = pager.currentPage,
                        pageCount = pager.pageCount,
                        drag = drag,
                        onDone = { editing = false },
                        modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = SIDE).graphicsLayer { alpha = edit.value },
                    )
                    PageThumbnails(
                        page = pager.currentPage,
                        pageCount = pager.pageCount,
                        onSelect = { scope.launch { pager.animateScrollToPage(it) } },
                        onAdd = ::addPage,
                        modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = edit.value },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.height(68.dp).padding(horizontal = SIDE), contentAlignment = Alignment.BottomCenter) {
                if (editing) {
                    EditToolbar(
                        onWallpaper = onOpenOptions,
                        onWidgets = onOpenWidgets,
                        onSettings = onOpenOptions,
                        modifier = Modifier.graphicsLayer { alpha = edit.value },
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PageIndicator(pager)
                        Spacer(Modifier.height(14.dp))
                        SearchPill { openDrawer(true) }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Dock(
                state.dock,
                editing = editing,
                drag = drag,
                modifier = Modifier.padding(horizontal = SIDE),
                onLaunch = ::launch,
                onAppMenu = ::appMenu,
                onPickup = { app, pointer, finger, bounds, size ->
                    startDrag(DragSource.Dock(app.key), HomeItem.App(app.key).id, DragVisual.App(app, withLabel = false), pointer, finger, bounds, size)
                },
            )
            Spacer(Modifier.height(Space.s3))
        }
        FolderOverlay(
            openFolder,
            overlay,
            editing = editing,
            drag = drag,
            onDismiss = { openFolder = null },
            onLaunch = ::launch,
            onAppMenu = ::appMenu,
            onRename = viewModel::renameFolder,
            onPickup = { folder, app, pointer, finger, bounds, size ->
                val source = DragSource.FolderApp(folder.id, app.key)
                startDrag(source, "folder:${folder.id}/${app.key.encode()}", DragVisual.App(app, withLabel = true), pointer, finger, bounds, size)
            },
        )
        GlassMenu(menu, onDismiss = { menu = null })
        DragLayer(drag, widgetContent)
    }
}

@Composable
private fun HomePage(
    index: Int,
    cells: List<PlacedCell>,
    editing: Boolean,
    editProgress: () -> Float,
    drag: DragState,
    velocity: () -> Float,
    widgetContent: @Composable (HomeItem.Widget, WidgetSize, Modifier) -> Unit,
    onLaunch: (LauncherApp, Rect) -> Unit,
    onAppMenu: (LauncherApp, Rect, Offset) -> Unit,
    onOpenFolder: (HomeCell.Folder, Rect) -> Unit,
    onPickup: (PlacedCell, PointerId, Offset, Rect, IntSize) -> Unit,
    onRemovePage: (() -> Unit)?,
) {
    val stretch = Modifier.glassStretch { Offset(velocity(), 0f) }
    val colors = Saber.colors
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                // Dashed page outline while editing (scales with the page).
                val a = editProgress()
                if (a <= 0f) return@drawBehind
                val radius = CornerRadius(28.dp.toPx())
                drawRoundRect(colors.glassTint.copy(alpha = 0.12f * a), cornerRadius = radius)
                drawRoundRect(
                    colors.glassBorder.copy(alpha = colors.glassBorder.alpha * a),
                    cornerRadius = radius,
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))),
                )
            },
    ) {
        Layout(
            content = {
                cells.forEach { placed ->
                    key(placed.id) {
                        val ref = remember { PickupRef() }
                        val item = Modifier
                            .graphicsLayer { alpha = if (drag.hiddenId == placed.id) 0f else 1f }
                            .pickup(drag, ref, { editing }) { pointer, finger, bounds, size -> onPickup(placed, pointer, finger, bounds, size) }
                        when (val cell = placed.cell) {
                            is HomeCell.App -> HomeAppIcon(
                                cell.app,
                                onClick = { if (!editing) onLaunch(cell.app, it) },
                                onLongClick = { bounds, at -> if (!editing) onAppMenu(cell.app, bounds, at) },
                                modifier = item,
                                tileModifier = stretch,
                            )
                            is HomeCell.Folder -> FolderIcon(
                                cell.name,
                                cell.apps,
                                onOpen = { onOpenFolder(cell, it) },
                                modifier = item,
                                tileModifier = stretch,
                            )
                            is HomeCell.Widget -> Box(item.consumeWhile { editing }, propagateMinConstraints = true) {
                                widgetContent(cell.widget, WidgetSize(placed.spanX, placed.spanY), stretch)
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SIDE)
                .padding(top = Space.s2)
                .onGloballyPositioned { drag.pages[index] = it },
        ) { measurables, constraints ->
            val grid = HomeGrid(constraints.maxWidth.toFloat(), this)
            val placeables = measurables.mapIndexed { i, m ->
                val c = cells[i]
                if (c.cell is HomeCell.Widget) {
                    val r = grid.widgetRect(c)
                    m.measure(Constraints.fixed(r.width.roundToInt(), r.height.roundToInt()))
                } else {
                    m.measure(Constraints(maxWidth = CELL_WIDTH.roundToPx(), maxHeight = CELL_HEIGHT.roundToPx()))
                }
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { i, p ->
                    val c = cells[i]
                    if (c.cell is HomeCell.Widget) {
                        val r = grid.widgetRect(c)
                        p.place(r.left.roundToInt(), r.top.roundToInt())
                    } else {
                        val origin = grid.cellOrigin(c.col, c.row)
                        p.place(origin.x.roundToInt(), origin.y.roundToInt())
                    }
                }
            }
        }
        if (editing && cells.isEmpty() && onRemovePage != null) {
            RemovePageButton(onRemovePage, Modifier.align(Alignment.Center))
        }
    }
}

/** Swallows touches meant for [this]'s children (e.g. a widget's own buttons) while [active]. */
private fun Modifier.consumeWhile(active: () -> Boolean) = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (active()) event.changes.forEach { it.consume() }
        }
    }
}

/** Stand-in until the widgets module provides real content. */
@Composable
private fun WidgetPlaceholder(widget: HomeItem.Widget, modifier: Modifier) {
    GlassSurface(modifier, shape = GlassShape.Rounded(Radius.lg), material = GlassMaterial.Regular) {
        BasicText(
            widget.type.name,
            Modifier.align(Alignment.Center),
            style = Saber.type.body.copy(color = Saber.colors.textSecondary),
        )
    }
}

/** Active page is an 18x6 pill, others 6x6 dots; morphs continuously while swiping. */
@Composable
private fun PageIndicator(pager: PagerState, modifier: Modifier = Modifier) {
    val colors = Saber.colors
    val count = pager.pageCount
    if (count < 2) {
        Spacer(modifier.height(6.dp))
        return
    }
    Canvas(modifier.size(width = (6 * count + 6 * (count - 1) + 12).dp, height = 6.dp)) {
        val position = pager.currentPage + pager.currentPageOffsetFraction
        val dot = 6.dp.toPx()
        val gap = 6.dp.toPx()
        val extra = 12.dp.toPx()
        var x = 0f
        for (i in 0 until count) {
            val near = (1f - abs(position - i)).coerceIn(0f, 1f)
            val w = dot + extra * near
            val color = androidx.compose.ui.graphics.lerp(colors.textTertiary, colors.textPrimary, near)
            drawRoundRect(color, Offset(x, 0f), Size(w, dot), CornerRadius(dot / 2f))
            x += w + gap
        }
    }
}

@Composable
private fun SearchPill(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = Saber.colors
    GlassSurface(
        modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = "Search apps, contacts and web" },
        shape = GlassShape.Pill,
        material = GlassMaterial.Regular,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = Space.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(UiGlyph.SEARCH.drawable), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(colors.textSecondary))
            Spacer(Modifier.width(10.dp))
            BasicText(
                "Search apps, contacts & web",
                Modifier.weight(1f),
                style = Saber.type.body.copy(color = colors.textSecondary),
                maxLines = 1,
            )
            Image(painterResource(UiGlyph.MIC.drawable), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(colors.textSecondary))
        }
    }
}

@Composable
private fun Dock(
    apps: List<LauncherApp>,
    editing: Boolean,
    drag: DragState,
    modifier: Modifier = Modifier,
    onLaunch: (LauncherApp, Rect) -> Unit,
    onAppMenu: (LauncherApp, Rect, Offset) -> Unit,
    onPickup: (LauncherApp, PointerId, Offset, Rect, IntSize) -> Unit,
) {
    GlassSurface(
        modifier.fillMaxWidth().height(80.dp).onGloballyPositioned { drag.dock = it },
        shape = GlassShape.Rounded(30.dp),
        material = GlassMaterial.Thick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            horizontalArrangement = if (apps.size < HomeLayout.DOCK_SIZE) Arrangement.SpaceEvenly else Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            apps.forEach { app ->
                key(app.key) {
                    val ref = remember { PickupRef() }
                    val id = HomeItem.App(app.key).id
                    AppTile(
                        app,
                        Modifier
                            .graphicsLayer { alpha = if (drag.hiddenId == id) 0f else 1f }
                            .pickup(drag, ref, { editing }) { pointer, finger, bounds, size -> onPickup(app, pointer, finger, bounds, size) },
                        onClick = { if (!editing) onLaunch(app, it) },
                        onLongClick = { bounds, at -> if (!editing) onAppMenu(app, bounds, at) },
                    )
                }
            }
        }
    }
}

/** Horizontal pager velocity in px/s, springing back to 0 when the pager settles. */
@Composable
private fun rememberPageVelocity(pager: PagerState): Animatable<Float, *> {
    val env = LocalGlassEnvironment.current
    val velocity = remember { Animatable(0f) }
    LaunchedEffect(pager) {
        var lastPos = Float.NaN
        var lastTime = 0L
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { pos ->
            val now = System.nanoTime()
            if (!lastPos.isNaN() && now > lastTime) {
                val pageWidth = pager.layoutInfo.viewportSize.width.toFloat()
                val v = (pos - lastPos) * pageWidth / ((now - lastTime) / 1e9f)
                velocity.snapTo(velocity.value * 0.6f + v * 0.4f)
            }
            lastPos = pos
            lastTime = now
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) velocity.animateTo(0f, GlassMotion.release(env.reducedMotion))
        }
    }
    return velocity
}
