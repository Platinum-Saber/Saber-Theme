package com.sabertheme.core.model

import kotlin.math.max
import kotlin.math.min

/**
 * How a photo wallpaper sits on the screen. [cx], [cy] (0..1) are the photo
 * point shown at the screen centre; [zoom] is relative to the cover scale:
 * 1 fills the screen, below 1 shows more of the photo (down to [minZoom],
 * the whole photo) with a blurred copy behind, above 1 zooms in.
 */
data class PhotoFraming(val cx: Float = 0.5f, val cy: Float = 0.5f, val zoom: Float = 1f) {

    /** Destination rect of the whole photo on a [screenW] x [screenH] screen: left, top, right, bottom. */
    fun destination(photoW: Float, photoH: Float, screenW: Float, screenH: Float): FloatArray {
        val scale = coverScale(photoW, photoH, screenW, screenH) * zoom
        val left = screenW / 2f - cx * photoW * scale
        val top = screenH / 2f - cy * photoH * scale
        return floatArrayOf(left, top, left + photoW * scale, top + photoH * scale)
    }

    /** Keeps zoom in range and the photo covering as much of the screen as it can. */
    fun clamp(photoW: Float, photoH: Float, screenW: Float, screenH: Float): PhotoFraming {
        val z = zoom.coerceIn(minZoom(photoW, photoH, screenW, screenH), MAX_ZOOM)
        val scale = coverScale(photoW, photoH, screenW, screenH) * z
        fun axis(c: Float, photo: Float, screen: Float): Float {
            val half = screen / 2f / (photo * scale)
            // Larger than the screen on this axis: keep its edges off-screen.
            // Smaller (zoomed out): it may slide anywhere it stays fully visible.
            return if (half <= 0.5f) c.coerceIn(half, 1f - half) else c.coerceIn(1f - half, half)
        }
        return PhotoFraming(axis(cx, photoW, screenW), axis(cy, photoH, screenH), z)
    }

    /** True when part of the screen shows the blurred fill instead of the photo. */
    fun showsFill(photoW: Float, photoH: Float, screenW: Float, screenH: Float): Boolean = zoom < 0.999f

    companion object {
        const val MAX_ZOOM = 4f

        fun coverScale(photoW: Float, photoH: Float, screenW: Float, screenH: Float) = max(screenW / photoW, screenH / photoH)

        /** Zoom at which the whole photo fits on screen. */
        fun minZoom(photoW: Float, photoH: Float, screenW: Float, screenH: Float): Float =
            min(screenW / photoW, screenH / photoH) / coverScale(photoW, photoH, screenW, screenH)

        /** Compact text for the sidecar file; null when it can't be read (callers use the default). */
        fun decode(text: String?): PhotoFraming? {
            val parts = text?.trim()?.split(',')?.map { it.toFloatOrNull() } ?: return null
            if (parts.size != 3 || parts.any { it == null || it.isNaN() }) return null
            return PhotoFraming(parts[0]!!, parts[1]!!, parts[2]!!)
        }

        fun encode(f: PhotoFraming) = "${f.cx},${f.cy},${f.zoom}"
    }
}
