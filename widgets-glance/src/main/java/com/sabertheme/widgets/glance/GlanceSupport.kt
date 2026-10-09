package com.sabertheme.widgets.glance

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sabertheme.core.designsystem.theme.DarkSaberColors
import com.sabertheme.core.designsystem.theme.LightSaberColors
import com.sabertheme.core.icons.UiGlyph
import com.sabertheme.core.widgetdata.WeatherCondition
import com.sabertheme.core.widgetdata.WidgetPermission
import com.sabertheme.core.widgetdata.WidgetSources
import com.sabertheme.core.widgetdata.WidgetState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.roundToInt

/** Glance widgets are not Hilt-injected; they reach the shared sources through this. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDataEntryPoint {
    fun sources(): WidgetSources
}

internal fun Context.widgetSources(): WidgetSources =
    EntryPointAccessors.fromApplication(applicationContext, WidgetDataEntryPoint::class.java).sources()

/**
 * Every Saber widget receiver keeps the background refresh scheduled: any
 * broadcast to it (enabled, update, resize) re-enqueues the unique work,
 * which is a no-op when it is already scheduled.
 */
abstract class SaberGlanceReceiver : GlanceAppWidgetReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        WidgetRefreshWorker.schedule(context)
    }
}

/**
 * Glance tokens: day/night pairs from the Saber colour tokens, so the host's
 * dark mode picks them. XML-only pieces (card drawable, TextClock layouts)
 * use the mirrored colours in res/values and res/values-night. Type sizes
 * follow `Saber.type` (display 44, title 22/17, label 13, caption 11).
 */
internal object GlanceTokens {
    val textPrimary = ColorProvider(day = LightSaberColors.textPrimary, night = DarkSaberColors.textPrimary)
    val textSecondary = ColorProvider(day = LightSaberColors.textSecondary, night = DarkSaberColors.textSecondary)
    val textTertiary = ColorProvider(day = LightSaberColors.textTertiary, night = DarkSaberColors.textTertiary)
    val accent = ColorProvider(day = LightSaberColors.accent, night = DarkSaberColors.accent)

    fun label(size: Int = 13, color: ColorProvider = textSecondary) =
        TextStyle(color = color, fontSize = size.sp, fontWeight = FontWeight.Medium)

    fun display(size: Int = 44, color: ColorProvider = textPrimary) =
        TextStyle(color = color, fontSize = size.sp, fontFamily = FontFamily("sans-serif-light"))

    fun title(size: Int = 22, color: ColorProvider = textPrimary) =
        TextStyle(color = color, fontSize = size.sp, fontWeight = FontWeight.Medium)

    // Responsive breakpoints from the 70n - 30 dp cell rule.
    val S2x1 = DpSize(110.dp, 40.dp)
    val S2x2 = DpSize(110.dp, 110.dp)
    val S4x1 = DpSize(250.dp, 40.dp)
    val S4x2 = DpSize(250.dp, 110.dp)
}

/** Which layout a size gets: wide (4x2), one row tall (4x1 / 2x1), or square (2x2). */
internal enum class GlanceLayout { Wide, Row, Square }

@Composable
internal fun currentLayout(): GlanceLayout {
    val size = LocalSize.current
    return when {
        size.height < 100.dp -> GlanceLayout.Row
        size.width < 220.dp -> GlanceLayout.Square
        else -> GlanceLayout.Wide
    }
}

/** Translucent card with a 1 dp border (Figma Render=Glance), padded 16 x 14. */
@Composable
internal fun GlanceFrame(wide: Boolean, modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(ImageProvider(if (wide) R.drawable.glance_frame_28 else R.drawable.glance_frame_24))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}

/**
 * The card for [state]: [content] when Ready (tapping runs [onClick]), a
 * "Tap to allow" prompt that opens [GlancePermissionActivity], an error
 * line, or an empty card while loading.
 */
