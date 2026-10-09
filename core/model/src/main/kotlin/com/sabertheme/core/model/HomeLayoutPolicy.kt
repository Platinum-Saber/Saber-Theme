package com.sabertheme.core.model

/**
 * Builds and maintains the curated home layout. Pure Kotlin so it is
 * unit-tested. Every app lives in the drawer; home holds what the user (or
 * the first-run default) chose, so new installs are never added here.
 */
object HomeLayoutPolicy {
    /** Glyph keys preferred for the dock, in order. */
    val DOCK_GLYPHS = listOf("phone", "messages", "browser", "camera")

    /** Glyph keys grouped into a "Social" folder on first run. */
    val SOCIAL_GLYPHS = listOf("chat", "instagram", "x", "telegram", "facebook", "discord", "work")
    const val SOCIAL_FOLDER_ID = "social"

    /** Glyph keys for the first-run apps page, most useful first. */
    val USEFUL_GLYPHS = listOf(
        "gallery", "mail", "maps", "calendar", "clock", "settings", "store", "music", "video",
        "files", "notes", "contacts", "weather", "calculator", "wallet", "drive", "health",
        "translate", "podcasts", "tasks", "recorder", "scanner", "netflix",
    )
    const val MAX_DEFAULT_APPS = 15

    /** First page as in Figma, fitted to 4x5 (Media is left to the picker: it needs listener access). */
    fun defaultWidgetPage(): HomePage = HomePage(
        listOf(
            Placed(HomeItem.Widget("clock", WidgetType.Clock), 0, 0, 4, 2),
            Placed(HomeItem.Widget("weather", WidgetType.Weather), 0, 2, 2, 2),
            Placed(HomeItem.Widget("calendar", WidgetType.Calendar), 2, 2, 2, 2),
            Placed(HomeItem.Widget("battery", WidgetType.Battery), 0, 4, 2, 1),
            Placed(HomeItem.Widget("alarm", WidgetType.Alarm), 2, 4, 2, 1),
        ),
    )

    /**
     * First-run layout: dock from [DOCK_GLYPHS]; page 1 widgets; page 2 a
     * Social folder (when at least two social apps exist) and up to
     * [MAX_DEFAULT_APPS] apps from [USEFUL_GLYPHS]. [glyphOf] maps a package
     * name to its glyph key, if any.
     */
    fun default(apps: List<AppEntry>, glyphOf: (String) -> String?): HomeLayout {
        val mainProfile = apps.minOfOrNull { it.userSerial }
        val byGlyph = apps
            .filter { it.userSerial == mainProfile }
            .sortedBy { it.label.lowercase() }
            .groupBy { glyphOf(it.packageName) }
        val dock = DOCK_GLYPHS.mapNotNull { g -> byGlyph[g]?.firstOrNull()?.key }.distinct().take(HomeLayout.DOCK_SIZE)
        val social = SOCIAL_GLYPHS.flatMap { g -> byGlyph[g].orEmpty().map { it.key } }.filterNot { it in dock }.distinct()
        val useSocial = social.size >= 2
        val taken = (dock + if (useSocial) social else emptyList()).toSet()
        val useful = USEFUL_GLYPHS
            .mapNotNull { g -> byGlyph[g]?.firstOrNull()?.key }
            .filterNot { it in taken }
            .distinct()
            .take(MAX_DEFAULT_APPS)

        val items = buildList<HomeItem> {
            if (useSocial) add(HomeItem.Folder(SOCIAL_FOLDER_ID, "Social", social))
            useful.forEach { add(HomeItem.App(it)) }
        }
        val appsPage = HomePage(items.mapIndexed { i, item -> Placed(item, i % HomeLayout.COLUMNS, i / HomeLayout.COLUMNS) })
        return HomeLayout(dock, listOf(defaultWidgetPage(), appsPage))
    }

    /**
     * Keeps [layout] in step with [installed]: removed apps disappear and
     * folders left with one app become that app in place. Apps in a
     * [lockedProfiles] profile (paused work profile, locked Secure Folder)
     * keep their slots, since their absence says nothing. Items out of bounds
     * or overlapping an earlier item are dropped. Pages are never added or
     * removed here.
     */
    fun reconcile(layout: HomeLayout, installed: List<AppEntry>, lockedProfiles: Set<Long> = emptySet()): HomeLayout {
        val present = installed.mapTo(HashSet()) { it.key }
        fun keep(key: AppKey) = key in present || key.userSerial in lockedProfiles

        val dock = layout.dock.filter(::keep).distinct().take(HomeLayout.DOCK_SIZE)
        val pages = layout.pages.map { page ->
            val kept = mutableListOf<Placed>()
            for (placed in page.items) {
                if (!placed.inBounds || kept.any { it.overlaps(placed) }) continue
                val item = when (val item = placed.item) {
                    is HomeItem.App -> item.takeIf { keep(it.key) }
                    is HomeItem.Folder -> {
                        val apps = item.apps.filter(::keep)
                        when (apps.size) {
                            0 -> null
                            1 -> HomeItem.App(apps.single())
                            else -> if (apps == item.apps) item else item.copy(apps = apps)
                        }
                    }
                    is HomeItem.Widget -> item
                } ?: continue
                kept += if (item === placed.item) placed else placed.copy(item = item, spanX = 1, spanY = 1)
            }
            if (kept == page.items) page else HomePage(kept)
        }
        return HomeLayout(dock, pages.ifEmpty { listOf(HomePage.Empty) })
    }

