package com.sabertheme.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeLayoutPolicyTest {
    private fun app(pkg: String, label: String = pkg, user: Long = 0) = AppEntry(pkg, "$pkg.Main", label, user)
    private fun key(pkg: String, user: Long = 0) = app(pkg, user = user).key
    private fun placedApp(pkg: String, col: Int, row: Int, user: Long = 0) = Placed(HomeItem.App(key(pkg, user)), col, row)

    private val glyphs = mapOf(
        "dialer" to "phone", "sms" to "messages", "web" to "browser", "cam" to "camera",
        "wa" to "chat", "ig" to "instagram", "tg" to "telegram",
        "photos" to "gallery", "gmail" to "mail", "calc" to "calculator",
    )

    @Test
    fun defaultHasWidgetPageThenCuratedAppsPage() {
        val apps = listOf(
            app("dialer"), app("sms"), app("web"), app("cam"), app("wa"), app("ig"),
            app("calc"), app("gmail"), app("photos"), app("zeta", "Zeta"),
        )
        val layout = HomeLayoutPolicy.default(apps) { glyphs[it] }
        assertThat(layout.dock.map { it.packageName }).containsExactly("dialer", "sms", "web", "cam").inOrder()
        assertThat(layout.pages).hasSize(2)
        assertThat(layout.pages[0]).isEqualTo(HomeLayoutPolicy.defaultWidgetPage())
        val items = layout.pages[1].items
        assertThat(items[0]).isEqualTo(Placed(HomeItem.Folder("social", "Social", listOf(key("wa"), key("ig"))), 0, 0))
        // USEFUL_GLYPHS order (gallery, mail, ..., calculator); unmapped "zeta" stays in the drawer.
        assertThat(items.drop(1)).containsExactly(placedApp("photos", 1, 0), placedApp("gmail", 2, 0), placedApp("calc", 3, 0)).inOrder()
    }

    @Test
    fun defaultWidgetPageFitsTheGridWithoutOverlap() {
        val items = HomeLayoutPolicy.defaultWidgetPage().items
        assertThat(items.all { it.inBounds }).isTrue()
        for (a in items) for (b in items) if (a !== b) assertThat(a.overlaps(b)).isFalse()
        for (p in items) {
            val type = (p.item as HomeItem.Widget).type
            assertThat(type.sizes).contains(WidgetSize(p.spanX, p.spanY))
        }
    }

    @Test
    fun defaultCapsAppsAndKeepsSingleSocialAppLoose() {
        val many = HomeLayoutPolicy.USEFUL_GLYPHS.map { app("pkg.$it") }
        val layout = HomeLayoutPolicy.default(many + app("wa")) { pkg -> if (pkg == "wa") "chat" else pkg.removePrefix("pkg.") }
        val items = layout.pages[1].items
        assertThat(items.filter { it.item is HomeItem.Folder }).isEmpty()
        assertThat(items).hasSize(HomeLayoutPolicy.MAX_DEFAULT_APPS)
    }

    @Test
    fun workProfileCopyIsNotDocked() {
        val layout = HomeLayoutPolicy.default(listOf(app("dialer", user = 10), app("dialer"))) { glyphs[it] }
        assertThat(layout.dock).containsExactly(key("dialer"))
    }

    @Test
    fun reconcileDropsRemovedUnwrapsFoldersInPlaceAndNeverAppends() {
        val layout = HomeLayout(
            dock = listOf(key("dialer"), key("gone")),
            pages = listOf(
                HomeLayoutPolicy.defaultWidgetPage(),
                HomePage(
                    listOf(
                        Placed(HomeItem.Folder("social", "Social", listOf(key("wa"), key("ig"))), 2, 1),
                        placedApp("a", 0, 0),
                        placedApp("gone", 1, 0),
                    ),
                ),
                HomePage(listOf(placedApp("gone", 0, 0))),
            ),
        )
        val installed = listOf(app("dialer"), app("wa"), app("a"), app("new"))
        val result = HomeLayoutPolicy.reconcile(layout, installed)
        assertThat(result.dock).containsExactly(key("dialer"))
        assertThat(result.pages[0]).isEqualTo(HomeLayoutPolicy.defaultWidgetPage())
        assertThat(result.pages[1].items).containsExactly(placedApp("wa", 2, 1), placedApp("a", 0, 0))
        assertThat(result.pages[2].items).isEmpty()
        assertThat(result.allKeys()).doesNotContain(key("new"))
    }

    @Test
    fun reconcileKeepsAppsOfLockedProfiles() {
        val layout = HomeLayout(
            dock = listOf(key("dialer")),
            pages = listOf(HomePage(listOf(placedApp("work", 0, 0, user = 10), placedApp("secure", 1, 0, user = 150), placedApp("gone", 2, 0)))),
        )
        // Profile 10 is paused (its apps are missing from the list); 150 is unlocked and the app is really gone.
        val result = HomeLayoutPolicy.reconcile(layout, listOf(app("dialer")), lockedProfiles = setOf(10))
        assertThat(result.pages[0].items).containsExactly(placedApp("work", 0, 0, user = 10))
    }

    @Test
    fun reconcileDropsOutOfBoundsAndOverlappingItems() {
        val widget = Placed(HomeItem.Widget("w", WidgetType.Clock), 0, 0, 4, 2)
        val layout = HomeLayout(
            dock = emptyList(),
            pages = listOf(HomePage(listOf(widget, placedApp("a", 1, 1), placedApp("b", 4, 0), placedApp("c", 0, 2)))),
        )
        val result = HomeLayoutPolicy.reconcile(layout, listOf(app("a"), app("b"), app("c")))
        assertThat(result.pages[0].items).containsExactly(widget, placedApp("c", 0, 2)).inOrder()
    }

    @Test
    fun reconcileIsStableWhenNothingChanged() {
        val apps = listOf(app("dialer"), app("wa"), app("ig"), app("photos"))
        val layout = HomeLayoutPolicy.default(apps) { glyphs[it] }
        assertThat(HomeLayoutPolicy.reconcile(layout, apps)).isEqualTo(layout)
    }

    @Test
    fun firstFreeSpotHonoursSpans() {
        val page = HomeLayoutPolicy.defaultWidgetPage()
        assertThat(HomeLayoutPolicy.firstFreeSpot(page)).isNull()
        val partial = HomePage(listOf(Placed(HomeItem.Widget("w", WidgetType.Clock), 0, 0, 4, 2), placedApp("a", 0, 2)))
        assertThat(HomeLayoutPolicy.firstFreeSpot(partial)).isEqualTo(1 to 2)
        assertThat(HomeLayoutPolicy.firstFreeSpot(partial, 2, 2)).isEqualTo(1 to 2)
        assertThat(HomeLayoutPolicy.firstFreeSpot(partial, 4, 1)).isEqualTo(0 to 3)
    }

    @Test
    fun addUsesAppsPageThenAppendsAPage() {
        val full = HomePage((0 until HomeLayout.PER_PAGE).map { placedApp("p$it", it % 4, it / 4) })
        val layout = HomeLayout(emptyList(), listOf(HomeLayoutPolicy.defaultWidgetPage(), HomePage(listOf(placedApp("a", 0, 0)))))
        val added = HomeLayoutPolicy.add(layout, HomeItem.App(key("b")))
        assertThat(added.pages[1].items).contains(placedApp("b", 1, 0))

        val overflow = HomeLayoutPolicy.add(layout.copy(pages = listOf(HomeLayoutPolicy.defaultWidgetPage(), full)), HomeItem.App(key("b")))
        assertThat(overflow.pages).hasSize(3)
        assertThat(overflow.pages[2].items).containsExactly(placedApp("b", 0, 0))
    }

    @Test
    fun moveRejectsOccupiedAndOutOfBoundsTargets() {
        val layout = HomeLayout(emptyList(), listOf(HomePage(listOf(placedApp("a", 0, 0), placedApp("b", 1, 0))), HomePage.Empty))
        val id = HomeItem.App(key("a")).id
        assertThat(HomeLayoutPolicy.move(layout, id, 0, 1, 0)).isEqualTo(layout)
        assertThat(HomeLayoutPolicy.move(layout, id, 0, 4, 0)).isEqualTo(layout)
        val moved = HomeLayoutPolicy.move(layout, id, 1, 3, 4)
        assertThat(moved.pages[0].items).containsExactly(placedApp("b", 1, 0))
        assertThat(moved.pages[1].items).containsExactly(placedApp("a", 3, 4))
    }

    @Test
    fun makeFolderMergesIntoTargetSlot() {
        val layout = HomeLayout(emptyList(), listOf(HomePage(listOf(placedApp("a", 2, 3), placedApp("b", 0, 0), placedApp("c", 1, 0)))))
        val folder = HomeLayoutPolicy.makeFolder(layout, HomeItem.App(key("a")).id, HomeItem.App(key("b")).id, "f1")
        assertThat(folder.pages[0].items).containsExactly(
            Placed(HomeItem.Folder("f1", "Folder", listOf(key("a"), key("b"))), 2, 3),
            placedApp("c", 1, 0),
        )
        val grown = HomeLayoutPolicy.makeFolder(folder, "folder:f1", HomeItem.App(key("c")).id, "unused")
        assertThat(grown.pages[0].items).containsExactly(Placed(HomeItem.Folder("f1", "Folder", listOf(key("a"), key("b"), key("c"))), 2, 3))
    }
}
