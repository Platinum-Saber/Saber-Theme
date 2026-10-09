package com.sabertheme.core.designsystem.wallpaper

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Bundled abstract wallpapers, specified exactly as the Figma components
 * "Wallpaper/Aurora Night|Dawn": blurred colour blobs on a 360x780 frame.
 * They are rendered procedurally at device resolution, so no image assets.
 */
@Immutable
data class AuroraWallpaper(
    val id: String,
    val dark: Boolean,
    val background: Color,
    val blobs: List<Blob>,
) {
    /** Centre and radius in 360x780 frame units; [alpha] is the fill opacity. */
    @Immutable
    data class Blob(val cx: Float, val cy: Float, val r: Float, val color: Color, val alpha: Float)

    companion object {
        const val FRAME_W = 360f
        const val FRAME_H = 780f
        /** Figma layer blur on each blob, in frame units. */
        const val BLOB_BLUR = 110f

        val Night = AuroraWallpaper(
            id = "aurora-night",
            dark = true,
            background = Color(0xFF0A0C14),
            blobs = listOf(
                Blob(60f, 140f, 170f, Color(0xFF4054E0), 0.9f),
                Blob(320f, 300f, 160f, Color(0xFF8A3CF0), 0.75f),
                Blob(90f, 560f, 190f, Color(0xFF00A894), 0.6f),
                Blob(300f, 700f, 150f, Color(0xFFE2557A), 0.55f),
                Blob(200f, 420f, 90f, Color(0xFF5BD3FF), 0.4f),
            ),
        )

        val Dawn = AuroraWallpaper(
            id = "aurora-dawn",
            dark = false,
            background = Color(0xFFEEF0F6),
            blobs = listOf(
                Blob(70f, 150f, 170f, Color(0xFF9FB2FF), 0.9f),
                Blob(320f, 320f, 160f, Color(0xFFFFC2D9), 0.9f),
                Blob(80f, 580f, 190f, Color(0xFFA8EEDF), 0.9f),
                Blob(310f, 690f, 150f, Color(0xFFFFDDA6), 0.9f),
                Blob(200f, 420f, 90f, Color(0xFFC9B8FF), 0.7f),
            ),
        )

        val bundled = listOf(Night, Dawn)

        fun byId(id: String?): AuroraWallpaper = bundled.firstOrNull { it.id == id } ?: Night
    }
}
