package com.sabertheme.feature.widgets

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.glass.GlassShape
import com.sabertheme.core.designsystem.glass.GlassSurface
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.Radius
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.icons.GlyphImages
import com.sabertheme.core.icons.UiGlyph
import com.sabertheme.core.model.WidgetSize
import com.sabertheme.core.widgetdata.AlarmData
import com.sabertheme.core.widgetdata.BatteryData
import com.sabertheme.core.widgetdata.CalendarData
import com.sabertheme.core.widgetdata.CalendarEvent
import com.sabertheme.core.widgetdata.ClockData
import com.sabertheme.core.widgetdata.HourForecast
import com.sabertheme.core.widgetdata.MediaApp
import com.sabertheme.core.widgetdata.MediaState
import com.sabertheme.core.widgetdata.WeatherCondition
import com.sabertheme.core.widgetdata.WeatherData
import com.sabertheme.core.widgetdata.WidgetFormat
import com.sabertheme.core.widgetdata.WidgetPermission
import androidx.core.graphics.drawable.toBitmap
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

// Glass content for each widget type and size, per the Figma "Widgets" board.
// These take plain data so the picker can render them with samples.

/** Regular glass card, 24 radius at 2 columns and 28 at 4, padded 16 x 14. */
@Composable
internal fun WidgetFrame(
    size: WidgetSize,
    modifier: Modifier,
    onClick: (() -> Unit)?,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        modifier,
        shape = GlassShape.Rounded(if (size.spanX <= 2) Radius.md else Radius.lg),
        material = GlassMaterial.Regular,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp), content = content)
    }
}

@Composable
internal fun ClockContent(size: WidgetSize, clock: ClockData, alarm: AlarmData?) {
    val colors = Saber.colors
    val type = Saber.type
    val time = clock.now.toLocalTime()
    val alarmText = alarm?.triggerAt?.let { WidgetFormat.alarm(it.toLocal(), clock.now, clock.is24Hour) }
    when {
        size == WidgetSize.S4x2 -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Label(WidgetFormat.time(time, clock.is24Hour), type.displayClock, colors.textPrimary)
            Label(WidgetFormat.longDate(clock.now.toLocalDate()), type.titleMedium, colors.textSecondary)
            if (alarmText != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Glyph(UiGlyph.ALARM, 14.dp, colors.textSecondary)
                    Label(alarmText, type.labelMedium, colors.textSecondary)
                }
            }
        }
        size.spanX >= 4 -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Label(WidgetFormat.time(time, clock.is24Hour), type.displayLarge, colors.textPrimary)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(WidgetFormat.longDate(clock.now.toLocalDate()), type.labelMedium, colors.textSecondary)
                if (alarmText != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Glyph(UiGlyph.ALARM, 12.dp, colors.textTertiary)
                        Label(alarmText, type.captionIcon, colors.textTertiary)
                    }
                }
            }
        }
        else -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Label(WidgetFormat.hour(time, clock.is24Hour), type.displayLarge, colors.textPrimary)
            Label(WidgetFormat.minute(time), type.displayLarge, colors.accent)
            Spacer(Modifier.height(4.dp))
            Label(WidgetFormat.shortDate(clock.now.toLocalDate()), type.labelMedium, colors.textSecondary)
        }
    }
}

