package com.sabertheme.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeLayoutDropTest {
    private fun key(pkg: String) = AppKey(pkg, "$pkg.Main", 0)
    private fun app(pkg: String, col: Int, row: Int) = Placed(HomeItem.App(key(pkg)), col, row)
    private fun id(pkg: String) = HomeItem.App(key(pkg)).id
    private val clock = HomeItem.Widget("w1", WidgetType.Clock)

    private val layout = HomeLayout(
        dock = listOf(key("d1"), key("d2")),
        pages = listOf(
            HomePage(listOf(Placed(clock, 0, 0, 4, 2), app("a", 0, 2), app("b", 1, 2))),
            HomePage(listOf(Placed(HomeItem.Folder("f", "Social", listOf(key("x"), key("y"))), 0, 0))),
        ),
    )

    private fun drop(source: DragSource, target: DropTarget, from: HomeLayout = layout) =
        HomeLayoutPolicy.drop(from, source, target, newFolderId = "new")

    @Test
    fun movesAnAppToAFreeCell() {
        val moved = drop(DragSource.Page(id("a")), DropTarget.Cell(1, 3, 4))
        assertThat(moved.pages[0].items).doesNotContain(app("a", 0, 2))
        assertThat(moved.pages[1].items).contains(app("a", 3, 4))
    }

    @Test
    fun occupiedOrOutOfBoundsCellsAreRejected() {
        assertThat(drop(DragSource.Page(id("a")), DropTarget.Cell(0, 1, 2))).isEqualTo(layout)
        assertThat(drop(DragSource.Page(id("a")), DropTarget.Cell(0, 4, 0))).isEqualTo(layout)
        assertThat(drop(DragSource.Page(id("a")), DropTarget.Cell(5, 0, 0))).isEqualTo(layout)
    }

    @Test
    fun droppingOnItsOwnCellIsANoOpMove() {
        assertThat(drop(DragSource.Page(id("a")), DropTarget.Cell(0, 0, 2))).isEqualTo(layout)
    }

    @Test
    fun widgetsMoveAsSpans() {
        val moved = drop(DragSource.Page(clock.id), DropTarget.Cell(0, 0, 3))
        assertThat(moved.pages[0].items).contains(Placed(clock, 0, 3, 4, 2))
        // A 4x2 widget cannot start in row 4 of a 5-row grid.
        assertThat(drop(DragSource.Page(clock.id), DropTarget.Cell(0, 0, 4))).isEqualTo(layout)
        // Nor overlap the apps in row 2.
        assertThat(drop(DragSource.Page(clock.id), DropTarget.Cell(0, 0, 1))).isEqualTo(layout)
    }

    @Test
    fun pageAppIntoDockAtIndex() {
        val moved = drop(DragSource.Page(id("b")), DropTarget.Dock(1))
        assertThat(moved.dock).containsExactly(key("d1"), key("b"), key("d2")).inOrder()
        assertThat(moved.pages[0].items).doesNotContain(app("b", 1, 2))
    }

    @Test
    fun fullDockRejectsButReordersItself() {
        val full = layout.copy(dock = listOf(key("d1"), key("d2"), key("d3"), key("d4")))
        assertThat(drop(DragSource.Page(id("a")), DropTarget.Dock(0), full)).isEqualTo(full)
        val reordered = drop(DragSource.Dock(key("d4")), DropTarget.Dock(0), full)
        assertThat(reordered.dock).containsExactly(key("d4"), key("d1"), key("d2"), key("d3")).inOrder()
    }

    @Test
    fun widgetsAndFoldersStayOutOfTheDock() {
        assertThat(drop(DragSource.Page(clock.id), DropTarget.Dock(0))).isEqualTo(layout)
        assertThat(drop(DragSource.Page("folder:f"), DropTarget.Dock(0))).isEqualTo(layout)
    }

    @Test
    fun dockAppToPage() {
        val moved = drop(DragSource.Dock(key("d1")), DropTarget.Cell(0, 2, 2))
        assertThat(moved.dock).containsExactly(key("d2"))
        assertThat(moved.pages[0].items).contains(app("d1", 2, 2))
    }

    @Test
    fun appOntoAppMakesAFolderInTheTargetSlot() {
        val merged = drop(DragSource.Page(id("a")), DropTarget.Onto(id("b")))
        assertThat(merged.pages[0].items).containsExactly(
            Placed(clock, 0, 0, 4, 2),
            Placed(HomeItem.Folder("new", "Folder", listOf(key("b"), key("a"))), 1, 2),
        )
    }

    @Test
    fun appOntoFolderJoinsIt() {
        val merged = drop(DragSource.Dock(key("d1")), DropTarget.Onto("folder:f"))
        assertThat(merged.pages[1].items.single().item).isEqualTo(HomeItem.Folder("f", "Social", listOf(key("x"), key("y"), key("d1"))))
        assertThat(merged.dock).containsExactly(key("d2"))
    }

    @Test
    fun widgetOntoAppIsRejected() {
        assertThat(drop(DragSource.Page(clock.id), DropTarget.Onto(id("a")))).isEqualTo(layout)
    }

    @Test
    fun dragOutOfFolderDissolvesItWhenOneAppRemains() {
        val out = drop(DragSource.FolderApp("f", key("x")), DropTarget.Cell(1, 2, 3))
        assertThat(out.pages[1].items).containsExactly(app("y", 0, 0), app("x", 2, 3))
    }

    @Test
    fun removeTakesAppsOffHomeAndDeletesWidgets() {
        assertThat(drop(DragSource.Page(clock.id), DropTarget.Remove).pages[0].items).containsExactly(app("a", 0, 2), app("b", 1, 2))
        assertThat(drop(DragSource.Dock(key("d2")), DropTarget.Remove).dock).containsExactly(key("d1"))
    }

    @Test
    fun missingSourceIsANoOp() {
        assertThat(drop(DragSource.Page("app:nope"), DropTarget.Remove)).isEqualTo(layout)
        assertThat(drop(DragSource.FolderApp("f", key("zz")), DropTarget.Remove)).isEqualTo(layout)
    }

    @Test
    fun renameFolderTrimsAndIgnoresBlank() {
        val renamed = HomeLayoutPolicy.renameFolder(layout, "f", "  Friends ")
        assertThat((renamed.pages[1].items.single().item as HomeItem.Folder).name).isEqualTo("Friends")
        assertThat(HomeLayoutPolicy.renameFolder(layout, "f", "   ")).isEqualTo(layout)
    }

    @Test
    fun pagesAddAndOnlyEmptyOnesRemove() {
        val added = HomeLayoutPolicy.addPage(layout)
        assertThat(added.pages).hasSize(3)
        assertThat(HomeLayoutPolicy.removePage(added, 2).pages).hasSize(2)
        assertThat(HomeLayoutPolicy.removePage(added, 0)).isEqualTo(added)
        val single = HomeLayout(emptyList(), listOf(HomePage.Empty))
        assertThat(HomeLayoutPolicy.removePage(single, 0)).isEqualTo(single)
    }
}
