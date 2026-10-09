package com.sabertheme.core.designsystem.glass

import com.sabertheme.core.designsystem.glass.WallpaperPalette.Companion.relativeLuminance

/**
 * Picks the lowest glass tint alpha that keeps text at >= [MIN_CONTRAST]
 * against what is behind the glass. Models the shader exactly: the tint is
 * mixed with the backdrop per sRGB channel.
 */
object TintSolver {
    const val MIN_CONTRAST = 4.5f
    const val MAX_ALPHA = 0.9f

    /**
     * @param backdrop opaque ARGB colour behind the surface
     * @param tint opaque ARGB tint colour
     * @param text opaque ARGB text colour drawn on the glass
     * @param minAlpha the material's own tint alpha (never go below it)
     */
    fun tintAlpha(backdrop: Int, tint: Int, text: Int, minAlpha: Float): Float {
        val textL = luminance(text)
        if (contrast(luminance(mix(backdrop, tint, minAlpha)), textL) >= MIN_CONTRAST) return minAlpha
        if (contrast(luminance(mix(backdrop, tint, MAX_ALPHA)), textL) < MIN_CONTRAST) return MAX_ALPHA
        // Contrast grows monotonically with alpha here (tint is on the far side of text), so bisect.
        var lo = minAlpha
        var hi = MAX_ALPHA
        repeat(14) {
            val mid = (lo + hi) / 2f
            if (contrast(luminance(mix(backdrop, tint, mid)), textL) >= MIN_CONTRAST) hi = mid else lo = mid
        }
        return hi
    }

    fun contrast(a: Float, b: Float): Float = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)

    fun luminance(c: Int): Float = relativeLuminance(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF)

    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = ((a shr shift and 0xFF) * (1 - t) + (b shr shift and 0xFF) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