@Composable
internal fun WeatherContent(size: WidgetSize, weather: WeatherData) {
    val colors = Saber.colors
    val type = Saber.type
    val highLow = WidgetFormat.highLow(weather.high, weather.low)
    when {
        size.spanX >= 4 && size.spanY == 1 -> Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Glyph(weather.condition.glyph, 28.dp, colors.glyph)
            Label("${weather.temperature}°", type.titleLarge, colors.textPrimary)
            Column(Modifier.weight(1f)) {
                Label(weather.condition.label, type.labelMedium, colors.textPrimary)
                Label(highLow, type.captionIcon, colors.textSecondary)
            }
            weather.hourly.take(3).forEach { HourColumn(it) }
        }
        size.spanX >= 4 -> Column(Modifier.fillMaxSize()) {
            WeatherHeader(weather)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Label("${weather.temperature}°", type.displayLarge, colors.textPrimary)
                Column(Modifier.padding(bottom = 4.dp)) {
                    Label(weather.condition.label, type.labelMedium, colors.textPrimary)
                    Label(highLow, type.captionIcon, colors.textSecondary)
                }
                Spacer(Modifier.weight(1f))
                weather.hourly.take(4).forEach { HourColumn(it) }
            }
        }
        else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WeatherHeader(weather)
            Spacer(Modifier.weight(1f))
            Label("${weather.temperature}°", type.displayLarge, colors.textPrimary)
            Label(weather.condition.label, type.labelMedium, colors.textPrimary)
            Label(highLow, type.captionIcon, colors.textSecondary)
        }
    }
}

@Composable
private fun WeatherHeader(weather: WeatherData) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Glyph(weather.condition.glyph, 28.dp, Saber.colors.glyph)
        Spacer(Modifier.weight(1f))
        Label("Now", Saber.type.captionIcon, Saber.colors.textTertiary)
    }
}

@Composable
private fun HourColumn(hour: HourForecast) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Label("%02d".format(Locale.ROOT, hour.hour), Saber.type.captionIcon, Saber.colors.textTertiary)
        Glyph(hour.condition.glyph, 16.dp, Saber.colors.textSecondary)
        Label("${hour.temperature}°", Saber.type.captionIcon, Saber.colors.textPrimary)
    }
}

internal val WeatherCondition.glyph: UiGlyph
    get() = when (this) {
        WeatherCondition.Clear -> UiGlyph.SUN
        WeatherCondition.PartlyCloudy, WeatherCondition.Cloudy, WeatherCondition.Fog -> UiGlyph.CLOUD_SUN
        WeatherCondition.Drizzle, WeatherCondition.Rain, WeatherCondition.Snow, WeatherCondition.Thunder -> UiGlyph.RAIN
    }

@Composable
internal fun CalendarContent(size: WidgetSize, calendar: CalendarData, is24Hour: Boolean) {
    val colors = Saber.colors
    val type = Saber.type
    if (size.spanX >= 4) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(calendar.today.dayOfMonth.toString(), type.titleLarge, colors.textPrimary)
                Label(WidgetFormat.weekday(calendar.today), type.labelMedium, colors.accent, Modifier.padding(bottom = 3.dp))
            }
            if (calendar.events.isEmpty()) Label("No upcoming events", type.labelMedium, colors.textSecondary)
            calendar.events.take(3).forEach { EventRow(it, calendar, is24Hour) }
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(WidgetFormat.weekday(calendar.today), type.labelMedium, colors.accent)
            Label(calendar.today.dayOfMonth.toString(), type.displayLarge, colors.textPrimary)
            Spacer(Modifier.weight(1f))
            val first = calendar.events.firstOrNull()
            if (first == null) {
                Label("No upcoming events", type.labelMedium, colors.textSecondary)
            } else {
                EventRow(first, calendar, is24Hour)
                val shownToday = if (!first.start.toLocalDate().isAfter(calendar.today)) 1 else 0
                val more = calendar.todayCount() - shownToday
                if (more > 0) Label("+$more more today", type.captionIcon, colors.textTertiary)
            }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent, calendar: CalendarData, is24Hour: Boolean) {
    val colors = Saber.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val bar = if (event.color != 0) Color(event.color).copy(alpha = 1f) else colors.accent
        Box(Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(1.5.dp)).background(bar))
        Column {
            Label(event.title, Saber.type.labelMedium, colors.textPrimary)
            Label(WidgetFormat.eventTime(event, calendar.today, is24Hour), Saber.type.captionIcon, colors.textSecondary)
        }
    }
}

