package com.sabertheme.feature.home

import android.app.ActivityOptions
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
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
import com.sabertheme.core.model.HomeLayout
import kotlin.math.abs
import kotlin.math.roundToInt
import android.graphics.Rect as AndroidRect

private val SIDE = 18.dp
/** Wallpaper drift per page, as a share of the overscan. */
private const val PAGE_PARALLAX = 0.45f
private const val MAX_CONTENT_BLUR_DP = 18f

/**
 * Home: pages of apps and folders over the launcher's own wallpaper, with
 * page indicator, search pill and dock. Long-press empty space for the home
 * menu, an icon for its app menu.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    viewModel: HomeViewModel,
    onOpenOptions: () -> Unit,
) {
    val env = LocalGlassEnvironment.current
    val view = LocalView.current
    val pager = rememberPagerState { state.pages.size.coerceAtLeast(1) }
    val overlay = remember { Animatable(0f) }
    var openFolder by remember { mutableStateOf<OpenFolder?>(null) }
    var menu by remember { mutableStateOf<MenuRequest?>(null) }
    val pageVelocity = rememberPageVelocity(pager)

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { position ->
            val reach = (env.backdrop?.overscan ?: 0f) * PAGE_PARALLAX
            val pages = (pager.pageCount - 1).coerceAtLeast(1)
            // Centre the drift so the first and last page use opposite ends.
            env.pageParallax = Offset(reach - 2f * reach * position / pages, 0f)
        }
    }

    fun launch(app: HomeApp, bounds: Rect) {
        openFolder = null
        val r = bounds.toAndroid()
        val options = ActivityOptions.makeClipRevealAnimation(view, r.left, r.top, r.width(), r.height()).toBundle()
        viewModel.launch(app.key, r, options)
    }

    fun appMenu(app: HomeApp, bounds: Rect, at: Offset) {
        menu = MenuRequest(
            anchor = at,
            items = listOf(
                MenuItem(AppGlyph.SETTINGS.drawable, "App info") { viewModel.openAppInfo(app.key, bounds.toAndroid()) },
            ),
        )
    }

    fun homeMenu(at: Offset) {
        menu = MenuRequest(
            anchor = at,
            items = listOf(
                MenuItem(UiGlyph.EDIT.drawable, "Edit home screen", enabled = false) {},
                MenuItem(UiGlyph.WIDGETS.drawable, "Widgets", enabled = false) {},
                MenuItem(UiGlyph.WALLPAPER.drawable, "Wallpaper & style", onClick = onOpenOptions),
                MenuItem(AppGlyph.SETTINGS.drawable, "Launcher settings", onClick = onOpenOptions),
            ),
        )
    }

    Box(Modifier.fillMaxSize()) {
        WallpaperLayer()
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Live blur only while an overlay is up (cheap layer effect, not per-surface).
                    val r = overlay.value.coerceIn(0f, 1f) * MAX_CONTENT_BLUR_DP.dp.toPx()
                    renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
                }
                .pointerInput(Unit) { detectTapGestures(onLongPress = { homeMenu(it) }) }
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            HorizontalPager(pager, Modifier.weight(1f)) { index ->
                HomePage(
                    cells = state.pages.getOrElse(index) { emptyList() },
                    velocity = { pageVelocity.value },
                    onLaunch = ::launch,
                    onAppMenu = ::appMenu,
                    onOpenFolder = { folder, bounds -> openFolder = OpenFolder(folder, bounds) },
                )
            }
            PageIndicator(pager, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(14.dp))
            SearchPill(Modifier.padding(horizontal = SIDE))
            Spacer(Modifier.height(14.dp))
            Dock(state.dock, Modifier.padding(horizontal = SIDE), onLaunch = ::launch, onAppMenu = ::appMenu)
            Spacer(Modifier.height(Space.s3))
        }
        FolderOverlay(openFolder, overlay, onDismiss = { openFolder = null }, onLaunch = ::launch, onAppMenu = ::appMenu)
        GlassMenu(menu, onDismiss = { menu = null })
    }
}

@Composable
private fun HomePage(
    cells: List<HomeCell>,
    velocity: () -> Float,
    onLaunch: (HomeApp, Rect) -> Unit,
    onAppMenu: (HomeApp, Rect, Offset) -> Unit,
    onOpenFolder: (HomeCell.Folder, Rect) -> Unit,
) {
    val stretch = Modifier.glassStretch { Offset(velocity(), 0f) }
    Column(Modifier.fillMaxSize().padding(horizontal = SIDE).padding(top = Space.s2)) {
        cells.chunked(HomeLayout.COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth().height(CELL_HEIGHT), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { cell ->
                    when (cell) {
                        is HomeCell.App -> HomeAppIcon(
                            cell.app,
                            onClick = { onLaunch(cell.app, it) },
                            onLongClick = { bounds, at -> onAppMenu(cell.app, bounds, at) },
                            tileModifier = stretch,
                        )
                        is HomeCell.Folder -> FolderIcon(cell, onOpen = { onOpenFolder(cell, it) }, tileModifier = stretch)
                    }
                }
                repeat(HomeLayout.COLUMNS - row.size) { Spacer(Modifier.width(CELL_WIDTH)) }
            }
        }
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
private fun SearchPill(modifier: Modifier = Modifier) {
    val colors = Saber.colors
    GlassSurface(
        modifier.fillMaxWidth().height(48.dp),
        shape = GlassShape.Pill,
        material = GlassMaterial.Regular,
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
    apps: List<HomeApp>,
    modifier: Modifier = Modifier,
    onLaunch: (HomeApp, Rect) -> Unit,
    onAppMenu: (HomeApp, Rect, Offset) -> Unit,
) {
    GlassSurface(
        modifier.fillMaxWidth().height(80.dp),
        shape = GlassShape.Rounded(30.dp),
        material = GlassMaterial.Thick,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            horizontalArrangement = if (apps.size < HomeLayout.DOCK_SIZE) Arrangement.SpaceEvenly else Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            apps.forEach { app ->
                AppTile(app, onClick = { onLaunch(app, it) }, onLongClick = { bounds, at -> onAppMenu(app, bounds, at) })
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

private fun Rect.toAndroid() = AndroidRect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())

