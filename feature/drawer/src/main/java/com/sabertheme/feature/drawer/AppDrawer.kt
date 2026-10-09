package com.sabertheme.feature.drawer

import android.Manifest
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabertheme.core.designsystem.glass.GlassMotion
import com.sabertheme.core.designsystem.glass.GlassShape
import com.sabertheme.core.designsystem.glass.GlassSurface
import com.sabertheme.core.designsystem.glass.LocalGlassEnvironment
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.Radius
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.icons.AppGlyph
import com.sabertheme.core.icons.GlyphImages
import com.sabertheme.core.icons.UiGlyph
import com.sabertheme.core.ui.GlassMenu
import com.sabertheme.core.ui.HomeAppIcon
import com.sabertheme.core.ui.LauncherApp
import com.sabertheme.core.ui.MenuRequest
import kotlinx.coroutines.launch

private val SIDE = 18.dp

/** Open/closed state of the drawer, hoisted so home can blur behind it. */
@Stable
class DrawerState {
    var visible by mutableStateOf(false)
        private set
    internal var withKeyboard by mutableStateOf(false)
    internal val progress = Animatable(0f)

    /** 0 closed .. 1 open; read it in a draw/layer phase. */
    val fraction: Float get() = progress.value

    fun open(keyboard: Boolean) {
        withKeyboard = keyboard
        visible = true
    }

    fun close() {
        visible = false
    }
}

@Composable
fun rememberDrawerState() = remember { DrawerState() }

/**
 * Full-height Thick glass sheet (Figma "App drawer"): search field, Suggested
 * row, A–Z grid with an alphabet rail; typing switches to universal search
 * (Figma "Search"). Drag the header, or pull past the top of the list, to close.
 */