@Composable
internal fun BatteryContent(size: WidgetSize, battery: BatteryData) {
    val colors = Saber.colors
    val type = Saber.type
    val status = if (battery.charging) "Charging" else "Phone"
    if (size.spanY >= 2) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Ring(battery.percent / 100f, 76.dp) { Glyph(UiGlyph.DEVICE, 24.dp, colors.glyph) }
            Spacer(Modifier.height(4.dp))
            Label("${battery.percent}%", type.titleLarge, colors.textPrimary)
            Label(status, type.captionIcon, colors.textSecondary)
        }
    } else {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Ring(battery.percent / 100f, 42.dp) { Glyph(UiGlyph.DEVICE, 16.dp, colors.glyph) }
            Column {
                Label("${battery.percent}%", type.titleMedium, colors.textPrimary)
                Label(status, type.captionIcon, colors.textSecondary)
            }
        }
    }
}

/** Progress ring: tertiary track at 40 %, accent arc from 12 o'clock (Figma Ring, inner radius 0.84). */
@Composable
private fun Ring(progress: Float, size: Dp, content: @Composable BoxScope.() -> Unit) {
    val track = Saber.colors.textTertiary.copy(alpha = 0.4f)
    val accent = Saber.colors.accent
    Box(
        Modifier.size(size).drawBehind {
            val stroke = this.size.minDimension * 0.08f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(accent, -90f, 360f * progress.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
internal fun AlarmContent(size: WidgetSize, alarm: AlarmData, now: LocalDateTime, is24Hour: Boolean) {
    val colors = Saber.colors
    val type = Saber.type
    val at = alarm.triggerAt?.toLocal()
    val time = at?.let { WidgetFormat.time(it.toLocalTime(), is24Hour) } ?: "No alarm"
    val day = at?.let { WidgetFormat.day(it.toLocalDate(), now.toLocalDate()) } ?: "Tap to set"
    if (size.spanY >= 2) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Glyph(UiGlyph.ALARM, 28.dp, colors.glyph)
            Spacer(Modifier.weight(1f))
            Label(time, if (at != null) type.displayLarge else type.titleLarge, colors.textPrimary)
            Label(day, type.labelMedium, colors.textSecondary)
        }
    } else {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Glyph(UiGlyph.ALARM, 24.dp, colors.glyph)
            Column {
                Label(time, if (at != null) type.titleLarge else type.titleMedium, colors.textPrimary)
                Label(day, type.captionIcon, colors.textSecondary)
            }
        }
    }
}

@Composable
internal fun MediaContent(
    size: WidgetSize,
    state: MediaState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSelect: (String) -> Unit,
    onCycle: () -> Unit,
    onAllowThumbnails: () -> Unit,
) {
    val colors = Saber.colors
    val type = Saber.type
    val media = state.now
    val resume = state.resumeApp
    val title = media?.title ?: resume?.label ?: "Nothing playing"
    val subtitle = when {
        media != null -> listOf(media.artist, media.app).filter(String::isNotBlank).joinToString(" · ")
        resume != null -> "Tap play to resume"
        else -> ""
    }
    val playing = media?.playing == true
    if (size.spanY >= 2) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(media?.art, state.selected, 64.dp, onAllowThumbnails.takeIf { media?.needsVideoPermission == true })
                Column(Modifier.weight(1f)) {
                    Label(title, type.titleMedium, colors.textPrimary)
                    Label(subtitle, type.labelMedium, colors.textSecondary)
                }
                if (state.apps.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                        state.apps.forEach { AppChip(it, it.packageName == state.selected) { onSelect(it.packageName) } }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally)) {
                Control(UiGlyph.PREV, 24.dp, "Previous", onPrevious)
                Control(if (playing) UiGlyph.PAUSE else UiGlyph.PLAY, 28.dp, if (playing) "Pause" else "Play", onPlayPause)
                Control(UiGlyph.NEXT, 24.dp, "Next", onNext)
            }
        }
    } else {
        val nextApp = state.apps.getOrNull((state.apps.indexOfFirst { it.packageName == state.selected } + 1).mod(state.apps.size.coerceAtLeast(1)))
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Artwork(media?.art, state.selected, 48.dp, onAllowThumbnails.takeIf { media?.needsVideoPermission == true })
            // No room for chips: tapping the text switches to the next app.
            val cycle = if (state.apps.size > 1 && nextApp != null) {
                Modifier
                    .semantics { contentDescription = "Switch to ${nextApp.label}" }
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onCycle)
            } else {
                Modifier
            }
            Column(Modifier.weight(1f).then(cycle)) {
                Label(title, type.labelMedium, colors.textPrimary)
                if (subtitle.isNotEmpty()) Label(subtitle, type.captionIcon, colors.textSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Control(UiGlyph.PREV, 20.dp, "Previous", onPrevious)
                Control(if (playing) UiGlyph.PAUSE else UiGlyph.PLAY, 22.dp, if (playing) "Pause" else "Play", onPlayPause)
                Control(UiGlyph.NEXT, 20.dp, "Next", onNext)
            }
        }
    }
}

