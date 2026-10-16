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
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.sabertheme.core.designsystem.component.GlassSwitch
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.model.GlassSettings
import java.util.Locale

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
    SettingRow("Speak urgent items", "Never 22:00–07:00, on silent, in Do Not Disturb or over music") {
        GlassSwitch(settings.briefingSpeakUrgent, viewModel::setBriefingSpeakUrgent)
    }
    Divider()
    MoodleRows(viewModel)
    Divider()
    CallLogRow(remember(visible, resumed) { viewModel.callLogGranted() })
    Divider()
    val listening = remember(visible, resumed) { context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context) }
    SettingRow(
        "Gmail",
        if (listening) "Reads new mail notifications; previews stay on this phone" else "Needs notification access",
        onClick = if (listening) null else ({ context.openListenerSettings() }),
    ) { Value(if (listening) "On" else "Allow") }
    Divider()
    val size = remember(visible, resumed) { viewModel.modelSize() }
    SettingRow(
        "On-device model",
        if (size != null) {
            "Gemma 4 E2B · ${String.format(Locale.ROOT, "%.1f", size / 1e9)} GB · labels mail on the phone"
        } else {
            "Missing: copy gemma-4-E2B-it.litertlm to ${viewModel.modelPath.substringBeforeLast('/')}. Simple rules label mail until then."
        },
    ) { Value(if (size != null) "Ready" else "Rules") }
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
private fun CallLogRow(granted: Boolean) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    var allowed by remember(granted) { mutableStateOf(granted) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        allowed = ok
        asked = true
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
