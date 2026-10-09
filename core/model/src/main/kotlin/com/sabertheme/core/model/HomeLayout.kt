package com.sabertheme.core.model

/** Something placed on a home page. */
sealed interface HomeItem {
    /** Stable identity across moves, used by edit mode and persistence. */
    val id: String

    data class App(val key: AppKey) : HomeItem {
        override val id get() = "app:${key.encode()}"
    }

    data class Folder(val folderId: String, val name: String, val apps: List<AppKey>) : HomeItem {
        override val id get() = "folder:$folderId"
    }

    /** A native glass widget; [config] is type-specific and opaque here. */
    data class Widget(val widgetId: String, val type: WidgetType, val config: String = "") : HomeItem {
        override val id get() = "widget:$widgetId"
    }
}

/** A widget footprint on the grid, in cells. */
data class WidgetSize(val spanX: Int, val spanY: Int) {
    override fun toString() = "${spanX}x$spanY"

    companion object {
        val S2x1 = WidgetSize(2, 1)
        val S2x2 = WidgetSize(2, 2)
        val S4x1 = WidgetSize(4, 1)
        val S4x2 = WidgetSize(4, 2)
    }
}

/** Native widgets; the first size is the default. */
enum class WidgetType(val sizes: List<WidgetSize>) {
    Clock(listOf(WidgetSize.S4x2, WidgetSize.S2x2, WidgetSize.S4x1)),
    Weather(listOf(WidgetSize.S2x2, WidgetSize.S4x2, WidgetSize.S4x1)),
    Calendar(listOf(WidgetSize.S2x2, WidgetSize.S4x2)),
    Media(listOf(WidgetSize.S4x1, WidgetSize.S4x2)),
    Battery(listOf(WidgetSize.S2x1, WidgetSize.S2x2)),
    Alarm(listOf(WidgetSize.S2x1, WidgetSize.S2x2)),
}

/** [item] occupying columns `col until col + spanX` and rows `row until row + spanY`. */
data class Placed(val item: HomeItem, val col: Int, val row: Int, val spanX: Int = 1, val spanY: Int = 1) {
    fun covers(c: Int, r: Int) = c in col until col + spanX && r in row until row + spanY

    fun overlaps(other: Placed) =
        col < other.col + other.spanX && other.col < col + spanX &&
            row < other.row + other.spanY && other.row < row + spanY

    val inBounds get() =
        col >= 0 && row >= 0 && spanX >= 1 && spanY >= 1 &&
            col + spanX <= HomeLayout.COLUMNS && row + spanY <= HomeLayout.ROWS
}

data class HomePage(val items: List<Placed>) {
    fun at(col: Int, row: Int): Placed? = items.firstOrNull { it.covers(col, row) }

    companion object {
        val Empty = HomePage(emptyList())
    }
}

/** Persisted arrangement of the home screen on a [COLUMNS] x [ROWS] grid. */
data class HomeLayout(
    val dock: List<AppKey>,
    val pages: List<HomePage>,
) {
    fun allKeys(): Set<AppKey> = buildSet {
        addAll(dock)
        for (page in pages) for (placed in page.items) when (val item = placed.item) {
            is HomeItem.App -> add(item.key)
            is HomeItem.Folder -> addAll(item.apps)
            is HomeItem.Widget -> Unit
        }
    }

    companion object {
        const val COLUMNS = 4
        const val ROWS = 5
        const val PER_PAGE = COLUMNS * ROWS
        const val DOCK_SIZE = 4
        val Empty = HomeLayout(emptyList(), emptyList())
    }
}
