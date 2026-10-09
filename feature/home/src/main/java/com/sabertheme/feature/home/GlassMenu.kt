package com.sabertheme.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.glass.GlassMotion
import com.sabertheme.core.designsystem.glass.GlassShape
import com.sabertheme.core.designsystem.glass.GlassSurface
import com.sabertheme.core.designsystem.glass.LocalGlassEnvironment
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.Radius
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.designsystem.theme.Space

internal data class MenuItem(val glyph: Int, val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

internal data class MenuRequest(val anchor: Offset, val items: List<MenuItem>)

/**
 * Thick-glass context menu (Figma ContextMenu, 232 wide) that morphs out of
 * [MenuRequest.anchor] and stays on screen.
 */
@Composable
internal fun GlassMenu(request: MenuRequest?, onDismiss: () -> Unit) {
    val env = LocalGlassEnvironment.current
    val progress = remember { Animatable(0f) }
    var shown by remember { mutableStateOf<MenuRequest?>(null) }
    LaunchedEffect(request) {
        if (request != null) {
            shown = request
            progress.snapTo(0f)
            progress.animateTo(1f, GlassMotion.morph(env.reducedMotion))
        } else if (shown != null) {
            progress.animateTo(0f, GlassMotion.press(env.reducedMotion))
            shown = null
        }
    }
    val current = shown ?: return
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    )
    var origin by remember { mutableStateOf(TransformOrigin.Center) }
    Layout(
        content = {
            GlassSurface(
                Modifier
                    .width(232.dp)
                    .graphicsLayer {
                        val p = progress.value
                        transformOrigin = origin
                        scaleX = 0.6f + 0.4f * p
                        scaleY = 0.6f + 0.4f * p
                        alpha = p.coerceIn(0f, 1f)
                    },
                shape = GlassShape.Rounded(Radius.md),
                material = GlassMaterial.Thick,
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    current.items.forEach { MenuRow(it, onDismiss) }
                }
            }
        },
    ) { measurables, constraints ->
        val menu = measurables.single().measure(Constraints())
        val margin = Space.s3.roundToPx()
        val x = (current.anchor.x - menu.width / 2f).toInt().coerceIn(margin, constraints.maxWidth - menu.width - margin)
        val below = current.anchor.y + 24.dp.toPx()
        val y = if (below + menu.height < constraints.maxHeight - margin) {
            below.toInt()
        } else {
            (current.anchor.y - menu.height - 24.dp.toPx()).toInt().coerceAtLeast(margin)
        }
        origin = TransformOrigin(
            ((current.anchor.x - x) / menu.width).coerceIn(0f, 1f),
            ((current.anchor.y - y) / menu.height).coerceIn(0f, 1f),
        )
        layout(constraints.maxWidth, constraints.maxHeight) { menu.place(IntOffset(x, y)) }
    }
}

@Composable
private fun MenuRow(item: MenuItem, dismiss: () -> Unit) {
    val colors = Saber.colors
    val tint = if (item.enabled) colors.textPrimary else colors.textTertiary
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (item.enabled) Modifier.clickable { dismiss(); item.onClick() } else Modifier)
            .padding(horizontal = Space.s4, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(item.glyph), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(tint))
        Spacer(Modifier.width(Space.s3))
        BasicText(item.label, Modifier.weight(1f), style = Saber.type.body.copy(color = tint))
        if (!item.enabled) BasicText("Soon", style = Saber.type.captionIcon.copy(color = colors.textTertiary))
    }
}
