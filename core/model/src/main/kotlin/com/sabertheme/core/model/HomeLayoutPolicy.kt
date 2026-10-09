package com.sabertheme.core.model

/**
 * Builds and maintains the home layout. Pure Kotlin so it is unit-tested.
 * Until the app drawer exists (M2) every installed app lives on a home page.
 */
object HomeLayoutPolicy {
    /** Glyph keys preferred for the dock, in order. */
    val DOCK_GLYPHS = listOf("phone", "messages", "browser", "camera")

    /** Glyph keys grouped into a "Social" folder on first run. */
    val SOCIAL_GLYPHS = listOf("chat", "instagram", "x", "telegram", "facebook", "discord", "work")
    const val SOCIAL_FOLDER_ID = "social"

    /**
     * First-run layout: dock from [DOCK_GLYPHS], a Social folder when at least
     * two social apps exist, then everything else alphabetically.
     * [glyphOf] maps a package name to its glyph key, if any.
     */
    fun default(apps: List<AppEntry>, glyphOf: (String) -> String?): HomeLayout {
        val mainProfile = apps.minOfOrNull { it.userSerial }
        val byGlyph = apps
            .filter { it.userSerial == mainProfile }
            .groupBy { glyphOf(it.packageName) }
        val dock = DOCK_GLYPHS.mapNotNull { g -> byGlyph[g]?.firstOrNull()?.key }.distinct().take(HomeLayout.DOCK_SIZE)
        val social = SOCIAL_GLYPHS.flatMap { g -> byGlyph[g].orEmpty().map { it.key } }.filterNot { it in dock }.distinct()
        val used = (dock + if (social.size >= 2) social else emptyList()).toSet()
        val rest = apps.sortedWith(compareBy({ it.label.lowercase() }, { it.userSerial })).map { it.key }.filterNot { it in used }

        val items = buildList<HomeItem> {
            if (social.size >= 2) add(HomeItem.Folder(SOCIAL_FOLDER_ID, "Social", social))
            rest.forEach { add(HomeItem.App(it)) }
        }
        return HomeLayout(dock, items.chunked(HomeLayout.PER_PAGE).ifEmpty { listOf(emptyList()) })
    }

    /**
     * Keeps [layout] in step with [installed]: removed apps disappear,
     * folders left with one app become that app, empty pages close, and new
     * apps are appended after the last item.
     */
    fun reconcile(layout: HomeLayout, installed: List<AppEntry>): HomeLayout {
        val present = installed.mapTo(HashSet()) { it.key }
        val dock = layout.dock.filter { it in present }
        val items = layout.pages.flatten().mapNotNull { item ->
            when (item) {
                is HomeItem.App -> item.takeIf { it.key in present }
                is HomeItem.Folder -> {
                    val apps = item.apps.filter { it in present }
                    when (apps.size) {
                        0 -> null
                        1 -> HomeItem.App(apps.single())
                        else -> item.copy(apps = apps)
                    }
                }
            }
        }
        val placed = HomeLayout(dock, listOf(items)).allKeys()
        val added = installed
            .filterNot { it.key in placed }
            .sortedWith(compareBy({ it.label.lowercase() }, { it.userSerial }))
            .map { HomeItem.App(it.key) }

        // Keep existing page breaks; only the tail is re-chunked.
        val pages = mutableListOf<List<HomeItem>>()
        var remaining = items
        for (page in layout.pages) {
            val keep = page.count { old -> remaining.any { it.sameSlotAs(old) } }
            if (keep == 0) continue
            pages += remaining.take(keep)
            remaining = remaining.drop(keep)
        }
        val tail = (pages.removeLastOrNull().orEmpty() + remaining + added)
        pages += tail.chunked(HomeLayout.PER_PAGE)
        return HomeLayout(dock, pages.filter { it.isNotEmpty() }.ifEmpty { listOf(emptyList()) })
    }

    private fun HomeItem.sameSlotAs(other: HomeItem): Boolean = when {
        this is HomeItem.App && other is HomeItem.App -> key == other.key
        this is HomeItem.Folder && other is HomeItem.Folder -> id == other.id
        this is HomeItem.App && other is HomeItem.Folder -> key in other.apps
        else -> false
    }
}
