package com.sabertheme.iconpack

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/** Unit tests run with the module directory as the working directory. */
class AppFilterTest {
    private val main = File("src/main")

    private fun items(file: File): List<Element> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("item")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun iconExists(name: String) = File(main, "res/drawable-anydpi/$name.xml").isFile

    @Test
    fun appfilterComponentsAreWellFormedAndDrawablesExist() {
        val items = items(File(main, "res/xml/appfilter.xml"))
        assertThat(items).isNotEmpty()
        val component = Regex("""ComponentInfo\{[\w.]+/[\w.$]+\}""")
        for (item in items) {
            assertThat(item.getAttribute("component")).matches(component.pattern)
            assertThat(iconExists(item.getAttribute("drawable"))).isTrue()
        }
        assertThat(items.map { it.getAttribute("component") }.toSet()).hasSize(items.size)
    }

    @Test
    fun assetsCopyMatchesResource() {
        assertThat(File(main, "assets/appfilter.xml").readText())
            .isEqualTo(File(main, "res/xml/appfilter.xml").readText())
    }

    @Test
    fun drawableListPointsAtExistingIcons() {
        val items = items(File(main, "res/xml/drawable.xml"))
        assertThat(items).isNotEmpty()
        items.forEach { assertThat(iconExists(it.getAttribute("drawable"))).isTrue() }
    }
}
