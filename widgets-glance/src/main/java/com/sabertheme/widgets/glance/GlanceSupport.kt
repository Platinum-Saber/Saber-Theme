package com.sabertheme.widgets.glance

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import com.sabertheme.core.designsystem.theme.DarkSaberColors
import com.sabertheme.core.designsystem.theme.LightSaberColors
import com.sabertheme.core.widgetdata.WidgetSources
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Glance widgets are not Hilt-injected; they reach the shared sources through this. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDataEntryPoint {
    fun sources(): WidgetSources
}

internal fun Context.widgetSources(): WidgetSources =
    EntryPointAccessors.fromApplication(applicationContext, WidgetDataEntryPoint::class.java).sources()

/**
 * Glance tokens: day/night pairs from the Saber colour tokens, so the host's
 * dark mode picks them. XML-only pieces (card drawable, TextClock layouts)
 * use the mirrored colours in res/values and res/values-night.
 */
internal object GlanceTokens {
    val textPrimary = ColorProvider(day = LightSaberColors.textPrimary, night = DarkSaberColors.textPrimary)
    val textSecondary = ColorProvider(day = LightSaberColors.textSecondary, night = DarkSaberColors.textSecondary)
    val accent = ColorProvider(day = LightSaberColors.accent, night = DarkSaberColors.accent)

    fun label(size: Int = 13, color: ColorProvider = textSecondary) =
        TextStyle(color = color, fontSize = size.sp, fontWeight = FontWeight.Medium)

    // Responsive breakpoints from the 70n - 30 dp cell rule.
    val S2x2 = DpSize(110.dp, 110.dp)
    val S4x1 = DpSize(250.dp, 40.dp)
    val S4x2 = DpSize(250.dp, 110.dp)
}

/** Which layout a size gets: 4x2, 4x1 (short), or 2x2 (narrow). */
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

@Composable
internal fun GlanceGlyph(@DrawableRes res: Int, size: Dp, color: ColorProvider = GlanceTokens.textSecondary) {
    Image(ImageProvider(res), contentDescription = null, modifier = GlanceModifier.size(size), colorFilter = ColorFilter.tint(color))
}
