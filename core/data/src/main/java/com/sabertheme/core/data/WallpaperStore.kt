package com.sabertheme.core.data

import com.sabertheme.core.model.PhotoFraming
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
    private val _framings = MutableStateFlow(scanFramings())

    /** Saved framing per photo file name (missing: centred fill). */
    val framings: StateFlow<Map<String, PhotoFraming>> = _framings.asStateFlow()

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
        val file = photoFile(fileName)
        if (file == null || !file.isFile) null else BitmapFactory.decodeFile(file.path)
    }

    /** Small preview for pickers. */
    suspend fun thumbnail(fileName: String, targetWidth: Int): Bitmap? = withContext(Dispatchers.IO) {
        val file = photoFile(fileName)
        if (file == null || !file.isFile) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val sample = Integer.highestOneBit((bounds.outWidth / targetWidth.coerceAtLeast(1)).coerceAtLeast(1))
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    suspend fun delete(fileName: String) = withContext(Dispatchers.IO) {
        photoFile(fileName)?.delete()
        framingFile(fileName)?.delete()
        _photos.value = scan()
        _framings.value = scanFramings()
    }

    fun framing(fileName: String): PhotoFraming = _framings.value[fileName] ?: PhotoFraming()

    suspend fun saveFraming(fileName: String, framing: PhotoFraming) = withContext(Dispatchers.IO) {
        val file = framingFile(fileName) ?: return@withContext
        file.writeText(PhotoFraming.encode(framing))
        _framings.value = scanFramings()
    }

    /** Pixel size of a stored photo, without decoding it. */
    suspend fun size(fileName: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val file = photoFile(fileName)
        if (file == null || !file.isFile) return@withContext null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        o.outWidth to o.outHeight
    }

    /**
     * A photo this store named, or null for anything else: the name comes from
     * settings, which a restore could bring back tampered ("../…").
     */
    private fun photoFile(fileName: String): File? = if (isStoredName(fileName)) File(dir, fileName) else null

    private fun framingFile(fileName: String): File? =
        if (isStoredName(fileName)) File(dir, fileName.substringBeforeLast('.') + FRAMING_SUFFIX) else null

    private fun scanFramings(): Map<String, PhotoFraming> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(FRAMING_SUFFIX) }.orEmpty().mapNotNull { f ->
            PhotoFraming.decode(runCatching { f.readText() }.getOrNull())?.let { f.name.removeSuffix(FRAMING_SUFFIX) + ".webp" to it }
        }.toMap()

    private fun scan(): List<String> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".webp") }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { it.name }

    companion object {
        private const val MAX_EDGE = 3072
        private const val FRAMING_SUFFIX = ".framing"
        private val STORED_NAME = Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.webp""")

        /** Names [import] gives photos: a random UUID plus `.webp`, nothing else. */
        internal fun isStoredName(name: String) = STORED_NAME.matches(name)
    }
}