/** Switcher pill: accent when selected, with a dot while that app is playing. */
@Composable
private fun AppChip(app: MediaApp, selected: Boolean, onClick: () -> Unit) {
    val colors = Saber.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) colors.accent.copy(alpha = 0.85f) else colors.glassTint.copy(alpha = 0.18f))
            .semantics { contentDescription = "Control ${app.label}" }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (app.playing) Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(if (selected) Color.White else colors.accent))
        Label(app.label, Saber.type.captionIcon, if (selected) Color.White else colors.textPrimary)
    }
}

@Composable
private fun Artwork(art: Bitmap?, packageName: String?, size: Dp, onAllow: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(12.dp)
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val image = remember(art, packageName, px) {
        art?.asImageBitmap() ?: packageName?.let { pkg ->
            runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(px, px).asImageBitmap() }.getOrNull()
        }
    }
    // Thumbnails need a permission: the art itself is the "Allow" control.
    val allow = if (onAllow != null) {
        Modifier
            .semantics { contentDescription = "Show video thumbnails" }
            .clickable(onClick = onAllow)
    } else {
        Modifier
    }
    Box(Modifier.size(size).clip(shape).then(allow)) {
        if (image != null) {
            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF5B7CFA), Color(0xFFE2557A)))))
        }
        if (onAllow != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Saber.colors.accent)
                    .padding(3.dp),
            ) {
                Glyph(UiGlyph.WALLPAPER, size / 5, Color.White)
            }
        }
    }
}

/** Glyph with a 14 dp touch margin, no ripple (the glass card owns press feedback). */
@Composable
private fun Control(glyph: UiGlyph, size: Dp, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size + 14.dp)
            .semantics { contentDescription = label }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(glyph, size, Saber.colors.glyph)
    }
}

@Composable
internal fun PermissionPrompt(size: WidgetSize, permission: WidgetPermission, onAllow: () -> Unit) {
    val message = when (permission) {
        WidgetPermission.Calendar -> "Show your next events"
        WidgetPermission.Location -> "Weather for your area"
        WidgetPermission.NotificationListener -> "Control what's playing"
        WidgetPermission.Videos -> "Show video thumbnails"
    }
    if (size.spanY == 1) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(message, Saber.type.labelMedium, Saber.colors.textSecondary, Modifier.weight(1f), maxLines = 2)
            AllowButton(onAllow)
        }
    } else {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Label(message, Saber.type.labelMedium.copy(textAlign = TextAlign.Center), Saber.colors.textSecondary, maxLines = 2)
            AllowButton(onAllow)
        }
    }
}

@Composable
private fun AllowButton(onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Saber.colors.textPrimary.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Label("Allow", Saber.type.labelMedium, Saber.colors.textPrimary)
    }
}

@Composable
internal fun Label(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, maxLines: Int = 1) {
    BasicText(text, modifier, style = style.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun Glyph(glyph: UiGlyph, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val image = remember(glyph, px) { GlyphImages.get(context, glyph.drawable, px) }
    val filter = remember(tint) { ColorFilter.tint(tint) }
    Spacer(modifier.size(size).drawBehind { drawImage(image, colorFilter = filter) })
}

private fun Long.toLocal(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
