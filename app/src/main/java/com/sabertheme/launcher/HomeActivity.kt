package com.sabertheme.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.glass.BackdropLoader
import com.sabertheme.core.designsystem.glass.GlassShape
import com.sabertheme.core.designsystem.glass.GlassSurface
import com.sabertheme.core.designsystem.glass.LocalGlassEnvironment
import com.sabertheme.core.designsystem.glass.WallpaperLayer
import com.sabertheme.core.designsystem.glass.rememberGlassEnvironment
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.Radius
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.designsystem.theme.SaberTheme
import com.sabertheme.core.designsystem.wallpaper.AuroraWallpaper
import com.sabertheme.core.icons.AppGlyph
import com.sabertheme.core.icons.AppGlyphIcon
import com.sabertheme.core.icons.AppIconSource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler {}
            val env = rememberGlassEnvironment()
            val wallpaper = AuroraWallpaper.Night
            BackdropLoader(env, wallpaper)
            CompositionLocalProvider(LocalGlassEnvironment provides env) {
                SaberTheme(dark = wallpaper.dark) { SpikeScreen() }
            }
        }
    }
}

/** Engine spike: worst-case home density (20 tiles, widget, dock) across 2 pages. */
@androidx.compose.runtime.Composable
private fun SpikeScreen() {
    val env = LocalGlassEnvironment.current
    val pager = rememberPagerState { 2 }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }
            .collect { env.parallax = Offset(-it * 24f, 0f) }
    }
    Box(Modifier.fillMaxSize()) {
        WallpaperLayer()
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            HorizontalPager(pager, Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    GlassSurface(Modifier.fillMaxWidth().height(164.dp), material = GlassMaterial.Regular) {}
                    repeat(4) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            repeat(4) { col ->
                                val glyph = AppGlyph.entries[(row * 4 + col) % AppGlyph.entries.size]
                                GlassSurface(Modifier.size(60.dp), shape = GlassShape.Rounded(Radius.icon), material = GlassMaterial.Thin) {
                                    AppGlyphIcon(AppIconSource.Glyph(glyph), Saber.colors.glyph, Modifier.align(Alignment.Center), 28.dp)
                                }
                            }
                        }
                    }
                }
            }
            GlassSurface(
                Modifier.padding(12.dp).fillMaxWidth().height(88.dp),
                shape = GlassShape.Rounded(Radius.xl),
                material = GlassMaterial.Thick,
            ) {}
        }
    }
}
