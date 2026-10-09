package com.sabertheme.core.ui

import com.sabertheme.core.data.AppRepository
import com.sabertheme.core.icons.AppIconSource
import com.sabertheme.core.icons.IconMapper
import com.sabertheme.core.model.AppEntry
import com.sabertheme.core.model.AppKey
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Resolves and caches each app's icon, shared by every surface that draws apps. */
@Singleton
class AppIconResolver @Inject constructor(private val apps: AppRepository) {

    private val cache = ConcurrentHashMap<AppKey, AppIconSource>()

    suspend fun icon(entry: AppEntry): AppIconSource =
        cache[entry.key] ?: resolve(entry).also { cache[entry.key] = it }

    suspend fun app(entry: AppEntry) = LauncherApp(entry, icon(entry))

    private suspend fun resolve(entry: AppEntry): AppIconSource {
        IconMapper.glyphFor(entry.packageName)?.let { return AppIconSource.Glyph(it) }
        val mono = apps.monochromeIcon(entry.key)
        return IconMapper.resolve(entry.packageName, entry.label) { mono }
    }
}
