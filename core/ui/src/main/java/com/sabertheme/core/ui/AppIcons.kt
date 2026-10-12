package com.sabertheme.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.glass.GlassMotion
import com.sabertheme.core.designsystem.glass.GlassShape
import com.sabertheme.core.designsystem.glass.GlassSurface
import com.sabertheme.core.designsystem.glass.TintSolver
import com.sabertheme.core.designsystem.theme.DarkSaberColors
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.LightSaberColors
import com.sabertheme.core.designsystem.theme.Radius
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.designsystem.theme.SaberColors
import com.sabertheme.core.icons.AppGlyphIcon
import com.sabertheme.core.model.IconStyle

val CELL_WIDTH = 72.dp
val TILE_SIZE = 56.dp
val CELL_HEIGHT = 96.dp

/** Window bounds of a node, kept outside snapshot state (only read on tap). */
@Stable
class BoundsRef {
    var rect: Rect = Rect.Zero
}

@Composable
fun rememberBoundsRef() = remember { BoundsRef() }

fun Modifier.trackBounds(ref: BoundsRef) = onGloballyPositioned { ref.rect = it.boundsInWindow() }

/** User icon settings, provided once at the root. [tint]: 0..0.5 of each app's colour on its glass tile. */
@Immutable
data class IconAppearance(val style: IconStyle = IconStyle.Tile, val showLabels: Boolean = true, val tint: Float = 0f)

val LocalIconAppearance = staticCompositionLocalOf { IconAppearance() }

/**
 * One app glyph (Figma IconTile): on a glass squircle (Style=Tile) or bare
 * and a little larger (Style=Bare), per [LocalIconAppearance].
 */
@Composable
fun AppTile(
    app: LauncherApp,
    modifier: Modifier = Modifier,
    size: Dp = TILE_SIZE,
    onClick: ((Rect) -> Unit)?,
    onLongClick: ((Rect, Offset) -> Unit)?,
) {
    val bounds = rememberBoundsRef()
    val appearance = LocalIconAppearance.current
    if (appearance.style == IconStyle.Bare) {
        BareTile(app, modifier.size(size).trackBounds(bounds), size, bounds, onClick, onLongClick)
        return
    }
    val colors = Saber.colors
    val accent = app.accent?.takeIf { appearance.tint > 0f }
    val glyph = remember(accent, appearance.tint, colors) {
        accent?.let { tintedGlyph(colors, it, appearance.tint) } ?: colors.glyph
    }
    GlassSurface(
        modifier.size(size).trackBounds(bounds).semantics { contentDescription = app.entry.label },
        shape = GlassShape.Rounded(Radius.icon * (size / TILE_SIZE)),
        material = GlassMaterial.Thin,
        onClick = onClick?.let { { it(bounds.rect) } },
        onLongClick = onLongClick?.let { { it(bounds.rect, bounds.rect.center) } },
        accent = accent?.let { Color(it).copy(alpha = appearance.tint) } ?: Color.Transparent,
    ) {
        AppGlyphIcon(app.icon, glyph, Modifier.align(Alignment.Center), size * (24f / 56f))
    }
}

/**
 * Glyph colour on a tile tinted [amount] toward [accent]: the theme glyph
 * while it keeps 3:1 against the tint, else whichever of the light and dark
 * glyphs reads better.
 */
internal fun tintedGlyph(colors: SaberColors, accent: Int, amount: Float): Color {
    val tile = TintSolver.luminance(TintSolver.mix(colors.glassTint.toArgb(), accent, amount))
    fun contrast(c: Color) = TintSolver.contrast(tile, TintSolver.luminance(c.toArgb()))
    if (contrast(colors.glyph) >= MIN_GLYPH_CONTRAST) return colors.glyph
    return listOf(LightSaberColors.glyph, DarkSaberColors.glyph).maxBy(::contrast)
}

private const val MIN_GLYPH_CONTRAST = 3f

@Composable
private fun BareTile(
    app: LauncherApp,
    modifier: Modifier,
    size: Dp,
    bounds: BoundsRef,
    onClick: ((Rect) -> Unit)?,
    onLongClick: ((Rect, Offset) -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, GlassMotion.press(), label = "bare-press")
    val clicks = if (onClick == null && onLongClick == null) {
        Modifier
    } else {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onLongClick = onLongClick?.let { { it(bounds.rect, bounds.rect.center) } },
            onClick = { onClick?.invoke(bounds.rect) },
        )
    }
    Box(
        modifier
            .semantics { contentDescription = app.entry.label }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(clicks),
        contentAlignment = Alignment.Center,
    ) {
        AppGlyphIcon(app.icon, Saber.colors.glyph, size = size * (30f / 56f))
    }
}

/** Home-grid cell: tile + label (Figma AppIcon). */
@Composable
fun HomeAppIcon(
    app: LauncherApp,
    onClick: (Rect) -> Unit,
    onLongClick: (Rect, Offset) -> Unit,
    modifier: Modifier = Modifier,
    tileModifier: Modifier = Modifier,
) {
    Column(modifier.width(CELL_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(2.dp))
        AppTile(app, tileModifier, onClick = onClick, onLongClick = onLongClick)
        if (LocalIconAppearance.current.showLabels) {
            Spacer(Modifier.height(6.dp))
            IconLabel(app.entry.label)
        }
    }
}

@Composable
fun IconLabel(text: String) {
    BasicText(
        text,
        style = Saber.type.captionIcon.copy(color = Saber.colors.textPrimary, textAlign = TextAlign.Center),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(CELL_WIDTH),
    )
}

/** Closed folder: 2x2 preview of the first four glyphs on a glass tile. */
@Composable
fun FolderIcon(
    name: String,
    apps: List<LauncherApp>,
    onOpen: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    tileModifier: Modifier = Modifier,
) {
    val bounds = rememberBoundsRef()
    Column(modifier.width(CELL_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(2.dp))
        GlassSurface(
            tileModifier.size(TILE_SIZE).trackBounds(bounds).semantics { contentDescription = "Folder $name" },
            shape = GlassShape.Rounded(Radius.icon),
            material = GlassMaterial.Regular,
            onClick = { onOpen(bounds.rect) },
        ) {
            Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                apps.take(4).chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { AppGlyphIcon(it.icon, Saber.colors.glyph, size = 14.dp) }
                    }
                }
            }
        }
        if (LocalIconAppearance.current.showLabels) {
            Spacer(Modifier.height(6.dp))
            IconLabel(name)
        }
    }
}
