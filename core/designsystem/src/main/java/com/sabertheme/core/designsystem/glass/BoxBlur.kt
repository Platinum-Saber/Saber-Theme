package com.sabertheme.core.designsystem.glass

/**
 * Three-pass box blur on opaque ARGB pixels (approximates a Gaussian with
 * sigma ~= [radius]). Runs once per wallpaper on a downsampled bitmap, so a
 * plain CPU loop is cheap enough and avoids live per-frame blur.
 */
internal object BoxBlur {
    fun blur(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
        if (radius < 1) return pixels.copyOf()
        var src = pixels.copyOf()
        var dst = IntArray(pixels.size)
        repeat(3) {
            pass(src, dst, w, h, radius, horizontal = true)
            pass(dst, src, w, h, radius, horizontal = false)
        }
        return src
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val window = 2 * r + 1
        for (line in 0 until lines) {
            fun idx(i: Int): Int {
                val k = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + k else k * w + line
            }
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val c = src[idx(i)]
                sr += c shr 16 and 0xFF
                sg += c shr 8 and 0xFF
                sb += c and 0xFF
            }
            for (i in 0 until len) {
                dst[idx(i)] = (0xFF shl 24) or ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
                val add = src[idx(i + r + 1)]
                val rem = src[idx(i - r)]
                sr += (add shr 16 and 0xFF) - (rem shr 16 and 0xFF)
                sg += (add shr 8 and 0xFF) - (rem shr 8 and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }
}
