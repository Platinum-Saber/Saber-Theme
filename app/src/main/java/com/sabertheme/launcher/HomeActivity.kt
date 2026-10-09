package com.sabertheme.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabertheme.core.designsystem.glass.BackdropLoader
import com.sabertheme.core.designsystem.glass.GlassEffectsController
import com.sabertheme.core.designsystem.glass.GlassShape
import com.sabertheme.core.designsystem.glass.GlassSurface
import com.sabertheme.core.designsystem.glass.LocalGlassEnvironment
import com.sabertheme.core.designsystem.glass.WallpaperLayer
import com.sabertheme.core.designsystem.glass.glassInteractionTracker
import com.sabertheme.core.designsystem.glass.rememberGlassEnvironment
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.Radius
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.designsystem.theme.SaberTheme
import com.sabertheme.core.icons.AppGlyph
import com.sabertheme.core.icons.AppGlyphIcon
import com.sabertheme.core.icons.AppIconSource
import com.sabertheme.feature.home.HomeOptionsSheet
import com.sabertheme.feature.home.HomeViewModel
import com.sabertheme.launcher.debug.FrameStatsOverlay
import com.sabertheme.launcher.debug.GlassLab
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler {}
            val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return@setContent
            val photos by viewModel.photos.collectAsStateWithLifecycle()
            val importing by viewModel.importing.collectAsStateWithLifecycle()
            val env = rememberGlassEnvironment()
            var previewIntensity by remember { mutableStateOf<Float?>(null) }
            var optionsOpen by rememberSaveable { mutableStateOf(false) }
            var frameOverlay by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(settings.intensity) { previewIntensity = null }
            BackdropLoader(env, remember(settings.wallpaper) { viewModel.backdropSource(settings.wallpaper) })
            GlassEffectsController(env, previewIntensity ?: settings.intensity)

            CompositionLocalProvider(LocalGlassEnvironment provides env) {
                SaberTheme(dark = env.backdrop?.dark ?: true) {
                    Box(Modifier.fillMaxSize().glassInteractionTracker(env)) {
                        SpikeScreen(onLongPressEmpty = { optionsOpen = true })
                        if (frameOverlay) FrameStatsOverlay(Modifier.align(Alignment.TopCenter))
                        HomeOptionsSheet(
                            visible = optionsOpen,
                            onDismiss = { optionsOpen = false },
                            settings = settings,
                            photos = photos,
                            importing = importing,
                            viewModel = viewModel,
                            onIntensityPreview = { previewIntensity = it },
                        ) {
                            if (BuildConfig.DEBUG) GlassLab(env, frameOverlay) { frameOverlay = it }
                        }
                    }
                }
            }
        }
    }
}

/** Engine spike layout; replaced by the real home in step 6. */
@Composable
private fun SpikeScreen(onLongPressEmpty: () -> Unit) {
    val env = LocalGlassEnvironment.current
    val pager = rememberPagerState { 2 }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }
            .collect { env.pageParallax = Offset(-it * 12f, 0f) }
    }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPressEmpty() }) }) {
        WallpaperLayer()
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            HorizontalPager(pager, Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    GlassSurface(Modifier.fillMaxWidth().height(164.dp), material = GlassMaterial.Regular) {}
                    repeat(4) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            repeat(4) { col ->
                                val glyph = AppGlyph.entries[(row * 4 + col) % AppGlyph.entries.size]
                                GlassSurface(
                                    Modifier.size(60.dp),
                                    shape = GlassShape.Rounded(Radius.icon),
                                    material = GlassMaterial.Thin,
                                    onClick = {},
                                ) {
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