@Composable
fun AppDrawer(state: DrawerState, viewModel: DrawerViewModel) {
    val env = LocalGlassEnvironment.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var pull by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.visible) {
        if (state.visible) {
            pull = 0f
            state.progress.animateTo(1f, GlassMotion.sheet(env.reducedMotion))
        } else {
            focusManager.clearFocus()
            keyboard?.hide()
            state.progress.animateTo(0f, GlassMotion.sheet(env.reducedMotion))
            viewModel.setQuery("")
            pull = 0f
        }
    }
    val shown by remember { derivedStateOf { state.visible || state.progress.value > 0f } }
    if (!shown) return

    BackHandler(enabled = state.visible) { state.close() }
    val closeDistance = with(LocalDensity.current) { 120.dp.toPx() }

    suspend fun settle(velocityY: Float) {
        if (pull > closeDistance || velocityY > 1800f) {
            state.close()
        } else {
            animate(pull, 0f, animationSpec = spring()) { value, _ -> pull = value }
        }
    }

    val pullToClose = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) keyboard?.hide()
                if (pull > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -pull)
                    pull += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pull += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull <= 0f) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }

    var menu by remember { mutableStateOf<MenuRequest?>(null) }
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val suggested by viewModel.suggested.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val focus = remember { FocusRequester() }
    val grid = rememberLazyGridState()
    val requestContacts = rememberContactsRequest(viewModel::recheckContacts)

    fun launch(app: LauncherApp, bounds: Rect) {
        viewModel.launch(app, view, bounds)
        state.close()
    }

    fun start(intent: Intent, fallback: Intent? = null) {
        if (context.startSafely(intent) || (fallback != null && context.startSafely(fallback))) state.close()
    }

    fun openTopResult() {
        val r = results ?: return
        when {
            r.apps.isNotEmpty() -> launch(r.apps.first(), Rect(0f, 0f, view.width.toFloat(), view.height.toFloat()))
            r.contacts.isNotEmpty() -> start(Intent(Intent.ACTION_VIEW, r.contacts.first().uri))
            r.settings.isNotEmpty() -> start(Intent(r.settings.first().action), Intent(Settings.ACTION_SETTINGS))
            else -> start(webSearch(r.query), webFallback(r.query))
        }
    }

    LaunchedEffect(state.visible, state.withKeyboard) {
        if (state.visible && state.withKeyboard) {
            withFrameNanos { }
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Blocks home underneath and closes on a tap above the sheet.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = state.progress.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.2f))
                .pointerInput(Unit) { detectTapGestures { state.close() } },
        )
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp)) {
            // Extends a corner radius below the screen so only the top corners show.
            val overhang = Radius.xl
            GlassSurface(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .height(maxHeight + overhang)
                    .graphicsLayer { translationY = (1f - state.progress.value) * size.height + pull },
                shape = GlassShape.Rounded(Radius.xl),
                material = GlassMaterial.Thick,
            ) {
                Column(Modifier.fillMaxSize().padding(bottom = overhang)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = { scope.launch { settle(0f) } },
                                    onDragCancel = { scope.launch { settle(0f) } },
                                ) { change, dy ->
                                    change.consume()
                                    pull = (pull + dy).coerceAtLeast(0f)
                                }
                            }
                            .padding(horizontal = SIDE)
                            .padding(top = 10.dp, bottom = 12.dp),
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(36.dp, 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Saber.colors.textTertiary),
                        )
                        Spacer(Modifier.height(14.dp))
                        SearchField(query, viewModel::setQuery, focus, onSearch = ::openTopResult)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().nestedScroll(pullToClose)) {
                        val onMenu = { app: LauncherApp, bounds: Rect, at: Offset -> menu = MenuRequest(at, viewModel.menu(app, bounds)) }
                        if (query.isBlank()) {
                            AppGrid(apps, suggested, grid, ::launch, onMenu)
                        } else {
                            results?.let { r ->
                                SearchResultsList(
                                    r,
                                    onLaunch = ::launch,
                                    onMenu = onMenu,
                                    onContact = { start(Intent(Intent.ACTION_VIEW, it.uri)) },
                                    onAllowContacts = requestContacts,
                                    onSetting = { start(Intent(it.action), Intent(Settings.ACTION_SETTINGS)) },
                                    onWeb = { start(webSearch(r.query), webFallback(r.query)) },
                                )
                            }
                        }
                    }
                }
            }
        }
        GlassMenu(menu, onDismiss = { menu = null })
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, focus: FocusRequester, onSearch: () -> Unit) {
    val colors = Saber.colors
    GlassSurface(Modifier.fillMaxWidth().height(48.dp), shape = GlassShape.Pill, material = GlassMaterial.Regular) {
        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(UiGlyph.SEARCH.drawable, 20.dp, colors.textSecondary)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    BasicText("Search apps, contacts & web", style = Saber.type.body.copy(color = colors.textSecondary), maxLines = 1)
                }
                BasicTextField(
                    query,
                    onQuery,
                    Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    textStyle = Saber.type.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(36.dp).clickable(remember { MutableInteractionSource() }, indication = null) { onQuery("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(UiGlyph.CLOSE.drawable, 18.dp, colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun AppGrid(
    apps: List<LauncherApp>,
    suggested: List<LauncherApp>,
    grid: LazyGridState,
    onLaunch: (LauncherApp, Rect) -> Unit,
    onMenu: (LauncherApp, Rect, Offset) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sections = remember(apps) { AppSearch.sections(apps.map { it.entry.label }) }
    // Suggested label + apps + divider, then the "All apps" label.
    val headers = if (suggested.isEmpty()) 1 else suggested.size + 3
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Fixed(4),
            Modifier.fillMaxSize(),
            state = grid,
            contentPadding = PaddingValues(start = SIDE, end = SIDE, bottom = bottom),
        ) {
            if (suggested.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Suggested") }
                items(suggested, key = { "s:" + it.key.encode() }) { AppCell(it, onLaunch, onMenu) }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(Saber.colors.glassBorder))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("All apps") }
            items(apps, key = { it.key.encode() }) { AppCell(it, onLaunch, onMenu) }
        }
        if (sections.size > 1) {
            AlphabetRail(sections.map { it.first }, Modifier.align(Alignment.CenterEnd)) { i ->
                scope.launch { grid.scrollToItem(headers + sections[i].second) }
            }
        }
    }
}

@Composable
private fun AppCell(app: LauncherApp, onLaunch: (LauncherApp, Rect) -> Unit, onMenu: (LauncherApp, Rect, Offset) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.TopCenter) {
        HomeAppIcon(app, onClick = { onLaunch(app, it) }, onLongClick = { bounds, at -> onMenu(app, bounds, at) })
    }
}

