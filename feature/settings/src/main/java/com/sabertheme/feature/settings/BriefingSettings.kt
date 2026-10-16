package com.sabertheme.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabertheme.core.briefing.ModelPack
import com.sabertheme.core.briefing.ModelState
import com.sabertheme.core.briefing.sizeLabel
import com.sabertheme.core.designsystem.component.GlassSwitch
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.model.GlassSettings

/** Where Moodle sign-in stands; errors are Moodle's own words. */
sealed interface MoodleStatus {
    data object Idle : MoodleStatus
    data object Connecting : MoodleStatus
    data class Failed(val message: String) : MoodleStatus
}

/**
 * Settings → Briefing: the switch, then (while on) what feeds it — Moodle,
 * missed calls, Gmail via notification access, the on-device model — each
 * showing whether it is ready and how to fix it.
 */
@Composable
internal fun BriefingRows(visible: Boolean, settings: GlassSettings, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    SettingRow("Saber's briefing", "Deadlines, mail, missed calls and events in her cloud; tap it to hear them") {
        GlassSwitch(settings.briefing, viewModel::setBriefing)
    }
    if (!settings.briefing) return

    // Re-read grants on every resume: the user changes them in Android settings.
    val resumed by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    Divider()
    ModelRow(
        "Voice",
        ModelPack.Kokoro,
        viewModel,
        ready = "Emma (Kokoro, on this phone) · calm, like a sage reporting; tap to hear",
        missing = "Phone voice until then; tap to hear",
        onClick = viewModel::previewVoice,
    )
    Divider()
    SettingRow("Speak urgent items", "Never 22:00–07:00, on silent, in Do Not Disturb or over music") {
        GlassSwitch(settings.briefingSpeakUrgent, viewModel::setBriefingSpeakUrgent)
    }
    Divider()
    MoodleRows(viewModel)
    Divider()
    CallLogRow(remember(visible, resumed) { viewModel.callLogGranted() }, viewModel::sourceAllowed)
    Divider()
    val listening = remember(visible, resumed) { context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context) }
    SettingRow(
        "Gmail",
        if (listening) "Reads new mail notifications; previews stay on this phone" else "Needs notification access",
        onClick = if (listening) null else ({ context.openListenerSettings() }),
    ) { Value(if (listening) "On" else "Allow") }
    Divider()
    ModelRow(
        "On-device model",
        ModelPack.Gemma,
        viewModel,
        ready = "Gemma 4 E2B · labels mail on the phone",
        missing = "Simple rules label mail until then",
    )
}

/**
 * A briefing model: Download (Wi-Fi only unless the owner taps through),
 * progress with Cancel, then Delete behind a second tap. A cancelled or
 * dropped download resumes where it stopped.
 */
@Composable
private fun ModelRow(
    label: String,
    pack: ModelPack,
    viewModel: SettingsViewModel,
    ready: String,
    missing: String,
    onClick: (() -> Unit)? = null,
) {
    val state by viewModel.models.getValue(pack).collectAsStateWithLifecycle()
    var confirmDelete by remember(state) { mutableStateOf(false) }
    val total = sizeLabel(pack.bytes)
    when (val s = state) {
        is ModelState.Ready -> SettingRow(label, "$ready · ${sizeLabel(s.bytes)}", onClick) {
            Action(if (confirmDelete) "Delete?" else "Delete") {
                if (confirmDelete) viewModel.deleteModel(pack) else confirmDelete = true
            }
        }
        is ModelState.Missing -> SettingRow(
            label,
            if (s.partBytes > 0) "${sizeLabel(s.partBytes)} of $total downloaded. $missing" else "$total download, on Wi-Fi. $missing",
            onClick,
        ) { Action(if (s.partBytes > 0) "Resume" else "Download") { viewModel.downloadModel(pack) } }
        is ModelState.Waiting -> SettingRow(
            label,
            if (s.wifiOnly) "Waiting for Wi-Fi · tap to use mobile data ($total)" else "Waiting for the network",
            if (s.wifiOnly) ({ viewModel.downloadModel(pack, anyNetwork = true) }) else null,
        ) { Action("Cancel") { viewModel.cancelDownload(pack) } }
        is ModelState.Downloading -> Column {
            SettingRow(label, "Downloading · ${sizeLabel(s.done)} of $total") {
                Action("Cancel") { viewModel.cancelDownload(pack) }
            }
            Progress(s.done.toFloat() / s.total)
        }
        ModelState.Verifying -> SettingRow(label, "Checking the download…") {}
        ModelState.Unpacking -> SettingRow(label, "Unpacking…") {}
        is ModelState.Failed -> SettingRow(label, "${s.message}. $missing", onClick) {
            Action("Retry") { viewModel.downloadModel(pack) }
        }
    }
}

