package com.sabertheme.core.model

/** Where a dragged item was picked up. */
sealed interface DragSource {
    /** An app, folder or widget on a page, by [HomeItem.id]. */
    data class Page(val itemId: String) : DragSource

    data class Dock(val key: AppKey) : DragSource

    /** An app dragged out of an open folder. */
    data class FolderApp(val folderId: String, val key: AppKey) : DragSource
}

/** Where a dragged item is dropped. */
sealed interface DropTarget {
    /** Top-left cell on [page]; the item keeps its span. */
    data class Cell(val page: Int, val col: Int, val row: Int) : DropTarget

    /** Insert into the dock at [index] (apps only). */
    data class Dock(val index: Int) : DropTarget

    /** Onto the app or folder [itemId]: makes or grows a folder (apps only). */
    data class Onto(val itemId: String) : DropTarget

    /** Off home; apps stay in the drawer, widgets are deleted. */
    data object Remove : DropTarget
}
