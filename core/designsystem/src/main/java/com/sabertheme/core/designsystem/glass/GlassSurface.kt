package com.sabertheme.core.designsystem.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.theme.GlassMaterial

/**
 * Liquid-glass container. The public API stays fixed whatever engine draws it.
 * When [onClick] or [onLongClick] is set, the glass answers touch: it
 * compresses, blooms from the finger and ticks.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: GlassShape = GlassShape.Default,
    material: GlassMaterial = GlassMaterial.Regular,
    pressState: GlassPressState? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactive = onClick != null || onLongClick != null
    val press = if (interactive) pressState ?: rememberGlassPressState() else pressState
    val env = LocalGlassEnvironment.current
    val view = LocalView.current
    Box(
        modifier
            .then(
                if (interactive && press != null) {
                    Modifier.glassPress(press, view, { env.reducedMotion }, onClick, onLongClick)
                } else {
                    Modifier
                },
            )
            .dropShadow(shape.toComposeShape(), material.shadow())
            .glassBackground(shape, material, press),
        content = content,
    )
}

internal fun GlassShape.toComposeShape(): Shape = when (this) {
    is GlassShape.Rounded -> RoundedCornerShape(radius)
    GlassShape.Pill -> RoundedCornerShape(50)
}

/** Figma effect style drop shadow: y 8, blur 24, spread -4. */
private fun GlassMaterial.shadow() = Shadow(
    radius = 24.dp,
    spread = (-4).dp,
    color = Color.Black,
    offset = DpOffset(0.dp, 8.dp),
    alpha = shadowAlpha,
)
