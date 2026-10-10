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

    /**
     * Removes app [key] from the dock, the pages and any folder; a folder left
     * with one app becomes that app in the same slot.
     */
    fun removeApp(layout: HomeLayout, key: AppKey): HomeLayout = HomeLayout(
        dock = layout.dock - key,
        pages = layout.pages.map { page ->
            HomePage(
                page.items.mapNotNull { placed ->
                    when (val item = placed.item) {
                        is HomeItem.App -> placed.takeIf { item.key != key }
                        is HomeItem.Folder -> {
                            val apps = item.apps - key
                            when (apps.size) {
                                item.apps.size -> placed
                                0 -> null
                                1 -> placed.copy(item = HomeItem.App(apps.single()))
                                else -> placed.copy(item = item.copy(apps = apps))
                            }
                        }
                        is HomeItem.Widget -> placed
                    }
                },
            )
        },
    )

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

    /**
     * Moves [source] to [target] in one step. Returns [layout] unchanged when
     * the drop is not allowed: occupied or out-of-bounds cells, a full dock,
     * non-apps into the dock or a folder, a missing source or target.
     * [newFolderId] names the folder an app-on-app drop creates.
     */
    fun drop(layout: HomeLayout, source: DragSource, target: DropTarget, newFolderId: String): HomeLayout {
        if (source is DragSource.Page && target is DropTarget.Cell) {
            val (page, placed) = find(layout, source.itemId) ?: return layout
            if (page == target.page && placed.col == target.col && placed.row == target.row) return layout
        }
        val lifted = lift(layout, source) ?: return layout
        val item = lifted.item
        val result = when (target) {
            is DropTarget.Cell -> {
                val placed = Placed(item, target.col, target.row, lifted.spanX, lifted.spanY)
                val page = lifted.layout.pages.getOrNull(target.page)
                if (page == null || !placed.inBounds || page.items.any { it.overlaps(placed) }) {
                    null
                } else {
                    lifted.layout.updatePage(target.page) { it.copy(items = it.items + placed) }
                }
            }
            is DropTarget.Dock -> {
                val dock = lifted.layout.dock
                if (item !is HomeItem.App || dock.size >= HomeLayout.DOCK_SIZE) {
                    null
                } else {
                    lifted.layout.copy(dock = dock.toMutableList().apply { add(target.index.coerceIn(0, dock.size), item.key) })
                }
            }
            is DropTarget.Onto -> (item as? HomeItem.App)?.let { merge(lifted.layout, target.itemId, it.key, newFolderId) }
            DropTarget.Remove -> lifted.layout
        }
        return result ?: layout
    }

    fun renameFolder(layout: HomeLayout, folderId: String, name: String): HomeLayout {
        val clean = name.trim().ifEmpty { return layout }
        return layout.copy(
            pages = layout.pages.map { page ->
                page.copy(
                    items = page.items.map { placed ->
                        val item = placed.item
                        if (item is HomeItem.Folder && item.folderId == folderId) placed.copy(item = item.copy(name = clean)) else placed
                    },
                )
            },
        )
    }

    fun addPage(layout: HomeLayout): HomeLayout = layout.copy(pages = layout.pages + HomePage.Empty)

    /** Only empty pages go, and the last page always stays. */
    fun removePage(layout: HomeLayout, index: Int): HomeLayout {
        val page = layout.pages.getOrNull(index) ?: return layout
        if (page.items.isNotEmpty() || layout.pages.size <= 1) return layout
        return layout.copy(pages = layout.pages.filterIndexed { i, _ -> i != index })
    }

    /** Moves the page at [from] to [to]; unchanged when either is out of range. */
    fun movePage(layout: HomeLayout, from: Int, to: Int): HomeLayout {
        if (from == to || from !in layout.pages.indices || to !in layout.pages.indices) return layout
        val pages = layout.pages.toMutableList()
        pages.add(to, pages.removeAt(from))
        return layout.copy(pages = pages)
    }

    private class Lifted(val layout: HomeLayout, val item: HomeItem, val spanX: Int, val spanY: Int)

    private fun lift(layout: HomeLayout, source: DragSource): Lifted? = when (source) {
        is DragSource.Page -> find(layout, source.itemId)?.let { (_, placed) ->
            Lifted(remove(layout, source.itemId), placed.item, placed.spanX, placed.spanY)
        }
        is DragSource.Dock -> if (source.key in layout.dock) {
            Lifted(layout.copy(dock = layout.dock - source.key), HomeItem.App(source.key), 1, 1)
        } else {
            null
        }
        is DragSource.FolderApp -> {
            val folderItemId = "folder:${source.folderId}"
            val (pageIndex, placed) = find(layout, folderItemId) ?: return null
            val folder = placed.item as HomeItem.Folder
            if (source.key !in folder.apps) return null
            val apps = folder.apps - source.key
            val rest = when (apps.size) {
                0 -> null
                1 -> placed.copy(item = HomeItem.App(apps.single()))
                else -> placed.copy(item = folder.copy(apps = apps))
            }
            val updated = layout.updatePage(pageIndex) { page ->
                page.copy(items = page.items.mapNotNull { if (it.item.id == folderItemId) rest else it })
            }
            Lifted(updated, HomeItem.App(source.key), 1, 1)
        }
    }

    /** App [key] onto [targetId]: an app becomes a two-app folder in its slot, a folder gains the app. */
    private fun merge(layout: HomeLayout, targetId: String, key: AppKey, folderId: String): HomeLayout? {
        val (pageIndex, target) = find(layout, targetId) ?: return null
        val merged = when (val item = target.item) {
            is HomeItem.App -> if (item.key == key) null else HomeItem.Folder(folderId, "Folder", listOf(item.key, key))
            is HomeItem.Folder -> if (key in item.apps) null else item.copy(apps = item.apps + key)
            is HomeItem.Widget -> null
        } ?: return null
        return layout.updatePage(pageIndex) { page ->
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
