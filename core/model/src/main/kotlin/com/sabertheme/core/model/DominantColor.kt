package com.sabertheme.core.model

/**
 * Picks an app icon's dominant colour for the glass tile tint. Pure Kotlin;
 * the caller renders the icon small (32 px is plenty) and passes its pixels.
 *
 * Pixels vote by hue, weighted by saturation x alpha, so white plates,
 * black outlines and anti-aliased edges don't win. The answer is the mean
 * colour of the strongest hue bin; null when the icon is essentially grey.
 */
object DominantColor {
    private const val BINS = 36
    private const val MIN_ALPHA = 0.5f
    private const val MIN_SATURATION = 0.25f
    private const val MIN_VALUE = 0.2f

    /** Share of the opaque pixels that must be colourful for a tint at all. */
    private const val MIN_COLOURFUL = 0.05f

    /** @param pixels ARGB pixels; returns an opaque ARGB colour or null. */
    fun of(pixels: IntArray): Int? {
        val weight = FloatArray(BINS)
        val r = FloatArray(BINS)
        val g = FloatArray(BINS)
        val b = FloatArray(BINS)
        var opaque = 0
        var colourful = 0
        for (p in pixels) {
            val a = (p ushr 24) / 255f
            if (a < MIN_ALPHA) continue
            opaque++
            val pr = p shr 16 and 0xFF
            val pg = p shr 8 and 0xFF
            val pb = p and 0xFF
            val max = maxOf(pr, pg, pb)
            val min = minOf(pr, pg, pb)
            val value = max / 255f
            val saturation = if (max == 0) 0f else (max - min).toFloat() / max
            if (saturation < MIN_SATURATION || value < MIN_VALUE) continue
            colourful++
            val bin = (hue(pr, pg, pb, max, min) / 360f * BINS).toInt().coerceIn(0, BINS - 1)
            val w = saturation * a
            weight[bin] += w
            r[bin] += pr * w
            g[bin] += pg * w
            b[bin] += pb * w
        }
        if (opaque == 0 || colourful < opaque * MIN_COLOURFUL) return null
        val top = weight.indices.maxBy { weight[it] }
        val w = weight[top]
        return (0xFF shl 24) or
            ((r[top] / w).toInt().coerceIn(0, 255) shl 16) or
            ((g[top] / w).toInt().coerceIn(0, 255) shl 8) or
            (b[top] / w).toInt().coerceIn(0, 255)
    }

    private fun hue(r: Int, g: Int, b: Int, max: Int, min: Int): Float {
        val d = (max - min).toFloat()
        if (d == 0f) return 0f
        val h = when (max) {
            r -> ((g - b) / d).mod(6f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        }
        return h * 60f
    }
}
