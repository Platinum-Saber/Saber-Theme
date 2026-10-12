package com.sabertheme.core.icons

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IconMapperTest {
    @Test
    fun mappedPackageResolvesToGlyphWithoutLoadingIcon() {
        var loaded = false
        val source = IconMapper.resolve("com.samsung.android.dialer", "Phone") { loaded = true; null }
        assertThat(source).isEqualTo(AppIconSource.Glyph(AppGlyph.PHONE))
        assertThat(loaded).isFalse()
    }

    @Test
    fun unmappedPackageWithoutMonochromeFallsBackToLetter() {
        val source = IconMapper.resolve("org.example.app", "  zed notes") { null }
        assertThat(source).isEqualTo(AppIconSource.Letter("Z"))
    }

    @Test
    fun emptyLabelUsesLastPackageSegment() {
        assertThat(IconMapper.letterFor("", "org.example.quill")).isEqualTo("Q")
    }

    @Test
    fun letterKeepsSurrogatePairsIntact() {
        assertThat(IconMapper.letterFor("😀 Smile", "x")).isEqualTo("😀")
    }

    @Test
    fun differentBrandsNeverShareAGlyph() {
        val shared = PACKAGE_GLYPHS.entries.groupBy({ it.value }, { it.key })
            .values.map { it.toSet() }.filter { it.size > 1 }
        assertThat(shared.filterNot { it in VARIANTS }).isEmpty()
    }

    @Test
    fun everyGlyphHasAPackage() {
        val mapped = PACKAGE_GLYPHS.values.toSet()
        assertThat(AppGlyph.entries.filterNot { it in mapped }).isEmpty()
    }

    private companion object {
        /** Packages that are the same app, so they may share a glyph. */
        val VARIANTS = setOf(
            setOf("com.whatsapp", "com.whatsapp.w4b"),
            setOf("com.google.android.dialer", "com.android.dialer"),
            setOf("com.samsung.android.app.watchmanager", "com.samsung.accessory"),
        )
    }
}
