package com.sabertheme.core.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Process-wide cache of glyphs rasterised once per pixel size. A
 * `painterResource` vector re-rasterises on the main thread for every new
 * composition, which cost 2–4 ms on the first frame of each page swipe.
 */
object GlyphImages {
    private val cache = object : LruCache<Long, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: Long, value: ImageBitmap) = value.width * value.height * 4
    }

    fun get(context: Context, @DrawableRes res: Int, sizePx: Int): ImageBitmap {
        val key = res.toLong() shl 32 or sizePx.toLong()
        cache.get(key)?.let { return it }
        val px = sizePx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        context.getDrawable(res)!!.apply {
            setBounds(0, 0, px, px)
            draw(Canvas(bitmap))
        }
        return bitmap.asImageBitmap().also { cache.put(key, it) }
    }

    private const val MAX_BYTES = 4 * 1024 * 1024
}