@Composable
internal fun <T> GlanceStateFrame(
    context: Context,
    state: WidgetState<T>,
    wide: Boolean,
    onClick: Action?,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        is WidgetState.Ready -> GlanceFrame(wide, onClick?.let { GlanceModifier.clickable(it) } ?: GlanceModifier) { content(state.data) }
        is WidgetState.NeedsPermission -> GlanceFrame(wide, GlanceModifier.clickable(allowAction(context, state.permission))) {
            Centered {
                Text(permissionMessage(state.permission), style = GlanceTokens.label().copy(textAlign = TextAlign.Center), maxLines = 2)
                Spacer(GlanceModifier.height(6.dp))
                Text("Tap to allow", style = GlanceTokens.label(color = GlanceTokens.accent))
            }
        }
        is WidgetState.Error -> GlanceFrame(wide) {
            Centered { Text(state.message, style = GlanceTokens.label().copy(textAlign = TextAlign.Center), maxLines = 2) }
        }
        WidgetState.Loading -> GlanceFrame(wide) {}
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

private fun permissionMessage(permission: WidgetPermission) = when (permission) {
    WidgetPermission.Calendar -> "Show your next events"
    WidgetPermission.Location -> "Weather for your area"
    WidgetPermission.NotificationListener -> "Control what's playing"
}

private fun allowAction(context: Context, permission: WidgetPermission): Action =
    actionStartActivity(
        Intent(context, GlancePermissionActivity::class.java)
            .putExtra(GlancePermissionActivity.EXTRA_PERMISSION, permission.name),
    )

/**
 * Opens [intent] from a widget tap. Glance tags click intents with a
 * `glance-action:` URI, which breaks resolution of data-less intents
 * (SHOW_ALARMS, POWER_USAGE_SUMMARY: START_INTENT_NOT_RESOLVED), so the
 * target activity is resolved here and the intent made explicit.
 */
internal fun Context.openAction(intent: Intent): Action {
    val target = packageManager.resolveActivity(intent, 0)?.activityInfo
    if (target != null) intent.setClassName(target.packageName, target.name)
    return actionStartActivity(intent)
}

@Composable
internal fun GlanceGlyph(@DrawableRes res: Int, size: Dp, color: ColorProvider = GlanceTokens.textSecondary) {
    Image(ImageProvider(res), contentDescription = null, modifier = GlanceModifier.size(size), colorFilter = ColorFilter.tint(color))
}

/** Same mapping as the in-launcher widget (`WidgetViews.kt`); kept here so widget data stays UI-free. */
internal val WeatherCondition.glyph: UiGlyph
    get() = when (this) {
        WeatherCondition.Clear -> UiGlyph.SUN
        WeatherCondition.PartlyCloudy, WeatherCondition.Cloudy, WeatherCondition.Fog -> UiGlyph.CLOUD_SUN
        WeatherCondition.Drizzle, WeatherCondition.Rain, WeatherCondition.Snow, WeatherCondition.Thunder -> UiGlyph.RAIN
    }

/**
 * Progress ring as a bitmap (RemoteViews has no determinate ring): tertiary
 * track at 40 %, accent arc from 12 o'clock, inner radius 0.84 (Figma Ring).
 */
internal fun ringImage(context: Context, size: Dp, progress: Float): ImageProvider {
    val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val colors = if (night) DarkSaberColors else LightSaberColors
    val px = (size.value * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val stroke = px * 0.08f
    val oval = RectF(stroke / 2, stroke / 2, px - stroke / 2, px - stroke / 2)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
    }
    Canvas(bitmap).apply {
        paint.color = colors.textTertiary.copy(alpha = 0.4f).toArgb()
        drawArc(oval, 0f, 360f, false, paint)
        paint.color = colors.accent.toArgb()
        paint.strokeCap = Paint.Cap.ROUND
        drawArc(oval, -90f, 360f * progress.coerceIn(0f, 1f), false, paint)
    }
    return ImageProvider(bitmap)
}
