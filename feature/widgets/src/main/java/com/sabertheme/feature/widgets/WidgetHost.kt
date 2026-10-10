package com.sabertheme.feature.widgets

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.model.HomeItem
import com.sabertheme.core.model.WidgetSize
import com.sabertheme.core.model.WidgetType
import com.sabertheme.core.widgetdata.MediaListenerService
import com.sabertheme.core.widgetdata.WidgetPermission
import com.sabertheme.core.widgetdata.WidgetPermissions
import com.sabertheme.core.widgetdata.WidgetSources
import com.sabertheme.core.widgetdata.WidgetState
import java.time.LocalDateTime

/** A live native widget of [size] cells; fills the bounds the home grid measured. */
@Composable
fun WidgetHost(sources: WidgetSources, widget: HomeItem.Widget, size: WidgetSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val allow = rememberAllowRequest(sources.permissions)
    when (widget.type) {
        WidgetType.Clock -> {
            val clock by sources.clock.state.collectAsStateWithLifecycle(WidgetState.Loading)
            val alarm by sources.alarm.state.collectAsStateWithLifecycle(WidgetState.Loading)
            StateFrame(clock, size, modifier, allow, onClick = { context.startSafely(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }) {
                ClockContent(size, it, alarm.dataOrNull())
            }
        }
        WidgetType.Alarm -> {
            val alarm by sources.alarm.state.collectAsStateWithLifecycle(WidgetState.Loading)
            val clock by sources.clock.state.collectAsStateWithLifecycle(WidgetState.Loading)
            StateFrame(alarm, size, modifier, allow, onClick = { context.startSafely(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }) {
                val now = clock.dataOrNull()
                AlarmContent(size, it, now?.now ?: LocalDateTime.now(), now?.is24Hour ?: true)
            }
        }
        WidgetType.Weather -> {
            val weather by sources.weather.state.collectAsStateWithLifecycle(WidgetState.Loading)
            StateFrame(weather, size, modifier, allow, onClick = null) { WeatherContent(size, it) }
        }
        WidgetType.Calendar -> {
            val calendar by sources.calendar.state.collectAsStateWithLifecycle(WidgetState.Loading)
            val clock by sources.clock.state.collectAsStateWithLifecycle(WidgetState.Loading)
            StateFrame(calendar, size, modifier, allow, onClick = { context.openCalendar() }) {
                CalendarContent(size, it, clock.dataOrNull()?.is24Hour ?: true)
            }
        }
        WidgetType.Battery -> {
            val battery by sources.battery.state.collectAsStateWithLifecycle(WidgetState.Loading)
            StateFrame(battery, size, modifier, allow, onClick = { context.startSafely(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }) {
                BatteryContent(size, it)
            }
        }
        WidgetType.Media -> {
            val media by sources.media.state.collectAsStateWithLifecycle(WidgetState.Loading)
            StateFrame(media, size, modifier, allow, onClick = null) {
                MediaContent(
                    size, it,
                    onPrevious = sources.media::previous,
                    onPlayPause = sources.media::playPause,
                    onNext = sources.media::next,
                    onSelect = sources.media::select,
                    onCycle = sources.media::cycle,
                )
            }
        }
    }
}

@Composable
private fun <T> StateFrame(
    state: WidgetState<T>,
    size: WidgetSize,
    modifier: Modifier,
    allow: (WidgetPermission) -> Unit,
    onClick: (() -> Unit)?,
    content: @Composable BoxScope.(T) -> Unit,
) {
    WidgetFrame(size, modifier, onClick = onClick.takeIf { state is WidgetState.Ready }) {
        when (state) {
            WidgetState.Loading -> Unit
            is WidgetState.Ready -> content(state.data)
            is WidgetState.NeedsPermission -> PermissionPrompt(size, state.permission) { allow(state.permission) }
            is WidgetState.Error -> Label(state.message, Saber.type.labelMedium, Saber.colors.textSecondary, Modifier.align(Alignment.Center))
        }
    }
}

private fun <T> WidgetState<T>.dataOrNull(): T? = (this as? WidgetState.Ready)?.data

/**
 * Runtime permissions go through the system dialog; once the user has denied
 * twice (no rationale left) app info opens instead. Notification access opens
 * its settings page. [WidgetPermissions.recheck] runs on result and on resume.
 */
@Composable
private fun rememberAllowRequest(permissions: WidgetPermissions): (WidgetPermission) -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var asked by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissions.recheck()
        val permission = asked
        if (!granted && permission != null && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            context.startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }
    }
    return remember(launcher, context) {
        { permission ->
            when (permission) {
                WidgetPermission.Calendar -> Manifest.permission.READ_CALENDAR
                WidgetPermission.Location -> Manifest.permission.ACCESS_COARSE_LOCATION
                WidgetPermission.NotificationListener -> null
            }?.let {
                asked = it
                launcher.launch(it)
            } ?: context.startSafely(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, MediaListenerService::class.java).flattenToString(),
                ),
            )
        }
    }
}

private fun Context.openCalendar() = startSafely(
    Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(System.currentTimeMillis().toString()).build()),
)

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // Nothing handles it on this device; the widget just stays put.
    } catch (e: SecurityException) {
        // The handler demands a permission we don't hold; never crash home over a tap.
    }
}
