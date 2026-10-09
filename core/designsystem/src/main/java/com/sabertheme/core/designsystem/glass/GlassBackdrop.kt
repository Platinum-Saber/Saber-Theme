package com.sabertheme.core.designsystem.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.wallpaper.AuroraWallpaper
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The wallpaper and its pre-blurred copies. Built once per wallpaper (and
 * window size) off the main thread; glass surfaces only sample it.
 *
 * The wallpaper is [overscan] px larger than the window on every side so
 * parallax can move it without exposing edges.
 */
@Immutable
class GlassBackdrop(
    val wallpaper: ImageBitmap,
    val overscan: Float,
    val dark: Boolean,
    /** Blurred copies keyed by [GlassMaterial.name], each [blurScale] x wallpaper size. */
    val blurred: Map<String, Bitmap>,
    val blurScale: Float,
    val palette: WallpaperPalette,
) {
    fun blurredFor(material: GlassMaterial): Bitmap = blurred[material.name] ?: blurred.values.first()

    companion object {
        private const val DOWNSAMPLE = 4

        /** Photos darker than this mean luminance get the dark (light-text) theme. */
        private const val DARK_PHOTO_LUMINANCE = 0.25f

        fun render(wallpaper: AuroraWallpaper, windowW: Int, windowH: Int, overscan: Int, density: Float): GlassBackdrop =
            render(windowW, windowH, overscan, density, darkOverride = wallpaper.dark) { canvas, w, h ->
                paintAurora(canvas, wallpaper, w, h)
            }

        /** Centre-crops [photo] to the window; light/dark comes from its luminance. */
        fun render(photo: Bitmap, windowW: Int, windowH: Int, overscan: Int, density: Float): GlassBackdrop =
            render(windowW, windowH, overscan, density, darkOverride = null) { canvas, w, h ->
                val scale = maxOf(w / photo.width, h / photo.height)
                val dw = photo.width * scale
                val dh = photo.height * scale
                val dst = android.graphics.RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
                canvas.drawBitmap(photo, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
            }

        private fun render(
            windowW: Int,
            windowH: Int,
            overscan: Int,
            density: Float,
            darkOverride: Boolean?,
            paint: (Canvas, Float, Float) -> Unit,
        ): GlassBackdrop {
            val w = windowW + 2 * overscan
            val h = windowH + 2 * overscan
            val full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            paint(Canvas(full), w.toFloat(), h.toFloat())

            val sw = (w / DOWNSAMPLE).coerceAtLeast(1)
            val sh = (h / DOWNSAMPLE).coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(full, sw, sh, true)
            val pixels = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh) }
            small.recycle()

            val blurred = GlassMaterial.presets.associate { material ->
                // Figma blur radius ~= 2 sigma; a 3-pass box of radius r has sigma ~= r.
                val sigmaPx = material.blurRadius.value * density / 2f / DOWNSAMPLE
                val out = BoxBlur.blur(pixels, sw, sh, sigmaPx.toInt().coerceAtLeast(1))
                material.name to Bitmap.createBitmap(out, sw, sh, Bitmap.Config.ARGB_8888)
            }
            val palettePixels = IntArray(sw * sh)
            blurred.getValue(GlassMaterial.Regular.name).getPixels(palettePixels, 0, sw, 0, 0, sw, sh)
            val palette = WallpaperPalette.from(palettePixels, sw, sh, w.toFloat(), h.toFloat())

            return GlassBackdrop(
                wallpaper = full.asImageBitmap(),
                overscan = overscan.toFloat(),
                dark = darkOverride ?: (palette.sample(0f, 0f, w.toFloat(), h.toFloat()).luminance < DARK_PHOTO_LUMINANCE),
                blurred = blurred,
                blurScale = sw.toFloat() / w,
                palette = palette,
            )
        }

        /** Small sharp render of a bundled wallpaper, for pickers. */
        fun auroraThumbnail(spec: AuroraWallpaper, width: Int, height: Int): Bitmap =
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                paintAurora(Canvas(it), spec, width.toFloat(), height.toFloat())
            }

        /** Draws the Figma blob recipe, scaled to cover [w]x[h]. */
        private fun paintAurora(canvas: Canvas, spec: AuroraWallpaper, w: Float, h: Float) {
            canvas.drawColor(spec.background.toArgb())
            val scale = maxOf(w / AuroraWallpaper.FRAME_W, h / AuroraWallpaper.FRAME_H)
            val dx = (w - AuroraWallpaper.FRAME_W * scale) / 2f
            val dy = (h - AuroraWallpaper.FRAME_H * scale) / 2f
            val sigma = AuroraWallpaper.BLOB_BLUR / 2f * scale
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
            for (blob in spec.blobs) {
                val cx = dx + blob.cx * scale
                val cy = dy + blob.cy * scale
                val r = blob.r * scale
                val extent = r + 3f * sigma
                val stops = FloatArray(STOPS) { it / (STOPS - 1f) }
                val argb = blob.color.toArgb() and 0x00FFFFFF
                val colors = IntArray(STOPS) { i ->
                    // A blurred disk's radial profile: Phi((r - d) / sigma).
                    val alpha = blob.alpha * phi((r - stops[i] * extent) / sigma)
                    ((alpha * 255f).toInt().coerceIn(0, 255) shl 24) or argb
                }
                paint.shader = RadialGradient(cx, cy, extent, colors, stops, Shader.TileMode.CLAMP)
                canvas.drawCircle(cx, cy, extent, paint)
            }
        }

        private const val STOPS = 16

        /** Standard normal CDF via the Abramowitz-Stegun erf approximation. */
        private fun phi(x: Float): Float = 0.5f * (1f + erf(x / sqrt(2f)))

        private fun erf(x: Float): Float {
            val t = 1f / (1f + 0.3275911f * abs(x))
            val y = 1f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t - 0.284496736f) * t + 0.254829592f) * t * exp(-x * x)
            return if (x >= 0) y else -y
        }
    }
}
