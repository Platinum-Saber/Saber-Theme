package com.sabertheme.core.icons

import android.graphics.drawable.Drawable

/** What to draw for an app, in lookup order: mapped glyph, monochrome layer, letter. */
sealed interface AppIconSource {
    data class Glyph(val glyph: AppGlyph) : AppIconSource
    /** Adaptive icon's monochrome layer (108dp canvas, safe zone 66dp). */
    class Monochrome(val drawable: Drawable) : AppIconSource
    data class Letter(val letter: String) : AppIconSource
}

object IconMapper {
    fun glyphFor(packageName: String): AppGlyph? = PACKAGE_GLYPHS[packageName]

    /**
     * [monochrome] is only called when no glyph is mapped, so callers can load
     * the app icon lazily.
     */
    fun resolve(packageName: String, label: String, monochrome: () -> Drawable?): AppIconSource =
        glyphFor(packageName)?.let(AppIconSource::Glyph)
            ?: monochrome()?.let(AppIconSource::Monochrome)
            ?: AppIconSource.Letter(letterFor(label, packageName))

    fun letterFor(label: String, packageName: String): String {
        val source = label.trim().ifEmpty { packageName.substringAfterLast('.') }
        val cp = source.codePointAt(0)
        return String(Character.toChars(cp)).uppercase()
    }
}