@Composable
private fun Action(text: String, onClick: () -> Unit) {
    BasicText(
        text,
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        style = Saber.type.body.copy(color = Saber.colors.accent),
    )
}

@Composable
private fun Progress(fraction: Float) {
    Box(
        Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Saber.colors.textPrimary.copy(alpha = 0.1f)),
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(3.dp).background(Saber.colors.accent))
    }
}

@Composable
private fun MoodleRows(viewModel: SettingsViewModel) {
    val account by viewModel.moodle.collectAsStateWithLifecycle()
    val status by viewModel.moodleStatus.collectAsStateWithLifecycle()
    val connected = account
    if (connected != null) {
        SettingRow("Moodle", "${connected.username} · ${connected.site.removePrefix("https://")}", onClick = viewModel::disconnectMoodle) {
            Value("Sign out")
        }
        ModuleIndex(viewModel)
        return
    }
    var site by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    // Not saveable: the password never outlives this screen.
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText("Moodle", style = Saber.type.body.copy(color = Saber.colors.textPrimary))
        BasicText(
            "Assignment and quiz deadlines. Only a sign-in token is kept; your password is not stored.",
            style = Saber.type.captionIcon.copy(color = Saber.colors.textSecondary),
        )
        Field(site, { site = it }, "moodle.your-uni.ac.lk", KeyboardType.Uri)
        Field(user, { user = it }, "Username", KeyboardType.Text)
        Field(password, { password = it }, "Password", KeyboardType.Password, secret = true)
        val connecting = status == MoodleStatus.Connecting
        val ready = site.isNotBlank() && user.isNotBlank() && password.isNotEmpty() && !connecting
        BasicText(
            if (connecting) "Signing in…" else "Sign in",
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (ready) Saber.colors.accent else Saber.colors.textPrimary.copy(alpha = 0.1f))
                .clickable(enabled = ready) {
                    viewModel.connectMoodle(site, user, password)
                    password = ""
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            style = Saber.type.labelMedium.copy(color = if (ready) Color.White else Saber.colors.textSecondary),
        )
        (status as? MoodleStatus.Failed)?.let {
            BasicText(it.message, style = Saber.type.captionIcon.copy(color = Saber.colors.accent))
        }
    }
}

/** Which module each code is; she says the name, the cloud shows the code beside the date. */
@Composable
private fun ModuleIndex(viewModel: SettingsViewModel) {
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    // Collapsed by default: the index runs to 50+ rows and buries the rest of Settings.
    var expanded by rememberSaveable { mutableStateOf(false) }
    Divider()
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = courses.isNotEmpty()) { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Modules", Modifier.weight(1f), style = Saber.type.body.copy(color = Saber.colors.textPrimary))
            if (courses.isNotEmpty()) {
                BasicText(
                    if (expanded) "Hide" else "${courses.size} · Show",
                    style = Saber.type.captionIcon.copy(color = Saber.colors.textSecondary),
                )
            }
        }
        if (courses.isEmpty()) {
            BasicText("Read from Moodle on the next briefing run", style = Saber.type.captionIcon.copy(color = Saber.colors.textSecondary))
        }
        if (expanded) courses.forEach { course ->
            BasicText(
                "${course.code}  ·  ${course.name}",
                style = Saber.type.captionIcon.copy(color = Saber.colors.textSecondary),
            )
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, type: KeyboardType, secret: Boolean = false) {
    val colors = Saber.colors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.textPrimary.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) BasicText(hint, style = Saber.type.body.copy(color = colors.textTertiary))
        BasicTextField(
            value,
            onChange,
            Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = Saber.type.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        )
    }
}

@Composable
private fun CallLogRow(granted: Boolean, onAllowed: () -> Unit) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    var allowed by remember(granted) { mutableStateOf(granted) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        allowed = ok
        asked = true
        if (ok) onAllowed()
    }
    SettingRow(
        "Missed calls",
        if (allowed) "Reads the call log for calls you missed" else "Needs call log access",
        onClick = if (allowed) null else ({
            // Denied twice: Android won't ask again, so open the app's page.
            if (asked) context.openAppDetails() else request.launch(Manifest.permission.READ_CALL_LOG)
        }),
    ) { Value(if (allowed) "On" else "Allow") }
}

private fun Context.openListenerSettings() {
    val component = ComponentName(packageName, "com.sabertheme.core.widgetdata.MediaListenerService")
    startOrIgnore(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString()),
    )
}

private fun Context.openAppDetails() =
    startOrIgnore(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))

private fun Context.startOrIgnore(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // Nothing to open on this device.
    }
}