/** Letters down the right edge; touch or drag to jump, with a tick per letter. */
@Composable
private fun AlphabetRail(letters: List<String>, modifier: Modifier, onPick: (Int) -> Unit) {
    var active by remember { mutableIntStateOf(-1) }
    val haptics = LocalHapticFeedback.current
    val pick by rememberUpdatedState(onPick)
    Column(
        modifier
            .width(SIDE)
            .pointerInput(letters) {
                fun select(y: Float) {
                    val i = (y / size.height * letters.size).toInt().coerceIn(0, letters.lastIndex)
                    if (i != active) {
                        active = i
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        pick(i)
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    select(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        change.consume()
                        if (!change.pressed) break
                        select(change.position.y)
                    }
                    active = -1
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        letters.forEachIndexed { i, letter ->
            val color = if (i == active) Saber.colors.accent else Saber.colors.textTertiary
            BasicText(letter, style = Saber.type.captionIcon.copy(color = color))
        }
    }
}

@Composable
private fun SearchResultsList(
    results: SearchResults,
    onLaunch: (LauncherApp, Rect) -> Unit,
    onMenu: (LauncherApp, Rect, Offset) -> Unit,
    onContact: (ContactResult) -> Unit,
    onAllowContacts: () -> Unit,
    onSetting: (SettingShortcut) -> Unit,
    onWeb: () -> Unit,
) {
    val colors = Saber.colors
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = SIDE, end = SIDE, bottom = bottom),
    ) {
        if (results.apps.isNotEmpty()) {
            item { SectionLabel("Apps") }
            items(results.apps.chunked(4)) { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    row.forEach { app ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            HomeAppIcon(app, onClick = { onLaunch(app, it) }, onLongClick = { bounds, at -> onMenu(app, bounds, at) })
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (results.contacts.isNotEmpty() || results.contactsNeedPermission) {
            item { SectionLabel("Contacts") }
            if (results.contactsNeedPermission) {
                item {
                    ResultRow("Search contacts", "Allow access to find people", onAllowContacts) {
                        LeadTile { Glyph(UiGlyph.SEARCH.drawable, 20.dp, colors.glyph) }
                    }
                }
            }
            items(results.contacts, key = { "c:${it.id}" }) { contact ->
                ResultRow(contact.name, contact.phone, { onContact(contact) }) { Avatar(contact.name) }
            }
        }
        if (results.settings.isNotEmpty()) {
            item { SectionLabel("Settings") }
            items(results.settings, key = { "s:${it.action}" }) { setting ->
                ResultRow(setting.title, "Settings › ${setting.section}", { onSetting(setting) }) {
                    LeadTile { Glyph(AppGlyph.SETTINGS.drawable, 20.dp, colors.glyph) }
                }
            }
        }
        item { SectionLabel("Web") }
        item {
            ResultRow(results.query, "Search the web", onWeb) {
                LeadTile { Glyph(UiGlyph.SEARCH.drawable, 20.dp, colors.textSecondary) }
            }
        }
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String?, onClick: () -> Unit, lead: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        lead()
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Saber.type.body.copy(color = Saber.colors.textPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) {
                BasicText(subtitle, style = Saber.type.captionIcon.copy(color = Saber.colors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LeadTile(content: @Composable () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Saber.colors.textPrimary.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Avatar(name: String) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(Saber.colors.accent), contentAlignment = Alignment.Center) {
        BasicText(name.trim().take(1).uppercase(), style = Saber.type.titleMedium.copy(color = Color.White))
    }
}

@Composable
private fun SectionLabel(text: String) {
    BasicText(
        text,
        Modifier.padding(top = 4.dp, bottom = 8.dp),
        style = Saber.type.labelMedium.copy(color = Saber.colors.textSecondary),
    )
}

@Composable
private fun Glyph(@DrawableRes res: Int, size: Dp, tint: Color) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val image = remember(res, px) { GlyphImages.get(context, res, px) }
    val filter = remember(tint) { ColorFilter.tint(tint) }
    Spacer(Modifier.size(size).drawBehind { drawImage(image, colorFilter = filter) })
}

/** READ_CONTACTS from the inline row; once the system stops asking, opens app info. */
@Composable
private fun rememberContactsRequest(onResult: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onResult()
        if (!granted && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CONTACTS)) {
            context.startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }
    }
    return remember(launcher) { { launcher.launch(Manifest.permission.READ_CONTACTS) } }
}

private fun webSearch(query: String) = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)

private fun webFallback(query: String) = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))

private fun Context.startSafely(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
}
