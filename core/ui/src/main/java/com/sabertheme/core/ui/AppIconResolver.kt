package com.sabertheme.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import com.sabertheme.core.data.AppRepository
import com.sabertheme.core.icons.AppIconSource
import com.sabertheme.core.icons.IconMapper
import com.sabertheme.core.model.AppEntry
import com.sabertheme.core.model.AppKey
import com.sabertheme.core.model.DominantColor
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resolves and caches each app's icon and colour, shared by every surface that draws apps. */
@Singleton
class AppIconResolver @Inject constructor(private val apps: AppRepository) {

    private val cache = ConcurrentHashMap<AppKey, AppIconSource>()
    private val accents = ConcurrentHashMap<AppKey, Optional<Int>>()

    suspend fun icon(entry: AppEntry): AppIconSource =
        cache[entry.key] ?: resolve(entry).also { cache[entry.key] = it }

    /** Dominant colour of the app's real icon, or null for grey icons. */
    suspend fun accent(entry: AppEntry): Int? =
        (accents[entry.key] ?: sample(entry.key).also { accents[entry.key] = it }).orElse(null)

    suspend fun app(entry: AppEntry) = LauncherApp(entry, icon(entry), accent(entry))

    /** Forgets icons of apps no longer installed, so the cache never outgrows the app list. */
    fun retainOnly(installed: Set<AppKey>) {
        cache.keys.retainAll(installed)
        accents.keys.retainAll(installed)
    }

    private suspend fun resolve(entry: AppEntry): AppIconSource {
        IconMapper.glyphFor(entry.packageName)?.let { return AppIconSource.Glyph(it) }
        val mono = apps.monochromeIcon(entry.key)
        return IconMapper.resolve(entry.packageName, entry.label) { mono }
    }

    /** Draws the icon at 32 px off the main thread and picks its colour. */
    private suspend fun sample(key: AppKey): Optional<Int> = withContext(Dispatchers.Default) {
        Optional.ofNullable(apps.fullIcon(key)?.let(::dominant))
    }

    private fun dominant(icon: Drawable): Int? {
        val bitmap = Bitmap.createBitmap(SAMPLE_PX, SAMPLE_PX, Bitmap.Config.ARGB_8888)
        val drawable = icon.mutate()
        drawable.setBounds(0, 0, SAMPLE_PX, SAMPLE_PX)
        drawable.draw(Canvas(bitmap))
        val pixels = IntArray(SAMPLE_PX * SAMPLE_PX)
        bitmap.getPixels(pixels, 0, SAMPLE_PX, 0, 0, SAMPLE_PX, SAMPLE_PX)
        bitmap.recycle()
        return DominantColor.of(pixels)
    }

    private companion object {
        const val SAMPLE_PX = 32
    }
}
