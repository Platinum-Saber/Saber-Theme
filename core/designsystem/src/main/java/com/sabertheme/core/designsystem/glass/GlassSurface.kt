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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.theme.GlassMaterial

/** Liquid-glass container. The public API stays fixed whatever engine draws it. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: GlassShape = GlassShape.Default,
    material: GlassMaterial = GlassMaterial.Regular,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .dropShadow(shape.toComposeShape(), material.shadow())
            .glassBackground(shape, material),
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
