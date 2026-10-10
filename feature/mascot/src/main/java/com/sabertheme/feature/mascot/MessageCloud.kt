package com.sabertheme.feature.mascot

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import com.sabertheme.core.designsystem.glass.GlassBlobPainter
import com.sabertheme.core.designsystem.glass.GlassEnvironment
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.SaberColors
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A chat waiting on the phone, as her cloud shows it; [open] opens it in its
 * app, [dismiss] clears its notification.
 */
data class CloudMessage(
    val chat: String,
    val sender: String?,
    val text: String,
    val count: Int,
    val open: () -> Unit,
    val dismiss: () -> Unit,
)

private val Chat = Color(0xFF25D366)
private val ChatDeep = Color(0xFF128C4A)
private val Badge = Color(0xFFE5484D)

private const val ROWS = 3
private const val OPEN_FOR_S = 8f
private const val FLIGHT_S = 0.7f
private const val FLICK_DP_S = 900f
private const val FLICK_DP = 90f

/**
 * The glass thought cloud beside her while chats are unread: tap it for a
 * preview of the latest ones, tap a row to open that chat, flick it away to
 * dismiss them (it drifts apart as it goes). Lives on her surface like the
 * rest of her; [bounds] (layer px) is where the layer puts its touch box.
 * Times are her clock in seconds.
 */
internal class MessageCloud(private val density: Density) {
    var messages: List<CloudMessage> = emptyList()
        private set
    var expanded = false
        private set

    /** Tap target in layer px; [Rect.Zero] while hidden. */
    var bounds = Rect.Zero
        private set

    /** Finger offset while it is being dragged; springs back unless flung. */
    var drag = Offset.Zero
    var dragging = false

    private var shownAt = -10f
    private var expandedAt = 0f
    private var lastTime = 0f
    private var tail = Offset.Zero
    private var flight: Flight? = null
    private val glass = GlassBlobPainter()
    private var lines: List<Pair<String, String>> = emptyList()
    private var linesFor: List<CloudMessage>? = null
    private var linesWidth = 0f

    /** A flung cloud: where it was, which way it flies, when it went. */
    private class Flight(val bounds: Rect, val expanded: Boolean, val tail: Offset, val from: Offset, val velocity: Offset, val start: Float)

    private class Shape(val area: Rect, val blobs: FloatArray, val box: Rect?, val radius: Float, val blend: Float)

