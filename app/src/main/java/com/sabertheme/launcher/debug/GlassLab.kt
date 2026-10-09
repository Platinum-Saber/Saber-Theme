package com.sabertheme.launcher.debug

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.metrics.performance.JankStats
import com.sabertheme.core.designsystem.glass.GlassEffects
import com.sabertheme.core.designsystem.glass.GlassEnvironment
import com.sabertheme.core.designsystem.glass.GlassShape
import com.sabertheme.core.designsystem.glass.GlassSurface
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.Saber
import com.sabertheme.core.designsystem.theme.Space
import kotlinx.coroutines.delay

/** Debug-only section of the home options sheet: toggle each effect, show frame times. */
@Composable
fun GlassLab(env: GlassEnvironment, frameOverlay: Boolean, onFrameOverlay: (Boolean) -> Unit) {
    val colors = Saber.colors
    Spacer(Modifier.height(Space.s5))
    BasicText("Glass Lab", Modifier.padding(bottom = Space.s2), style = Saber.type.titleMedium.copy(color = colors.textPrimary))
    val e = env.effects
    Toggle("Refraction", e.refraction) { env.effects = e.copy(refraction = it) }
    Toggle("Tilt light + parallax", e.tilt) { env.effects = e.copy(tilt = it) }
    Toggle("Press bloom", e.press) { env.effects = e.copy(press = it) }
    Toggle("Adaptive tint", e.adaptiveTint) { env.effects = e.copy(adaptiveTint = it) }
    Toggle("Frame-time overlay", frameOverlay, onFrameOverlay)
    if (e != GlassEffects()) {
        BasicText(
            "Reset effects",
            Modifier.padding(top = Space.s2).clickable { env.effects = GlassEffects() },
            style = Saber.type.labelMedium.copy(color = colors.accent),
        )
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Saber.colors
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, Modifier.weight(1f), style = Saber.type.body.copy(color = colors.textPrimary))
        Canvas(Modifier.size(44.dp, 26.dp)) {
            val r = size.height / 2f
            drawRoundRect(if (on) colors.accent else colors.textTertiary, cornerRadius = CornerRadius(r))
            val knob = r - 3.dp.toPx()
            drawCircle(colors.glassHighlight.copy(alpha = 1f), knob, Offset(if (on) size.width - r else r, r))
        }
    }
}

/** Rolling frame stats from JankStats: p50 / p90 frame time and jank share. */
@Composable
fun FrameStatsOverlay(modifier: Modifier = Modifier) {
    val activity = LocalActivity.current ?: return
    val samples = remember { FrameSamples(240) }
    var text by remember { mutableStateOf("…") }
    DisposableEffect(activity) {
        val stats = JankStats.createAndTrack(activity.window) { frame ->
            samples.add(frame.frameDurationUiNanos / 1_000_000f, frame.isJank)
        }
        onDispose { stats.isTrackingEnabled = false }
    }
    LaunchedEffect(samples) {
        while (true) {
            delay(500)
            text = samples.summary()
        }
    }
    Box(modifier.statusBarsPadding().padding(Space.s2)) {
        GlassSurface(shape = GlassShape.Pill, material = GlassMaterial.Thick) {
            Column(Modifier.padding(horizontal = Space.s3, vertical = Space.s1)) {
                BasicText(text, style = Saber.type.labelMedium.copy(color = Saber.colors.textPrimary))
            }
        }
    }
}

private class FrameSamples(private val capacity: Int) {
    private val ms = FloatArray(capacity)
    private val jank = BooleanArray(capacity)
    private var count = 0
    private var next = 0

    @Synchronized
    fun add(durationMs: Float, isJank: Boolean) {
        ms[next] = durationMs
        jank[next] = isJank
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    @Synchronized
    fun summary(): String {
        if (count == 0) return "no frames"
        val sorted = ms.copyOf(count).sorted()
        val p50 = sorted[count / 2]
        val p90 = sorted[(count * 9 / 10).coerceAtMost(count - 1)]
        val janks = (0 until count).count { jank[it] }
        return "p50 %.1f ms · p90 %.1f ms · jank %.1f%%".format(p50, p90, janks * 100f / count)
    }
}

