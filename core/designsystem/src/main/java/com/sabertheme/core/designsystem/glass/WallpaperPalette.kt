package com.sabertheme.core.designsystem.glass

import kotlin.math.pow

/**
 * Coarse luminance/colour grid of the wallpaper, computed once per wallpaper.
 * [sample] answers "what is behind this rect" for the adaptive tint solver.
 */
class WallpaperPalette(
    val cols: Int,
    val rows: Int,
    /** Wallpaper size in px that the grid covers. */
    val width: Float,
    val height: Float,
    private val luminance: FloatArray,
    private val colors: IntArray,
) {
    data class Sample(val luminance: Float, val color: Int)

    fun sample(left: Float, top: Float, right: Float, bottom: Float): Sample {
        val c0 = ((left / width) * cols).toInt().coerceIn(0, cols - 1)
        val c1 = ((right / width) * cols).toInt().coerceIn(c0, cols - 1)
        val r0 = ((top / height) * rows).toInt().coerceIn(0, rows - 1)
        val r1 = ((bottom / height) * rows).toInt().coerceIn(r0, rows - 1)
        var lum = 0f
        var r = 0f
        var g = 0f
        var b = 0f
        var n = 0
        for (row in r0..r1) for (col in c0..c1) {
            val i = row * cols + col
            lum += luminance[i]
            val c = colors[i]
            r += (c shr 16 and 0xFF)
            g += (c shr 8 and 0xFF)
            b += (c and 0xFF)
            n++
        }
        val color = (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        return Sample(lum / n, color)
    }

    companion object {
        /** Builds the grid from opaque ARGB [pixels] of a [w]x[h] bitmap covering [width]x[height] wallpaper px. */
        fun from(pixels: IntArray, w: Int, h: Int, width: Float, height: Float, cols: Int = 12, rows: Int = 26): WallpaperPalette {
            val lum = FloatArray(cols * rows)
            val sumR = FloatArray(cols * rows)
            val sumG = FloatArray(cols * rows)
            val sumB = FloatArray(cols * rows)
            val count = IntArray(cols * rows)
            for (y in 0 until h) {
                val row = (y * rows / h).coerceAtMost(rows - 1)
                for (x in 0 until w) {
                    val i = row * cols + (x * cols / w).coerceAtMost(cols - 1)
                    val c = pixels[y * w + x]
                    val r = c shr 16 and 0xFF
                    val g = c shr 8 and 0xFF
                    val b = c and 0xFF
                    lum[i] += relativeLuminance(r, g, b)
                    sumR[i] += r
                    sumG[i] += g
                    sumB[i] += b
                    count[i]++
                }
            }
            val colors = IntArray(cols * rows) { i ->
                val n = count[i].coerceAtLeast(1)
                lum[i] /= n
                (0xFF shl 24) or ((sumR[i] / n).toInt() shl 16) or ((sumG[i] / n).toInt() shl 8) or (sumB[i] / n).toInt()
            }
            return WallpaperPalette(cols, rows, width, height, lum, colors)
        }

        /** WCAG relative luminance of an sRGB colour (0..255 channels). */
        fun relativeLuminance(r: Int, g: Int, b: Int): Float =
            0.2126f * linear(r) + 0.7152f * linear(g) + 0.0722f * linear(b)

        private fun linear(channel: Int): Float = LINEAR[channel]

        /** sRGB channel -> linear light, precomputed (called per pixel and per tint solve). */
        private val LINEAR = FloatArray(256) { i ->
            val c = i / 255f
            if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        }
    }
}
