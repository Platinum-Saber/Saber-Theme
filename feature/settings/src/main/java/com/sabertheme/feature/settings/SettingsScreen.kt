package com.sabertheme.feature.settings

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabertheme.core.designsystem.component.GlassSlider
import com.sabertheme.core.designsystem.component.GlassSwitch
import com.sabertheme.core.designsystem.glass.GlassMotion
import com.sabertheme.core.designsystem.glass.GlassShape
import com.sabertheme.core.designsystem.glass.GlassSurface
import com.sabertheme.core.designsystem.glass.LocalGlassEnvironment
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.Radius
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.icons.UiGlyph
import com.sabertheme.core.model.HomeLayout
import com.sabertheme.core.model.IconStyle
import com.sabertheme.core.model.MascotOutfit

private val SIDE = 18.dp

/** Open/closed state, hoisted so home can blur behind the page. */
@Stable
class SettingsState {
    var visible by mutableStateOf(false)
        private set
    internal val progress = Animatable(0f)

    /** 0 closed .. 1 open; read it in a draw/layer phase. */
    val fraction: Float get() = progress.value

    fun open() {
        visible = true
    }

    fun close() {
        visible = false
    }
}

@Composable
fun rememberSettingsState() = remember { SettingsState() }

/**
 * Full-screen Thick glass settings (Figma "Settings"): Wallpaper, Glass,
 * Icons, Home and About groups. [onIntensityPreview] lets the glass follow
 * the slider before the value is saved; [debugTools] (Glass Lab) shows
 * under About when the app passes it.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    viewModel: SettingsViewModel,
    onIntensityPreview: (Float) -> Unit,
    debugTools: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val env = LocalGlassEnvironment.current
    LaunchedEffect(state.visible) {
        state.progress.animateTo(if (state.visible) 1f else 0f, GlassMotion.sheet(env.reducedMotion))
    }
    val shown by remember { derivedStateOf { state.visible || state.progress.value > 0f } }
    if (!shown) return
    BackHandler(enabled = state.visible) { state.close() }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val colors = Saber.colors

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = state.progress.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.2f))
                .pointerInput(Unit) { detectTapGestures { state.close() } },
        )
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp)) {
            // Extends a corner radius below the screen so only the top corners show.
            val overhang = Radius.xl
            GlassSurface(
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .height(maxHeight + overhang)
                    .graphicsLayer { translationY = (1f - state.progress.value) * size.height },
                shape = GlassShape.Rounded(Radius.xl),
                material = GlassMaterial.Thick,
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = overhang)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = SIDE)
                        .padding(top = 14.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText("Saber", Modifier.weight(1f), style = Saber.type.displayLarge.copy(color = colors.textPrimary))
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(colors.textPrimary.copy(alpha = 0.1f))
                                .semantics { contentDescription = "Close settings" }
                                .clickable { state.close() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(painterResource(UiGlyph.CLOSE.drawable), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(colors.glyph))
                        }
                    }

                    Group("Wallpaper") {
                        WallpaperPicker(settings, photos, importing, viewModel)
                    }

                    Group("Glass") {
                        SettingRow("Glass intensity") {
                            GlassSlider(
                                value = settings.intensity,
                                modifier = Modifier.width(140.dp),
                                onValueChange = onIntensityPreview,
                                onValueChangeFinished = viewModel::setIntensity,
                            )
                        }
                        Divider()
                        SettingRow("Tilt effects", "Light and wallpaper follow the phone") {
                            GlassSwitch(settings.tiltEnabled, viewModel::setTiltEnabled)
                        }
                    }

                    Group("Icons") {
                        SettingRow("Icon style") {
                            Segmented(
                                options = listOf(IconStyle.Tile to "Glass tile", IconStyle.Bare to "Bare"),
                                selected = settings.iconStyle,
                                onSelect = viewModel::setIconStyle,
                            )
                        }
                        Divider()
                        SettingRow("Show labels") {
                            GlassSwitch(settings.showLabels, viewModel::setShowLabels)
                        }
                    }

                    Group("Mascot") {
                        SettingRow("Show Saber") {
                            GlassSwitch(settings.mascotEnabled, viewModel::setMascotEnabled)
                        }
                        Divider()
                        SettingRow("Outfit") {
                            Segmented(
                                options = listOf(MascotOutfit.Armor to "Armour", MascotOutfit.Winter to "Winter", MascotOutfit.Casual to "Casual"),
                                selected = settings.mascotOutfit,
                                onSelect = viewModel::setMascotOutfit,
                            )
                        }
                    }

                    Group("Home screen") {
                        DefaultHomeRow(state.visible)
                        Divider()
                        SettingRow("Grid") { Value("${HomeLayout.COLUMNS} × ${HomeLayout.ROWS}") }
                    }

                    Group("About") {
                        SettingRow("Version") { Value(appVersion(LocalContext.current)) }
                    }
                    if (debugTools != null) Column(content = debugTools)
                }
            }
        }
        editing?.let { WallpaperEditor(it, viewModel) }
    }
}

/** "Default home app": Saber when it holds ROLE_HOME, else a request; tapping when held opens the system chooser. */
@Composable
private fun DefaultHomeRow(visible: Boolean) {
    val context = LocalContext.current
    val roles = remember { context.getSystemService(RoleManager::class.java) }
    var isDefault by remember { mutableStateOf(roles.isRoleHeld(RoleManager.ROLE_HOME)) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = roles.isRoleHeld(RoleManager.ROLE_HOME)
    }
    LaunchedEffect(visible) { isDefault = roles.isRoleHeld(RoleManager.ROLE_HOME) }
    SettingRow(
        "Default home app",
        if (isDefault) "Saber is your home screen" else "Make Saber your home screen",
        onClick = {
            if (!isDefault && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                request.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
            } else {
                context.startSafely(Intent(Settings.ACTION_HOME_SETTINGS))
            }
        },
    ) {
        Value(if (isDefault) "Saber" else "Set")
        Image(painterResource(UiGlyph.CHEVRON.drawable), null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(Saber.colors.textTertiary))
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    BasicText(title, Modifier.padding(top = 8.dp), style = Saber.type.labelMedium.copy(color = Saber.colors.textSecondary))
    GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.Rounded(22.dp), material = GlassMaterial.Regular) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingRow(
    label: String,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(label, style = Saber.type.body.copy(color = Saber.colors.textPrimary))
            if (detail != null) BasicText(detail, style = Saber.type.captionIcon.copy(color = Saber.colors.textSecondary))
        }
        trailing()
    }
}

@Composable
private fun Value(text: String) {
    BasicText(text, style = Saber.type.body.copy(color = Saber.colors.textSecondary))
}

@Composable
private fun Divider() {
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Saber.colors.glassBorder))
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = Saber.colors
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(colors.textPrimary.copy(alpha = 0.1f)).padding(2.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            BasicText(
                label,
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) colors.accent else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = Saber.type.labelMedium.copy(color = if (on) Color.White else colors.textPrimary),
            )
        }
    }
}

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // No chooser on this device; nothing to do.
    }
}
