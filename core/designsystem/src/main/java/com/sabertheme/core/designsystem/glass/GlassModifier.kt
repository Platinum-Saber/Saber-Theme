package com.sabertheme.core.designsystem.glass

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.toArgb
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
import com.sabertheme.core.designsystem.theme.SaberColors
import kotlin.math.roundToInt

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
 * this node's window position. Reads environment and [press] state only in
 * draw, so tilt and touch never recompose.
 */
fun Modifier.glassBackground(shape: GlassShape, material: GlassMaterial, press: GlassPressState? = null): Modifier =
    this then GlassElement(shape, material, press)

private data class GlassElement(
    val shape: GlassShape,
    val material: GlassMaterial,
    val press: GlassPressState?,
) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(shape, material, press)
    override fun update(node: GlassNode) {
        node.shape = shape
        node.material = material
        node.press = press
        node.invalidateDraw()
    }
}

private class GlassNode(
    var shape: GlassShape,
    var material: GlassMaterial,
    var press: GlassPressState?,
) : Modifier.Node(),
    DrawModifierNode,
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {

    private var windowPos = Offset.Unspecified
    private var shader: RuntimeShader? = null
    private var brush: ShaderBrush? = null
    private var boundBitmap: Bitmap? = null

    // Adaptive tint cache: recomputed only when the sampled region changes.
    private var tintKey = 0L
    private var tintAlpha = 0f

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
            tintKey = 0L
        }

        val effects = env.effects
        val intensity = env.intensity
        val parallax = env.parallax
        val light = if (effects.tilt) env.light else GlassEnvironment.DEFAULT_LIGHT
        val originX = windowPos.x + backdrop.overscan - parallax.x
        val originY = windowPos.y + backdrop.overscan - parallax.y

        rs.setFloatUniform("size", size.width, size.height)
        rs.setFloatUniform("radius", shape.radiusPx(size.width, size.height, density))
        rs.setFloatUniform("origin", originX, originY)
        rs.setFloatUniform("backdropScale", backdrop.blurScale)
        val alpha = if (effects.adaptiveTint) adaptiveTint(backdrop, colors, originX, originY) else material.tintAlpha(colors.isDark)
        rs.setColor("tint", colors.glassTint, alpha)
        rs.setFloatUniform("refraction", if (effects.refraction) material.refraction * intensity else 0f)
        rs.setFloatUniform("light", light.x, light.y)
        rs.setFloatUniform("highlight", material.highlight * (0.4f + 0.6f * intensity))
        rs.setColor("rimColor", colors.glassHighlight, colors.glassHighlight.alpha * material.rimAlpha / 0.55f)
        rs.setColor("borderColor", colors.glassBorder, colors.glassBorder.alpha)
        rs.setFloatUniform("borderWidth", material.borderWidth.toPx())
        val p = press
        if (p != null && effects.press) {
            rs.setFloatUniform("press", p.point.x, p.point.y, p.progress.value.coerceIn(0f, 1f))
        } else {
            rs.setFloatUniform("press", 0f, 0f, 0f)
        }
        rs.setColor("bloomColor", Color.White, BLOOM_ALPHA * intensity)

        drawRect(brush!!)
        drawContent()
    }

    /** Lowest tint alpha that keeps primary text >= 4.5:1 over this surface's backdrop. */
    private fun ContentDrawScope.adaptiveTint(backdrop: GlassBackdrop, colors: SaberColors, x: Float, y: Float): Float {
        // Quantise to 16 px so pager scrolling doesn't re-solve every frame.
        val key = (x / 16f).roundToInt().toLong() shl 40 or
            ((y / 16f).roundToInt().toLong() and 0xFFFFF shl 20) or
            ((size.width / 16f).roundToInt().toLong() and 0x3FF shl 10) or
            ((size.height / 16f).roundToInt().toLong() and 0x3FF) or
            (if (colors.isDark) 1L shl 62 else 0L) or
            (material.hashCode().toLong() and 0xF shl 56)
        if (key != tintKey) {
            val sample = backdrop.palette.sample(x, y, x + size.width, y + size.height)
            tintAlpha = TintSolver.tintAlpha(
                backdrop = sample.color,
                tint = colors.glassTint.toArgb(),
                text = colors.textPrimary.copy(alpha = 1f).toArgb(),
                minAlpha = material.tintAlpha(colors.isDark),
            )
            tintKey = key
        }
        return tintAlpha
    }

    private companion object {
        const val BLOOM_ALPHA = 0.22f
    }
}

private fun RuntimeShader.setColor(name: String, color: Color, alpha: Float) =
    setFloatUniform(name, color.red, color.green, color.blue, alpha.coerceIn(0f, 1f))