    /** px per sp (TextPaint has its own `density`, so not read inside apply). */
    private val spPx = density.density * density.fontScale
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 14f * spPx
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * spPx
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 12f * spPx
    }

    private fun Float.dp() = this * density.density

    /** Takes the latest list; true when a newer message arrived (she glances at it). */
    fun update(list: List<CloudMessage>, time: Float): Boolean {
        if (list === messages) return false
        val before = messages
        messages = list
        if (list.isEmpty()) expanded = false
        val newer = list.isNotEmpty() &&
            (before.isEmpty() || list.first().chat != before.first().chat || list.sumOf { it.count } > before.sumOf { it.count })
        if (newer && !expanded) shownAt = time
        return newer
    }

    fun toggle(time: Float) {
        expanded = !expanded
        expandedAt = time
    }

    fun collapse() {
        expanded = false
    }

    /** The row under [y] (px from the top of [bounds]) when expanded, or null for the header. */
    fun rowAt(y: Float): CloudMessage? {
        if (!expanded) return null
        val rowsTop = 12f.dp() + 22f.dp()
        return if (y < rowsTop) null else messages.take(ROWS).getOrNull(((y - rowsTop) / 44f.dp()).toInt())
    }

    /**
     * The finger let go after a drag at [velocity] (px/s): a flick (fast or
     * far enough) sends the cloud off and dismisses its chats, returning
     * true. Otherwise it springs back.
     */
    fun release(velocity: Offset, time: Float): Boolean {
        dragging = false
        val speed = velocity.getDistance() / density.density
        val far = drag.getDistance() / density.density
        if (bounds == Rect.Zero || (speed < FLICK_DP_S && far < FLICK_DP)) return false
        val v = if (speed >= 300f) velocity else drag / drag.getDistance().coerceAtLeast(1f) * 1_500f.dp()
        flight = Flight(bounds, expanded, tail, drag, v, time)
        val gone = messages
        drag = Offset.Zero
        expanded = false
        gone.forEach { it.dismiss() }
        return true
    }

    /** Places the cloud beside [her] (her box, layer px) or hides it when [visible] is false. */
    fun layout(her: Rect, layerWidth: Float, visible: Boolean, time: Float) {
        val dt = (time - lastTime).coerceIn(0f, 0.1f)
        lastTime = time
        if (!dragging) drag *= exp(-dt * 14f)
        if (expanded && time - expandedAt > OPEN_FOR_S) expanded = false
        if (!visible || messages.isEmpty()) {
            bounds = Rect.Zero
            return
        }
        val margin = 8f.dp()
        if (expanded) {
            val width = minOf(260f.dp(), layerWidth - 2 * margin)
            val height = 12f.dp() * 2 + 22f.dp() + 44f.dp() * messages.size.coerceAtMost(ROWS)
            val right = (her.center.x + 30f.dp()).coerceIn(margin + width, layerWidth - margin)
            val bottom = her.top - 16f.dp()
            bounds = Rect(right - width, bottom - height, right, bottom)
            tail = Offset(her.center.x, her.top + 4f.dp())
        } else {
            val w = 56f.dp()
            val h = 42f.dp()
            val left = if (her.left - w + 6f.dp() >= margin) her.left - w + 6f.dp() else her.right - 6f.dp()
            val top = her.top - 30f.dp()
            bounds = Rect(left, top, left + w, top + h)
            // The edge of her head facing the cloud, so the dots stay off her hair.
            tail = Offset(if (left < her.left) her.left + her.width * 0.2f else her.right - her.width * 0.2f, her.top + her.height * 0.3f)
        }
    }

    /**
     * Draws at [bounds]. [origin] is where this scope's (0, 0) is in the
     * layer, [window] the layer's origin in the window (for the glass).
     */
    fun DrawScope.draw(origin: Offset, window: Offset, time: Float, env: GlassEnvironment, colors: SaberColors) {
        val windowOffset = window + origin
        flight?.let { f ->
            val t = time - f.start
            if (t >= FLIGHT_S || env.reducedMotion) flight = null else drawFlight(f, t, origin, windowOffset, env, colors)
        }
        if (bounds == Rect.Zero) return
        val bob = if (expanded || dragging) 0f else 2f.dp() * sin(time * 2f)
        val shift = drag + Offset(0f, bob) - origin
        val since = time - shownAt
        // Pops in with a little overshoot.
        val pop = if (since in 0f..0.6f && !env.reducedMotion) 1f - exp(-since * 12f) + 0.18f * exp(-since * 6f) * sin(since * 14f) else 1f
        val b = bounds.translate(shift)
        val s = shape(b, expanded, tail - origin)
        scale(pop.coerceAtLeast(0.01f), pivot = b.center) {
            with(glass) {
                drawGlassBlob(
                    env, colors, if (expanded) GlassMaterial.Thick else GlassMaterial.Regular, windowOffset,
                    s.area, s.blobs, s.box, s.radius, s.blend,
                )
            }
            if (expanded) drawPreview(b, colors) else drawGlyph(b)
        }
    }

    /** The cloud as glass blobs (plus a box when expanded) and two thought dots toward [tail]. */
    private fun shape(b: Rect, expanded: Boolean, tail: Offset): Shape {
        val blobs = ArrayList<Float>(36)
        val box: Rect?
        val radius: Float
        if (expanded) {
            box = b
            radius = 18f.dp()
            val bumps = ((b.width - 2 * radius) / 38f.dp()).toInt().coerceIn(2, 8)
            val step = (b.width - 2 * radius) / bumps
            for (i in 0 until bumps) blobs += listOf(b.left + radius + step * (i + 0.5f), b.top + 5f.dp(), step * 0.52f)
        } else {
            box = null
            radius = 0f
            for ((p, r) in PUFFS) blobs += listOf(b.left + p.x * b.width, b.top + p.y * b.height, r * b.height)
        }
        // Dots start under the cloud on her side and step toward her head.
        val from = Offset(if (tail.x > b.center.x) b.left + b.width * 0.72f else b.left + b.width * 0.28f, b.bottom + 2f.dp())
        for ((f, r) in listOf(0.3f to 4f, 0.72f to 2.6f)) {
            val c = from + (tail - from) * f
            blobs += listOf(c.x, c.y, r.dp())
        }
        var area = b.inflate(10f.dp())
        for (i in blobs.indices step 3) {
            val r = blobs[i + 2] + 2f
            area = Rect(
                minOf(area.left, blobs[i] - r), minOf(area.top, blobs[i + 1] - r),
                maxOf(area.right, blobs[i] + r), maxOf(area.bottom, blobs[i + 1] + r),
            )
        }
        return Shape(area, blobs.toFloatArray(), box, radius, if (expanded) 8f.dp() else 3.5f.dp())
    }

    /** Flung: it keeps going, slowing, while its puffs drift apart, shrink and fade. */
    private fun DrawScope.drawFlight(f: Flight, t: Float, origin: Offset, windowOffset: Offset, env: GlassEnvironment, colors: SaberColors) {
        val e = (t / FLIGHT_S).coerceIn(0f, 1f)
        val ease = 1f - (1f - e).pow(3)
        val travel = f.from + f.velocity * ((1f - exp(-3f * t)) / 3f)
        val b = f.bounds.translate(travel - origin)
        val s = shape(b, f.expanded, f.tail - origin + travel)
        val c = b.center
        val blobs = s.blobs.copyOf()
        for (i in blobs.indices step 3) {
            var dx = blobs[i] - c.x
            var dy = blobs[i + 1] - c.y
            val len = sqrt(dx * dx + dy * dy)
            if (len < 1f) {
                dx = if (i % 2 == 0) 1f else -1f
                dy = -0.4f
            } else {
                dx /= len
                dy /= len
            }
            val spread = (30f + (i % 4) * 8f).dp() * ease
            blobs[i] += dx * spread
            blobs[i + 1] += dy * spread - 12f.dp() * ease
            blobs[i + 2] *= 1f - 0.55f * ease
        }
        val shrink = 1f - 0.75f * ease
        val box = s.box?.let {
            Rect(it.center.x - it.width / 2f * shrink, it.center.y - it.height / 2f * shrink, it.center.x + it.width / 2f * shrink, it.center.y + it.height / 2f * shrink)
        }
        with(glass) {
            drawGlassBlob(
                env, colors, if (f.expanded) GlassMaterial.Thick else GlassMaterial.Regular, windowOffset,
                s.area.inflate(48f.dp()), blobs, box, s.radius * (1f - 0.5f * ease), s.blend * (1f - ease), alpha = 1f - e * e,
            )
        }
    }

    /** Just a small red dot on the cloud: something unread. */
    private fun DrawScope.drawGlyph(b: Rect) {
        val dot = Offset(b.right - 10f.dp(), b.top + 10f.dp())
        drawCircle(Color.White, 5.5f.dp(), dot)
        drawCircle(Badge, 4.2f.dp(), dot)
    }

    private fun DrawScope.drawPreview(b: Rect, colors: SaberColors) {
        titlePaint.color = colors.textPrimary.copy(alpha = 1f).toArgb()
        textPaint.color = colors.textSecondary.toArgb()
        headerPaint.color = (if (colors.isDark) Chat else ChatDeep).toArgb()
        val pad = 14f.dp()
        val canvas = drawContext.canvas.nativeCanvas
        val unread = messages.sumOf { it.count }
        canvas.drawText("WhatsApp · $unread unread", b.left + pad, b.top + 12f.dp() + 14f.dp(), headerPaint)
        val textWidth = b.width - pad * 2 - 16f.dp()
        if (linesFor !== messages || linesWidth != textWidth) {
            linesFor = messages
            linesWidth = textWidth
            lines = messages.take(ROWS).map { m ->
                val title = if (m.sender != null) "${m.chat} · ${m.sender}" else m.chat
                TextUtils.ellipsize(title, titlePaint, textWidth, TextUtils.TruncateAt.END).toString() to
                    TextUtils.ellipsize(m.text.replace('\n', ' '), textPaint, textWidth, TextUtils.TruncateAt.END).toString()
            }
        }
        lines.forEachIndexed { i, (title, text) ->
            val top = b.top + 12f.dp() + 22f.dp() + i * 44f.dp()
            if (i > 0) drawLine(colors.textPrimary.copy(alpha = 0.12f), Offset(b.left + pad, top), Offset(b.right - pad, top), 1f.dp())
            drawCircle(Chat, 4f.dp(), Offset(b.left + pad + 4f.dp(), top + 15f.dp()))
            val x = b.left + pad + 16f.dp()
            canvas.drawText(title, x, top + 19f.dp(), titlePaint)
            canvas.drawText(text, x, top + 37f.dp(), textPaint)
        }
    }

    private companion object {
        /** Collapsed cloud puffs: centre as a fraction of the bounds, radius as a fraction of its height. */
        val PUFFS = listOf(
            Offset(0.22f, 0.62f) to 0.25f, Offset(0.42f, 0.40f) to 0.31f, Offset(0.66f, 0.36f) to 0.29f,
            Offset(0.82f, 0.60f) to 0.23f, Offset(0.52f, 0.68f) to 0.27f,
        )
    }
}
