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

data class GlassSettings(
    /** 0..1 user effect strength; the effects policy may lower it further. */
    val intensity: Float = 1f,
    val wallpaper: WallpaperChoice = WallpaperChoice.Default,
)
