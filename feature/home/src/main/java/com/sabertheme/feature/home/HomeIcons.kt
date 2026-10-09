package com.sabertheme.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.glass.GlassShape
import com.sabertheme.core.designsystem.glass.GlassSurface
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.Radius
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.icons.AppGlyphIcon

internal val CELL_WIDTH = 72.dp
internal val TILE_SIZE = 56.dp
internal val CELL_HEIGHT = 96.dp

/** Window bounds of a node, kept outside snapshot state (only read on tap). */
@Stable
internal class BoundsRef {
    var rect: Rect = Rect.Zero
}

@Composable
internal fun rememberBoundsRef() = remember { BoundsRef() }

internal fun Modifier.trackBounds(ref: BoundsRef) = onGloballyPositioned { ref.rect = it.boundsInWindow() }

/** Glass squircle holding one glyph (Figma IconTile, Style=Tile). */
@Composable
internal fun AppTile(
    app: HomeApp,
    modifier: Modifier = Modifier,
    size: Dp = TILE_SIZE,
    onClick: ((Rect) -> Unit)?,
    onLongClick: ((Rect, Offset) -> Unit)?,
) {
    val bounds = rememberBoundsRef()
    GlassSurface(
        modifier.size(size).trackBounds(bounds).semantics { contentDescription = app.entry.label },
        shape = GlassShape.Rounded(Radius.icon * (size / TILE_SIZE)),
        material = GlassMaterial.Thin,
        onClick = onClick?.let { { it(bounds.rect) } },
        onLongClick = onLongClick?.let { { it(bounds.rect, bounds.rect.center) } },
    ) {
        AppGlyphIcon(app.icon, Saber.colors.glyph, Modifier.align(Alignment.Center), size * (24f / 56f))
    }
}

/** Home-grid cell: tile + label (Figma AppIcon). */
@Composable
internal fun HomeAppIcon(
    app: HomeApp,
    onClick: (Rect) -> Unit,
    onLongClick: (Rect, Offset) -> Unit,
    modifier: Modifier = Modifier,
    tileModifier: Modifier = Modifier,
) {
    Column(modifier.width(CELL_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(2.dp))
        AppTile(app, tileModifier, onClick = onClick, onLongClick = onLongClick)
        Spacer(Modifier.height(6.dp))
        IconLabel(app.entry.label)
    }
}

@Composable
internal fun IconLabel(text: String) {
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
internal fun FolderIcon(
    folder: HomeCell.Folder,
    onOpen: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    tileModifier: Modifier = Modifier,
) {
    val bounds = rememberBoundsRef()
    Column(modifier.width(CELL_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(2.dp))
        GlassSurface(
            tileModifier.size(TILE_SIZE).trackBounds(bounds).semantics { contentDescription = "Folder ${folder.name}" },
            shape = GlassShape.Rounded(Radius.icon),
            material = GlassMaterial.Regular,
            onClick = { onOpen(bounds.rect) },
        ) {
            Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                folder.apps.take(4).chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { AppGlyphIcon(it.icon, Saber.colors.glyph, size = 14.dp) }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        IconLabel(folder.name)
    }
}
