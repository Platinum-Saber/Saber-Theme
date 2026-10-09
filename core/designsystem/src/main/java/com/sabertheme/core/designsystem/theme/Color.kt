package com.sabertheme.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colour tokens. Values mirror the Figma "Color" collection and
 * `GLASS_COLORS` in design/figma-plugin/src/code.js; change both together.
 */
@Immutable
data class SaberColors(
    val isDark: Boolean,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val glyph: Color,
    val accent: Color,
    /** Base colour of every glass tint; alpha comes from [GlassMaterial.tintAlpha]. */
    val glassTint: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    /** Glance widgets cannot blur, so they use this more opaque fill. */
    val glanceFill: Color,
)

private val Ink = Color(0xFF0E0F12)
private val Paper = Color(0xFFF5F6F8)
private val TintDark = Color(0xFF1A1C22)

val LightSaberColors = SaberColors(
    isDark = false,
    textPrimary = Ink,
    textSecondary = Ink.copy(alpha = 0.65f),
    textTertiary = Ink.copy(alpha = 0.44f),
    glyph = Ink,
    accent = Color(0xFF5B7CFA),
    glassTint = Color.White,
    glassBorder = Color.White.copy(alpha = 0.7f),
    glassHighlight = Color.White.copy(alpha = 0.85f),
    glanceFill = Color.White.copy(alpha = 0.6f),
)

val DarkSaberColors = SaberColors(
    isDark = true,
    textPrimary = Paper,
    textSecondary = Paper.copy(alpha = 0.7f),
    textTertiary = Paper.copy(alpha = 0.44f),
    glyph = Paper,
    accent = Color(0xFF8EA6FF),
    glassTint = TintDark,
    glassBorder = Color.White.copy(alpha = 0.24f),
    glassHighlight = Color.White.copy(alpha = 0.4f),
    glanceFill = TintDark.copy(alpha = 0.7f),
)
