package com.sabertheme.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeLayoutPolicyTest {
    private fun app(pkg: String, label: String = pkg, user: Long = 0) = AppEntry(pkg, "$pkg.Main", label, user)

    private val glyphs = mapOf(
        "dialer" to "phone", "sms" to "messages", "web" to "browser", "cam" to "camera",
        "wa" to "chat", "ig" to "instagram", "tg" to "telegram",
    )

    @Test
    fun defaultFillsDockGroupsSocialAndSortsTheRest() {
        val apps = listOf(app("dialer"), app("sms"), app("web"), app("cam"), app("wa"), app("ig"), app("zeta", "Zeta"), app("alpha", "Alpha"))
        val layout = HomeLayoutPolicy.default(apps) { glyphs[it] }
        assertThat(layout.dock.map { it.packageName }).containsExactly("dialer", "sms", "web", "cam").inOrder()
        val page = layout.pages.single()
        assertThat(page.first()).isEqualTo(HomeItem.Folder("social", "Social", listOf(app("wa").key, app("ig").key)))
        assertThat(page.drop(1).map { (it as HomeItem.App).key.packageName }).containsExactly("alpha", "zeta").inOrder()
    }

    @Test
    fun singleSocialAppStaysLoose() {
        val layout = HomeLayoutPolicy.default(listOf(app("wa"), app("b"))) { glyphs[it] }
        assertThat(layout.pages.single().filterIsInstance<HomeItem.Folder>()).isEmpty()
    }

    @Test
    fun workProfileCopyIsNotDockedButIsPlaced() {
        val layout = HomeLayoutPolicy.default(listOf(app("dialer", user = 10), app("dialer"))) { glyphs[it] }
        assertThat(layout.dock).containsExactly(app("dialer").key)
        assertThat(layout.allKeys()).contains(app("dialer", user = 10).key)
    }

    @Test
    fun overflowSpillsToNewPages() {
        val apps = (1..45).map { app("p%02d".format(it)) }
        val layout = HomeLayoutPolicy.default(apps) { null }
        assertThat(layout.pages.map { it.size }).containsExactly(20, 20, 5).inOrder()
    }

    @Test
    fun reconcileDropsRemovedUnwrapsFoldersAndAppendsNew() {
        val layout = HomeLayout(
            dock = listOf(app("dialer").key, app("gone").key),
            pages = listOf(
                listOf(HomeItem.Folder("social", "Social", listOf(app("wa").key, app("ig").key)), HomeItem.App(app("a").key)),
                listOf(HomeItem.App(app("b").key)),
            ),
        )
        val installed = listOf(app("dialer"), app("wa"), app("a"), app("b"), app("new"))
        val result = HomeLayoutPolicy.reconcile(layout, installed)
        assertThat(result.dock).containsExactly(app("dialer").key)
        assertThat(result.pages[0]).containsExactly(HomeItem.App(app("wa").key), HomeItem.App(app("a").key)).inOrder()
        assertThat(result.pages[1]).containsExactly(HomeItem.App(app("b").key), HomeItem.App(app("new").key)).inOrder()
    }

    @Test
    fun reconcileIsStableWhenNothingChanged() {
        val apps = (1..25).map { app("p%02d".format(it)) }
        val layout = HomeLayoutPolicy.default(apps) { null }
        assertThat(HomeLayoutPolicy.reconcile(layout, apps)).isEqualTo(layout)
    }

    @Test
    fun codecRoundTripsIncludingAwkwardFolderNames() {
        val layout = HomeLayout(
            dock = listOf(app("dialer").key),
            pages = listOf(
                listOf(HomeItem.Folder("f1", "Tabs\tand\nnewlines 100%", listOf(app("wa").key, app("ig", user = 150).key)), HomeItem.App(app("a").key)),
                listOf(HomeItem.App(app("b").key)),
            ),
        )
        assertThat(HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout))).isEqualTo(layout)
    }

    @Test
    fun codecRejectsGarbage() {
        assertThat(HomeLayoutCodec.decode(null)).isNull()
        assertThat(HomeLayoutCodec.decode("v0\nP")).isNull()
        assertThat(HomeLayoutCodec.decode("v1\nA nope")).isNull()
    }
}
