package com.sabertheme.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sabertheme.core.designsystem.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val flex = GoogleFont("Google Sans Flex")

/** Google Sans Flex via downloadable fonts; falls back to the system sans. */
val SaberFontFamily = FontFamily(
    Font(flex, provider, FontWeight.Light),
    Font(flex, provider, FontWeight.Normal),
    Font(flex, provider, FontWeight.Medium),
    Font(flex, provider, FontWeight.SemiBold),
)

/** Figma text styles; letter spacing converted from px to em. */
@Immutable
data class SaberTypography(
    val displayClock: TextStyle,
    val displayLarge: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val body: TextStyle,
    val labelMedium: TextStyle,
    val captionIcon: TextStyle,
)

private fun style(size: Int, weight: FontWeight, trackingPx: Float) = TextStyle(
    fontFamily = SaberFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = (trackingPx / size).em,
    lineHeight = (size * if (size > 40) 1.0f else 1.3f).sp,
)

val DefaultSaberTypography = SaberTypography(
    displayClock = style(72, FontWeight.Light, -2f),
    displayLarge = style(44, FontWeight.Light, -1f),
    titleLarge = style(22, FontWeight.Medium, -0.2f),
    titleMedium = style(17, FontWeight.Medium, 0f),
    body = style(15, FontWeight.Normal, 0f),
    labelMedium = style(13, FontWeight.Medium, 0.1f),
    captionIcon = style(11, FontWeight.Medium, 0.2f),
)
