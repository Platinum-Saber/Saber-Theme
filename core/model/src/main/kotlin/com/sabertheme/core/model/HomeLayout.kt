package com.sabertheme.core.model

/** Something placed on a home page. */
sealed interface HomeItem {
    data class App(val key: AppKey) : HomeItem

    data class Folder(val id: String, val name: String, val apps: List<AppKey>) : HomeItem
}

/** Persisted arrangement of the home screen. */
data class HomeLayout(
    val dock: List<AppKey>,
    val pages: List<List<HomeItem>>,
) {
    fun allKeys(): Set<AppKey> = buildSet {
        addAll(dock)
        for (page in pages) for (item in page) when (item) {
            is HomeItem.App -> add(item.key)
            is HomeItem.Folder -> addAll(item.apps)
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
