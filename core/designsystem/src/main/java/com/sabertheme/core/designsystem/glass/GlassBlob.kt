package com.sabertheme.core.designsystem.glass

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.SaberColors
import kotlin.math.roundToInt

/**
 * [GLASS_AGSL]'s glass for a free-form shape: the smooth union of up to
 * [GlassBlobPainter.MAX_BLOBS] circles and an optional rounded box (clouds,
 * thought bubbles). No press bloom; [alpha] fades the whole shape.
 * Coordinates are the draw scope's own; `origin` maps them to wallpaper px.
 */
private const val GLASS_BLOB_AGSL = """
uniform shader backdrop;
uniform float2 origin;
uniform float backdropScale;
uniform float4 tint;
uniform float refraction;
uniform float band;
uniform float2 light;
uniform float highlight;
uniform float4 rimColor;
uniform float4 borderColor;
uniform float borderWidth;
uniform float4 box;
uniform float boxRadius;
uniform float4 blobs[12];
uniform float blend;
uniform float alpha;

float sdRoundBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float sdShape(float2 p) {
    float d = 10000.0;
    if (box.z > 0.0) {
        d = sdRoundBox(p - box.xy, box.zw, boxRadius);
    }
    for (int i = 0; i < 12; i++) {
        if (blobs[i].z > 0.0) {
            d = smin(d, length(p - blobs[i].xy) - blobs[i].z, blend);
        }
    }
    return d;
}

half4 main(float2 coord) {
    float d = sdShape(coord);
    float coverage = clamp(0.5 - d, 0.0, 1.0);
    if (coverage <= 0.0) {
        return half4(0.0);
    }

    float2 n = float2(
        sdShape(coord + float2(0.5, 0.0)) - sdShape(coord - float2(0.5, 0.0)),
        sdShape(coord + float2(0.0, 0.5)) - sdShape(coord - float2(0.0, 0.5)));
    n = n / max(length(n), 0.0001);

    float edge = smoothstep(-band, 0.0, d);
    float2 offset = -n * edge * edge * refraction * band;
    float3 c = backdrop.eval((origin + coord + offset) * backdropScale).rgb;
    c = mix(c, tint.rgb, tint.a);

    float facing = max(dot(n, light), 0.0);
    float away = max(dot(n, -light), 0.0);
    float rim = smoothstep(-5.0, 0.0, d);
    float spec = highlight * (rim * (pow(facing, 2.0) + 0.35 * pow(away, 3.0)) + 0.18 * edge * facing);
    c = mix(c, rimColor.rgb, clamp(spec * rimColor.a, 0.0, 1.0));

    float border = smoothstep(-borderWidth - 0.5, -borderWidth + 0.5, d);
    c = mix(c, borderColor.rgb, border * borderColor.a);

    float a = coverage * alpha;
    return half4(half3(c * a), half(a));
}
"""

/**
 * Glass for things drawn outside the Compose tree (the mascot's surface):
 * the same material, tint and lit rim as `glassBackground`, on a blob shape.
 * One painter per shape; it keeps its own compiled shader.
 */
class GlassBlobPainter {
    private val shader = RuntimeShader(GLASS_BLOB_AGSL)
    private val brush = ShaderBrush(shader)
    private val uniforms = UniformWriter(shader)
    private val blobData = FloatArray(MAX_BLOBS * 4)
    private var bound: Bitmap? = null
    private var tintKey = 0L
    private var tintAlpha = 0f

    /**
     * Draws glass over [area] (this scope's coordinates) shaped as the smooth
     * union of [blobs] (x, y, radius triples, same coordinates) and an
     * optional rounded [box]. [windowOffset] is this scope's (0, 0) in the
     * window; [blend] is the smooth-union radius in px.
     */
    fun DrawScope.drawGlassBlob(
        env: GlassEnvironment,
        colors: SaberColors,
        material: GlassMaterial,
        windowOffset: Offset,
        area: Rect,
        blobs: FloatArray,
        box: Rect? = null,
        boxRadius: Float = 0f,
        blend: Float = 0f,
        alpha: Float = 1f,
    ) {
        val backdrop = env.backdrop ?: return
        if (alpha <= 0.01f || area.isEmpty) return
        val bitmap = backdrop.blurredFor(material)
        if (bitmap !== bound) {
            shader.setInputShader("backdrop", BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            })
            bound = bitmap
            tintKey = 0L
        }
        val effects = env.effects
        val intensity = env.intensity
        val parallax = env.parallax
        val light = if (effects.tilt) env.light else GlassEnvironment.DEFAULT_LIGHT
        val originX = windowOffset.x + backdrop.overscan - parallax.x
        val originY = windowOffset.y + backdrop.overscan - parallax.y

        val u = uniforms
        u.set("origin", originX, originY)
        u.set("backdropScale", backdrop.blurScale)
        val tint = if (effects.adaptiveTint) adaptiveTint(backdrop, colors, material, area.translate(Offset(originX, originY))) else material.tintAlpha(colors.isDark)
        u.setColor("tint", colors.glassTint, tint)
        u.set("refraction", if (effects.refraction) material.refraction * intensity else 0f)
        u.set("band", minOf(area.minDimension * 0.3f, 14f * density))
        u.set("light", light.x, light.y)
        u.set("highlight", material.highlight * (0.4f + 0.6f * intensity))
        u.setColor("rimColor", colors.glassHighlight, colors.glassHighlight.alpha * material.rimAlpha / 0.55f)
        u.setColor("borderColor", colors.glassBorder, colors.glassBorder.alpha)
        u.set("borderWidth", material.borderWidth.toPx())
        if (box != null) {
            shader.setFloatUniform("box", box.center.x, box.center.y, box.width / 2f, box.height / 2f)
        } else {
            shader.setFloatUniform("box", 0f, 0f, 0f, 0f)
        }
        u.set("boxRadius", boxRadius)
        blobData.fill(0f)
        for (i in 0 until minOf(blobs.size / 3, MAX_BLOBS)) {
            blobData[i * 4] = blobs[i * 3]
            blobData[i * 4 + 1] = blobs[i * 3 + 1]
            blobData[i * 4 + 2] = blobs[i * 3 + 2]
        }
        shader.setFloatUniform("blobs", blobData)
        u.set("blend", blend.coerceAtLeast(0.001f))
        u.set("alpha", alpha.coerceIn(0f, 1f))
        drawRect(brush, area.topLeft, area.size)
    }

    /** As in `glassBackground`: the lowest tint keeping primary text readable over [wallpaperArea]. */
    private fun adaptiveTint(backdrop: GlassBackdrop, colors: SaberColors, material: GlassMaterial, wallpaperArea: Rect): Float {
        val key = (wallpaperArea.left / 16f).roundToInt().toLong() shl 40 or
            ((wallpaperArea.top / 16f).roundToInt().toLong() and 0xFFFFF shl 20) or
            ((wallpaperArea.width / 16f).roundToInt().toLong() and 0x3FF shl 10) or
            ((wallpaperArea.height / 16f).roundToInt().toLong() and 0x3FF) or
            (if (colors.isDark) 1L shl 62 else 0L) or
            (material.hashCode().toLong() and 0xF shl 56)
        if (key != tintKey) {
            val sample = backdrop.palette.sample(wallpaperArea.left, wallpaperArea.top, wallpaperArea.right, wallpaperArea.bottom)
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

    companion object {
        const val MAX_BLOBS = 12
    }
}
