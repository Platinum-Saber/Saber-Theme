package com.sabertheme.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Imported photo wallpapers. Photos are copied into app storage (the picker
 * grant is temporary) and downscaled so the long edge is at most [MAX_EDGE].
 */
@Singleton
class WallpaperStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
    private val _photos = MutableStateFlow(scan())

    /** Imported photo file names, newest first. */
    val photos: StateFlow<List<String>> = _photos.asStateFlow()

    /** Copies [uri] into the store; returns the new file name. */
    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longEdge = max(info.size.width, info.size.height)
            if (longEdge > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / longEdge
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val name = "${UUID.randomUUID()}.webp"
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 92, it) }
        bitmap.recycle()
        _photos.value = scan()
        name
    }

    suspend fun load(fileName: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(dir, fileName)
        if (!file.isFile) null else BitmapFactory.decodeFile(file.path)
    }

    /** Small preview for pickers. */
    suspend fun thumbnail(fileName: String, targetWidth: Int): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(dir, fileName)
        if (!file.isFile) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val sample = Integer.highestOneBit((bounds.outWidth / targetWidth.coerceAtLeast(1)).coerceAtLeast(1))
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    suspend fun delete(fileName: String) = withContext(Dispatchers.IO) {
        File(dir, fileName).delete()
        _photos.value = scan()
    }

    private fun scan(): List<String> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".webp") }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { it.name }

    private companion object {
        const val MAX_EDGE = 3072
    }
}
