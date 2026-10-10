package com.sabertheme.core.widgetdata

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadata
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.core.graphics.get
import androidx.core.net.toUri
import java.nio.ByteBuffer

/**
 * Better artwork than a session's bitmap, decoded small with black letterbox
 * bars trimmed: the session's artwork URI when the app lets us read it, or
 * (VLC) the media store thumbnail of the video with the same title. VLC puts
 * its cone in ALBUM_ART and its ArtworkProvider only serves VLC, the system
 * and platform-signed callers.
 */
internal class MediaArt(private val resolver: ContentResolver) {
    private val cache = LruCache<String, Bitmap>(8)
    private val failed = mutableSetOf<String>()

    /**
     * content:// artwork URI of [meta], if any. Not file://: that would make us
     * open whatever path another app names, with our own access.
     */
    fun uriOf(meta: MediaMetadata): String? = listOf(
        MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
        MediaMetadata.METADATA_KEY_ART_URI,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
    ).firstNotNullOfOrNull { meta.getString(it) }
        ?.takeIf { it.startsWith("content://") }

    fun cached(uri: String): Bitmap? = cache.get(uri)

    fun needsLoad(uri: String) = cache.get(uri) == null && uri !in failed

    /** Blocking; call off the main thread. */
    fun load(uri: String): Bitmap? = runCatching {
        // Plain open, not ImageDecoder's typed "image/*" open: VLC's provider only serves openFile.
        val bytes = resolver.openInputStream(uri.toUri())?.use { it.readCapped(MAX_ART_BYTES) } ?: return@runCatching null
        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = (SIZE.toFloat() / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
        }
        trimmed(decoded)
    }.getOrNull().also { if (it != null) cache.put(uri, it) else failed += uri }

    /**
     * The system thumbnail of the indexed video titled [title] (as VLC reports
     * it), cached under [localKey]. Needs READ_MEDIA_VIDEO. Blocking.
     */
    fun loadLocal(title: String): Bitmap? = runCatching {
        val uri = findVideo(title) ?: return@runCatching null
        trimmed(resolver.loadThumbnail(uri, Size(SIZE, SIZE), null))
    }.getOrNull().also { val key = localKey(title); if (it != null) cache.put(key, it) else failed += key }

    fun localKey(title: String) = "local:$title"

    private fun findVideo(title: String): Uri? {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val queries = listOf(
            "${MediaStore.Video.Media.TITLE} = ?" to arrayOf(title),
            "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?" to arrayOf("$title.%"),  // LIKE wildcards in titles only widen the match
        )
        for ((selection, args) in queries) {
            resolver.query(collection, arrayOf(MediaStore.Video.Media._ID), selection, args, "${MediaStore.Video.Media.DATE_MODIFIED} DESC")
                ?.use { if (it.moveToFirst()) return ContentUris.withAppendedId(collection, it.getLong(0)) }
        }
        return null
    }

    private fun trimmed(bitmap: Bitmap): Bitmap {
        val (l, t, r, b) = LetterboxTrim.bounds(bitmap.width, bitmap.height) { x, y ->
            val c = bitmap[x, y]
            ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }
        if (l == 0 && t == 0 && r == bitmap.width && b == bitmap.height) return bitmap
        return Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
    }

    private companion object {
        const val SIZE = 384

        /** Full-size album art is a few MB at most. */
        const val MAX_ART_BYTES = 12 * 1024 * 1024
    }
}

/** Finds near-black bars around a picture. Pure; see LetterboxTrimTest. */
internal object LetterboxTrim {
    private const val DARK = 24
    private const val SAMPLES = 24

    /**
     * Returns `[left, top, right, bottom)` of the picture inside uniform dark
     * bars, or the full bounds when trimming would leave less than a quarter of
     * either side (a dark image, not a letterbox).
     */
    fun bounds(width: Int, height: Int, luminance: (x: Int, y: Int) -> Int): IntArray {
        fun rowDark(y: Int) = (0 until SAMPLES).all { luminance(it * (width - 1) / (SAMPLES - 1), y) < DARK }
        fun colDark(x: Int) = (0 until SAMPLES).all { luminance(x, it * (height - 1) / (SAMPLES - 1)) < DARK }
        var top = 0
        while (top < height && rowDark(top)) top++
        var bottom = height
        while (bottom > top && rowDark(bottom - 1)) bottom--
        var left = 0
        while (left < width && colDark(left)) left++
        var right = width
        while (right > left && colDark(right - 1)) right--
        val full = intArrayOf(0, 0, width, height)
        if (bottom - top < height / 4 || right - left < width / 4) return full
        return intArrayOf(left, top, right, bottom)
    }
}
