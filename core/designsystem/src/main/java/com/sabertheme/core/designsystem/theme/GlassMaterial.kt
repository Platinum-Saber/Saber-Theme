package com.sabertheme.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glass material tokens (`GLASS_COLORS` / `GLASS_FLOATS` in the Figma
 * plugin, and the Glass/Thin|Regular|Thick effect styles). Feature code
 * picks a preset; it never passes raw numbers.
 */
@Immutable
data class GlassMaterial(
    val name: String,
    val blurRadius: Dp,
    val tintAlphaLight: Float,
    val tintAlphaDark: Float,
    /** 0..1, edge lensing strength. */
    val refraction: Float,
    /** 0..1, specular rim strength. */
    val highlight: Float,
    val borderWidth: Dp,
    /** Top inner-shadow (rim light) alpha from the Figma effect style. */
    val rimAlpha: Float,
    val shadowAlpha: Float,
) {
    fun tintAlpha(dark: Boolean): Float = if (dark) tintAlphaDark else tintAlphaLight

    companion object {
        /** Icon tiles, small chips. */
        val Thin = GlassMaterial(
            name = "thin", blurRadius = 6.dp, tintAlphaLight = 0.06f, tintAlphaDark = 0.10f,
            refraction = 0.35f, highlight = 0.6f, borderWidth = 1.dp, rimAlpha = 0.35f, shadowAlpha = 0.10f,
        )

        /** Widgets, search pill. */
        val Regular = GlassMaterial(
            name = "regular", blurRadius = 12.dp, tintAlphaLight = 0.14f, tintAlphaDark = 0.20f,
            refraction = 0.35f, highlight = 0.6f, borderWidth = 1.dp, rimAlpha = 0.55f, shadowAlpha = 0.10f,
        )

        /** Overlays: dock, menus, sheets, open folders. */
        val Thick = GlassMaterial(
            name = "thick", blurRadius = 36.dp, tintAlphaLight = 0.34f, tintAlphaDark = 0.42f,
            refraction = 0.35f, highlight = 0.6f, borderWidth = 1.dp, rimAlpha = 0.55f, shadowAlpha = 0.18f,
        )

        val presets = listOf(Thin, Regular, Thick)
    }
}
