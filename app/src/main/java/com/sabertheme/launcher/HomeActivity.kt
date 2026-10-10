package com.sabertheme.launcher

import com.sabertheme.feature.mascot.MascotLayer
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabertheme.core.designsystem.glass.BackdropLoader
import com.sabertheme.core.designsystem.glass.GlassEffectsController
import com.sabertheme.core.designsystem.glass.LocalGlassEnvironment
import com.sabertheme.core.designsystem.glass.WallpaperLayer
import com.sabertheme.core.designsystem.glass.glassInteractionTracker
import com.sabertheme.core.designsystem.glass.rememberGlassEnvironment
import com.sabertheme.core.designsystem.theme.SaberTheme
import com.sabertheme.core.ui.IconAppearance
import com.sabertheme.core.ui.LocalIconAppearance
import com.sabertheme.core.widgetdata.WidgetSources
import com.sabertheme.feature.drawer.AppDrawer
import com.sabertheme.feature.drawer.DrawerViewModel
import com.sabertheme.feature.drawer.rememberDrawerState
import com.sabertheme.feature.home.HomeScreen
import com.sabertheme.feature.home.HomeViewModel
import com.sabertheme.feature.settings.SettingsScreen
import com.sabertheme.feature.settings.SettingsViewModel
import com.sabertheme.feature.settings.rememberSettingsState
import com.sabertheme.feature.widgets.WidgetHost
import com.sabertheme.feature.widgets.WidgetPicker
import com.sabertheme.launcher.debug.FrameStatsOverlay
import com.sabertheme.launcher.debug.GlassLab
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()
    private val drawerViewModel: DrawerViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    /** Bumped by the Home button while already home; closes the drawer. */
    private val homePresses = MutableStateFlow(0)

    @Inject lateinit var widgets: WidgetSources

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler {}
            val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return@setContent
            val home = viewModel.home.collectAsStateWithLifecycle().value
            val env = rememberGlassEnvironment()
            var previewIntensity by remember { mutableStateOf<Float?>(null) }
            val settingsPage = rememberSettingsState()
            var frameOverlay by rememberSaveable { mutableStateOf(false) }
            val drawer = rememberDrawerState()
            var widgetPickerOpen by rememberSaveable { mutableStateOf(false) }
            val homePressCount by homePresses.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                homePresses.drop(1).collect {
                    drawer.close()
                    settingsPage.close()
                    widgetPickerOpen = false
                }
            }

            LaunchedEffect(settings.intensity) { previewIntensity = null }
            BackdropLoader(env, remember(settings.wallpaper) { viewModel.backdropSource(settings.wallpaper) })
            GlassEffectsController(env, previewIntensity ?: settings.intensity)
            LaunchedEffect(settings.tiltEnabled) {
                env.effects = env.effects.copy(tilt = settings.tiltEnabled)
                if (!settings.tiltEnabled) env.tiltParallax = Offset.Zero
            }
            val icons = remember(settings.iconStyle, settings.showLabels) { IconAppearance(settings.iconStyle, settings.showLabels) }

            CompositionLocalProvider(LocalGlassEnvironment provides env, LocalIconAppearance provides icons) {
                SaberTheme(dark = env.backdrop?.dark ?: true) {
                    Box(Modifier.fillMaxSize().glassInteractionTracker(env)) {
                        if (home != null) {
                            HomeScreen(
                                home,
                                viewModel,
                                onOpenSettings = settingsPage::open,
                                onOpenDrawer = { drawer.open(keyboard = it) },
                                onOpenWidgets = { widgetPickerOpen = true },
                                backgroundBlur = { maxOf(drawer.fraction, settingsPage.fraction) },
                                resetSignal = homePressCount,
                                widgetContent = { widget, size, modifier -> WidgetHost(widgets, widget, size, modifier) },
                                companion = { anchor -> MascotLayer(anchor) },
                            )
                        } else {
                            WallpaperLayer()
                        }
                        AppDrawer(drawer, drawerViewModel)
                        WidgetPicker(widgetPickerOpen, onDismiss = { widgetPickerOpen = false }) { type, size ->
                            viewModel.addWidget(type, size)
                        }
                        SettingsScreen(
                            settingsPage,
                            settingsViewModel,
                            onIntensityPreview = { previewIntensity = it },
                            debugTools = if (BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "benchmark") {
                                { GlassLab(env, frameOverlay) { frameOverlay = it } }
                            } else {
                                null
                            },
                        )
                        if (frameOverlay) FrameStatsOverlay(Modifier.align(Alignment.TopCenter))
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.value++
    }

    override fun onResume() {
        super.onResume()
        // Permission or notification-access changes made in Settings.
        widgets.permissions.recheck()
    }
}
