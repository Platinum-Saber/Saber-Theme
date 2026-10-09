package com.sabertheme.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeLayoutCodecTest {
    private fun key(pkg: String, user: Long = 0) = AppKey(pkg, "$pkg.Main", user)

    @Test
    fun v2RoundTripsAppsFoldersAndWidgets() {
        val layout = HomeLayout(
            dock = listOf(key("dialer")),
            pages = listOf(
                HomeLayoutPolicy.defaultWidgetPage(),
                HomePage(
                    listOf(
                        Placed(HomeItem.Folder("f1", "Tabs\tand\nnewlines 100%", listOf(key("wa"), key("ig", user = 150))), 0, 0),
                        Placed(HomeItem.App(key("a")), 3, 4),
                        Placed(HomeItem.Widget("w\t1", WidgetType.Media, "city=Colombo\tx"), 0, 1, 4, 1),
                    ),
                ),
                HomePage.Empty,
            ),
        )
        assertThat(HomeLayoutCodec.decode(HomeLayoutCodec.encode(layout))).isEqualTo(layout)
    }

    @Test
    fun v1MigratesToWidgetPagePlusFirstAppsPage() {
        val v1 = buildString {
            appendLine("v1")
            appendLine("D ${key("dialer").encode()}")
            appendLine("P")
            appendLine("F social\tSocial\t${key("wa").encode()}\t${key("ig").encode()}")
            (1..21).forEach { appendLine("A ${key("p$it").encode()}") }
            appendLine("P")
            appendLine("A ${key("later").encode()}")
        }
        val layout = HomeLayoutCodec.decode(v1)!!
        assertThat(layout.dock).containsExactly(key("dialer"))
        assertThat(layout.pages).hasSize(2)
        assertThat(layout.pages[0]).isEqualTo(HomeLayoutPolicy.defaultWidgetPage())
        val items = layout.pages[1].items
        assertThat(items).hasSize(HomeLayout.PER_PAGE)
        assertThat(items[0]).isEqualTo(Placed(HomeItem.Folder("social", "Social", listOf(key("wa"), key("ig"))), 0, 0))
        assertThat(items[5]).isEqualTo(Placed(HomeItem.App(key("p5")), 1, 1))
        assertThat(items.last()).isEqualTo(Placed(HomeItem.App(key("p19")), 3, 4))
        assertThat(layout.allKeys()).doesNotContain(key("later"))
    }

    @Test
    fun rejectsGarbage() {
        assertThat(HomeLayoutCodec.decode(null)).isNull()
        assertThat(HomeLayoutCodec.decode("v0\nP")).isNull()
        assertThat(HomeLayoutCodec.decode("v1\nA nope")).isNull()
        assertThat(HomeLayoutCodec.decode("v2\nA\t0\t0\tx")).isNull()
        assertThat(HomeLayoutCodec.decode("v2\nP\nW\t0\t0\t2\t2\tid\tNotAType\t")).isNull()
    }
}