    /** Top-left cell of the first free [spanX] x [spanY] area, in reading order, or null when full. */
    fun firstFreeSpot(page: HomePage, spanX: Int = 1, spanY: Int = 1): Pair<Int, Int>? {
        for (row in 0..HomeLayout.ROWS - spanY) for (col in 0..HomeLayout.COLUMNS - spanX) {
            val probe = Placed(HomeItem.Widget("", WidgetType.Clock), col, row, spanX, spanY)
            if (page.items.none { it.overlaps(probe) }) return col to row
        }
        return null
    }

    /**
     * Places [item] at the first free spot, starting from [fromPage] (the
     * apps page by default); appends a page when every page is full.
     */
    fun add(layout: HomeLayout, item: HomeItem, spanX: Int = 1, spanY: Int = 1, fromPage: Int = 1): HomeLayout {
        val order = layout.pages.indices.let { all -> all.filter { it >= fromPage } + all.filter { it < fromPage } }
        for (index in order) {
            val (col, row) = firstFreeSpot(layout.pages[index], spanX, spanY) ?: continue
            return layout.updatePage(index) { it.copy(items = it.items + Placed(item, col, row, spanX, spanY)) }
        }
        return layout.copy(pages = layout.pages + HomePage(listOf(Placed(item, 0, 0, spanX, spanY))))
    }

    /** Moves the item with [itemId] to [toPage] at ([col], [row]); no-op when the target is occupied or out of bounds. */
    fun move(layout: HomeLayout, itemId: String, toPage: Int, col: Int, row: Int): HomeLayout {
        val (fromPage, placed) = find(layout, itemId) ?: return layout
        if (toPage !in layout.pages.indices) return layout
        val moved = placed.copy(col = col, row = row)
        if (!moved.inBounds) return layout
        val others = layout.pages[toPage].items.filter { it.item.id != itemId }
        if (others.any { it.overlaps(moved) }) return layout
        return layout
            .updatePage(fromPage) { page -> page.copy(items = page.items.filter { it.item.id != itemId }) }
            .updatePage(toPage) { page -> page.copy(items = page.items + moved) }
    }

    /** Removes the item with [itemId] from every page (not the dock). */
    fun remove(layout: HomeLayout, itemId: String): HomeLayout =
        layout.copy(pages = layout.pages.map { page -> page.copy(items = page.items.filter { it.item.id != itemId }) })

    /**
     * Drops the app [droppedId] onto the app or folder [targetId]: an app
     * target becomes a folder named [name] in its slot; a folder target gains
     * the app. Widgets and folders cannot be dropped in.
     */
    fun makeFolder(layout: HomeLayout, targetId: String, droppedId: String, folderId: String, name: String = "Folder"): HomeLayout {
        if (targetId == droppedId) return layout
        val (_, dropped) = find(layout, droppedId) ?: return layout
        val droppedKey = (dropped.item as? HomeItem.App)?.key ?: return layout
        val (targetPage, target) = find(layout, targetId) ?: return layout
        val merged = when (val item = target.item) {
            is HomeItem.App -> HomeItem.Folder(folderId, name, listOf(item.key, droppedKey))
            is HomeItem.Folder -> item.copy(apps = item.apps + droppedKey)
            is HomeItem.Widget -> return layout
        }
        return remove(layout, droppedId).updatePage(targetPage) { page ->
            page.copy(items = page.items.map { if (it.item.id == targetId) it.copy(item = merged) else it })
        }
    }

    fun find(layout: HomeLayout, itemId: String): Pair<Int, Placed>? {
        layout.pages.forEachIndexed { index, page ->
            page.items.firstOrNull { it.item.id == itemId }?.let { return index to it }
        }
        return null
    }

    private fun HomeLayout.updatePage(index: Int, transform: (HomePage) -> HomePage) =
        copy(pages = pages.mapIndexed { i, page -> if (i == index) transform(page) else page })
}
