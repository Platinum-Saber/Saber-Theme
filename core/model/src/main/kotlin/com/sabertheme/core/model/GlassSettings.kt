package com.sabertheme.core.model

/** What the launcher draws behind everything. */
sealed interface WallpaperChoice {
    /** One of the procedurally drawn Aurora wallpapers. */
    data class Bundled(val id: String) : WallpaperChoice

    /** A photo the user imported; [fileName] lives in the app's wallpaper folder. */
    data class Photo(val fileName: String) : WallpaperChoice

    companion object {
        val Default: WallpaperChoice = Bundled("aurora-night")
    }
}

/** How app icons are drawn (Figma IconTile Style). */
enum class IconStyle {
    /** Glyph on a glass squircle. */
    Tile,

    /** Glyph only, a little larger; folders keep their glass. */
    Bare,
}

data class GlassSettings(
    /** 0..1 user effect strength; the effects policy may lower it further. */
    val intensity: Float = 1f,
    val wallpaper: WallpaperChoice = WallpaperChoice.Default,
    val iconStyle: IconStyle = IconStyle.Tile,
    val showLabels: Boolean = true,
    /** Device tilt moves the light and the wallpaper. */
    val tiltEnabled: Boolean = true,
    /** The Saber mascot on the search bar. */
    val mascotEnabled: Boolean = true,
    val mascotOutfit: MascotOutfit = MascotOutfit.Armor,
    /** Double-tap empty Home space locks the phone (needs the accessibility service). */
    val doubleTapLock: Boolean = true,
)

/** Mascot outfits; drawn by :feature:mascot. */
enum class MascotOutfit { Armor, Winter, Casual }
