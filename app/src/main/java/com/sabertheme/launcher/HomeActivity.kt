package com.sabertheme.launcher

import com.sabertheme.core.briefing.BriefingBridge
import com.sabertheme.core.briefing.BriefingStore
import com.sabertheme.core.briefing.SaberVoice
import com.sabertheme.core.briefing.SpeakGuard
import com.sabertheme.core.data.ScreenLock
import com.sabertheme.feature.mascot.CloudKind
import com.sabertheme.core.widgetdata.WidgetState
import com.sabertheme.feature.mascot.Outfit
import com.sabertheme.core.widgetdata.ChatNotifications
import com.sabertheme.feature.mascot.CloudMessage
import com.sabertheme.feature.mascot.MascotLayer
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import java.util.function.Consumer
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()
    private val drawerViewModel: DrawerViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    /** Bumped by the Home button while already home; closes the drawer. */
    private val homePresses = MutableStateFlow(0)

    /** Home is visible in a screen recording or share: message previews hide. */
    private val screenRecorded = MutableStateFlow(false)
    private val recordingCallback = Consumer<Int> { screenRecorded.value = it == WindowManager.SCREEN_RECORDING_STATE_VISIBLE }

    @Inject lateinit var widgets: WidgetSources
    @Inject lateinit var briefingStore: BriefingStore
    @Inject lateinit var briefingBridge: BriefingBridge
    @Inject lateinit var voice: SaberVoice

    /** Briefing rows flicked away this session; they come back with the next Home start. */
    private val hiddenBriefing = MutableStateFlow(emptySet<String>())

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
            var lockPrompt by rememberSaveable { mutableStateOf(false) }
            // The mascot dances while anything plays (needs the Media widget's listener access).
            val media by widgets.media.state.collectAsStateWithLifecycle(WidgetState.Loading)
            val musicPlaying = (media as? WidgetState.Ready)?.data?.let { m -> m.now?.playing == true || m.apps.any { it.playing } } == true
            val playingNow = rememberUpdatedState(musicPlaying)
            // ...and stands guard with a glowing sword while charging.
            val battery by widgets.battery.state.collectAsStateWithLifecycle(WidgetState.Loading)
            val charging = (battery as? WidgetState.Ready)?.data?.charging == true
            // ...and keeps unread WhatsApp chats in a cloud beside her (never while the screen is recorded).
            val chats by ChatNotifications.messages.collectAsStateWithLifecycle()
            val recorded by screenRecorded.collectAsStateWithLifecycle()
            // ...and her briefing (deadlines, mail, calls, events) after the chats.
            val briefing by briefingStore.briefing.collectAsStateWithLifecycle(null)
            val hidden by hiddenBriefing.collectAsStateWithLifecycle()
            val speaking by voice.speaking.collectAsStateWithLifecycle()
            val cloudMessages = remember(chats, settings.mascotMessageCloud, settings.briefing, briefing, hidden, recorded) {
                if (recorded) return@remember emptyList()
                val chatRows = if (!settings.mascotMessageCloud) emptyList() else chats.map { m ->
                    CloudMessage(
                        m.chat, m.sender, m.text, m.count,
                        open = { m.openChat(this@HomeActivity) },
                        dismiss = { ChatNotifications.dismiss(listOf(m.key)) },
                    )
                }
                val briefingRows = if (!settings.briefing) emptyList() else briefing?.items.orEmpty()
                    .filter { it.key !in hidden }
                    .map { item ->
                        CloudMessage(
                            item.title, null, item.detail, 1,
                            open = { openBriefingItem(item) },
                            dismiss = { hiddenBriefing.update { it + item.key } },
                            kind = CloudKind.Briefing,
                        )
                    }
                chatRows + briefingRows
            }
            // Pressing news, said unasked once per item, only when it can't startle anyone.
            LaunchedEffect(briefing?.builtAtMs, settings.briefing, settings.briefingSpeakUrgent) {
                val b = briefing ?: return@LaunchedEffect
                if (!settings.briefing || !settings.briefingSpeakUrgent || b.urgentKeys.isEmpty()) return@LaunchedEffect
                // Waits for Home to be in front; one try per new briefing.
                this@HomeActivity.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                val may = SpeakGuard.mayAnnounce(
                    hour = LocalTime.now().hour,
                    ringerNormal = ringerNormal(),
                    interruptionsAll = interruptionsAll(),
                    musicPlaying = playingNow.value,
                    screenRecorded = screenRecorded.value,
                    homeVisible = true,
                )
                if (may) {
                    briefingStore.markSpoken(b.urgentKeys)
                    voice.speak(b.urgentSpoken)
                }
            }

            LaunchedEffect(Unit) {
                homePresses.drop(1).collect {
                    drawer.close()
                    settingsPage.close()
                    widgetPickerOpen = false
                }
            }

            LaunchedEffect(settings.intensity) { previewIntensity = null }
            val framings by viewModel.wallpaperFramings.collectAsStateWithLifecycle()
            BackdropLoader(env, remember(settings.wallpaper, framings) { viewModel.backdropSource(settings.wallpaper) })
            GlassEffectsController(env, previewIntensity ?: settings.intensity)
            LaunchedEffect(settings.tiltEnabled) {
                env.effects = env.effects.copy(tilt = settings.tiltEnabled)
                if (!settings.tiltEnabled) env.tiltParallax = Offset.Zero
            }
            val icons = remember(settings.iconStyle, settings.showLabels, settings.iconTint) {
                IconAppearance(settings.iconStyle, settings.showLabels, settings.iconTint)
            }

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
                                onDoubleTapEmpty = {
                                    if (settings.doubleTapLock && !ScreenLock.lockNow()) lockPrompt = true
                                },
                                backgroundBlur = { maxOf(drawer.fraction, settingsPage.fraction) },
                                resetSignal = homePressCount,
                                widgetContent = { widget, size, modifier -> WidgetHost(widgets, widget, size, modifier) },
                                companion = { anchor, alpha ->
                                    // Her surface draws above every view: hide her under app-level sheets.
                                    if (settings.mascotEnabled && !lockPrompt && !widgetPickerOpen) {
                                        MascotLayer(anchor, outfit = Outfit.valueOf(settings.mascotOutfit.name), musicPlaying = { musicPlaying }, charging = { charging }, alpha = alpha, messages = { cloudMessages }, pillEnabled = { settings.mascotPill },
                                            speaking = { speaking },
                                            onCloudOpened = {
                                                val b = briefing
                                                if (settings.briefing && b != null && cloudMessages.any { it.kind == CloudKind.Briefing }) voice.speak(b.spoken)
                                            },
                                        )
                                    }
                                },
                            )
                        } else {
                            WallpaperLayer()
                        }
                        AppDrawer(drawer, drawerViewModel)
                        WidgetPicker(widgetPickerOpen, onDismiss = { widgetPickerOpen = false }) { type, size ->
                            viewModel.addWidget(type, size)
                        }
                        LockPrompt(lockPrompt, onDismiss = { lockPrompt = false })
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

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val state = windowManager.addScreenRecordingCallback(mainExecutor, recordingCallback)
            screenRecorded.value = state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE
        }
    }

    override fun onStop() {
        voice.release()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            windowManager.removeScreenRecordingCallback(recordingCallback)
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.value++
    }

    override fun onResume() {
        super.onResume()
        // Permission or notification-access changes made in Settings.
        widgets.permissions.recheck()
        lifecycleScope.launch {
            if (viewModel.settings.first { it != null }?.briefing == true) {
                briefingBridge.refreshIfStale(briefingStore.briefing.first()?.builtAtMs)
            }
        }
    }
}
