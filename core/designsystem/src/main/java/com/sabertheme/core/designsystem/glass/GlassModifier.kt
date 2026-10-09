package com.sabertheme.core.designsystem.glass

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.LocalSaberColors
import android.graphics.Bitmap

@Immutable
sealed interface GlassShape {
    fun radiusPx(width: Float, height: Float, density: Float): Float

    data class Rounded(val radius: Dp) : GlassShape {
        override fun radiusPx(width: Float, height: Float, density: Float) =
            minOf(radius.value * density, minOf(width, height) / 2f)
    }

    /** Fully rounded ends (pills and circles). */
    data object Pill : GlassShape {
        override fun radiusPx(width: Float, height: Float, density: Float) = minOf(width, height) / 2f
    }

    companion object {
        val Default = Rounded(28.dp)
    }
}

/**
 * Draws a glass background that samples the cached blurred wallpaper behind
 * this node's window position. Reads environment state only in draw.
 */
fun Modifier.glassBackground(shape: GlassShape, material: GlassMaterial): Modifier =
    this then GlassElement(shape, material)

private data class GlassElement(val shape: GlassShape, val material: GlassMaterial) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(shape, material)
    override fun update(node: GlassNode) {
        node.shape = shape
        node.material = material
        node.invalidateDraw()
    }
}

private class GlassNode(var shape: GlassShape, var material: GlassMaterial) :
    Modifier.Node(),
    DrawModifierNode,
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {

    private var windowPos = Offset.Unspecified
    private var shader: RuntimeShader? = null
    private var brush: ShaderBrush? = null
    private var boundBitmap: Bitmap? = null

    override val shouldAutoInvalidate get() = false

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val pos = coordinates.positionInWindow()
        if (pos != windowPos) {
            windowPos = pos
            invalidateDraw()
        }
    }

    override fun onDetach() {
        shader = null
        brush = null
        boundBitmap = null
    }

    override fun ContentDrawScope.draw() {
        val env = currentValueOf(LocalGlassEnvironment)
        val colors = currentValueOf(LocalSaberColors)
        val backdrop = env.backdrop
        if (backdrop == null || !windowPos.isSpecified) {
            drawContent()
            return
        }
        val rs = shader ?: RuntimeShader(GLASS_AGSL).also {
            shader = it
            brush = ShaderBrush(it)
        }
        val bitmap = backdrop.blurredFor(material)
        if (bitmap !== boundBitmap) {
            rs.setInputShader("backdrop", BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            })
            boundBitmap = bitmap
        }

        val intensity = env.intensity
        val parallax = env.parallax
        val light = env.light
        val dark = colors.isDark
        rs.setFloatUniform("size", size.width, size.height)
        rs.setFloatUniform("radius", shape.radiusPx(size.width, size.height, density))
        rs.setFloatUniform(
            "origin",
            windowPos.x + backdrop.overscan - parallax.x,
            windowPos.y + backdrop.overscan - parallax.y,
        )
        rs.setFloatUniform("backdropScale", backdrop.blurScale)
        rs.setColor("tint", colors.glassTint, material.tintAlpha(dark))
        rs.setFloatUniform("refraction", material.refraction * intensity)
        rs.setFloatUniform("light", light.x, light.y)
        rs.setFloatUniform("highlight", material.highlight * (0.4f + 0.6f * intensity))
        rs.setColor("rimColor", colors.glassHighlight, colors.glassHighlight.alpha * material.rimAlpha / 0.55f)
        rs.setColor("borderColor", colors.glassBorder, colors.glassBorder.alpha)
        rs.setFloatUniform("borderWidth", material.borderWidth.toPx())
        rs.setFloatUniform("press", 0f, 0f, 0f)
        rs.setColor("bloomColor", Color.White, 0f)

        drawRect(brush!!)
        drawContent()
    }
}

private fun RuntimeShader.setColor(name: String, color: Color, alpha: Float) =
    setFloatUniform(name, color.red, color.green, color.blue, alpha.coerceIn(0f, 1f))
