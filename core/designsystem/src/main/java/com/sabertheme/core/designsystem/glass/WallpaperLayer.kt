package com.sabertheme.core.designsystem.glass

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset

/** The launcher's own wallpaper, offset by parallax (read in draw only). */
@Composable
fun WallpaperLayer(modifier: Modifier = Modifier) {
    val env = LocalGlassEnvironment.current
    Spacer(
        modifier.fillMaxSize().drawBehind {
            val backdrop = env.backdrop ?: return@drawBehind
            val p = env.parallax
            drawImage(backdrop.wallpaper, Offset(p.x - backdrop.overscan, p.y - backdrop.overscan))
        },
    )
}
