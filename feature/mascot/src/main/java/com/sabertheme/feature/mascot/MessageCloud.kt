package com.sabertheme.feature.mascot

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import kotlin.math.exp
import kotlin.math.sin

/** A chat waiting on the phone, as her cloud shows it; [open] opens it in its app. */
data class CloudMessage(val chat: String, val sender: String?, val text: String, val count: Int, val open: () -> Unit)

private val CloudFill = Color(0xFFFDFDFF)
private val CloudLine = Color(0xFF4A2A1C)
private val Chat = Color(0xFF25D366)
private val Badge = Color(0xFFE5484D)
private val TitleInk = Color(0xFF1E1A24)
private val TextInk = Color(0xFF5B5566)

private const val ROWS = 3
private const val OPEN_FOR_S = 8f

/**
 * The thought cloud beside her while chats are unread: tap it for a preview
 * of the latest ones, tap a row to open that chat. Lives on her surface
 * like the rest of her; [bounds] (layer px) is where the layer puts its
 * touch box. Times are her clock in seconds.
 */
internal class MessageCloud(private val density: Density) {
    var messages: List<CloudMessage> = emptyList()
        private set
    var expanded = false
        private set

    /** Tap target in layer px; [Rect.Zero] while hidden. */
    var bounds = Rect.Zero
        private set

    private var shownAt = -10f
    private var expandedAt = 0f
    private var tail = Offset.Zero
    private var lines: List<Pair<String, String>> = emptyList()
    private var linesFor: List<CloudMessage>? = null
    private var linesWidth = 0f

    /** px per sp (TextPaint has its own `density`, so not read inside apply). */
    private val spPx = density.density * density.fontScale
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = TitleInk.toArgb()
        textSize = 14f * spPx
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TextInk.toArgb()
        textSize = 13f * spPx
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color(0xFF128C4A).toArgb()
        textSize = 12f * spPx
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 10f * spPx
    }

    private fun Float.dp() = this * density.density

    /** Takes the latest list; true when a newer message arrived (she glances at it). */
    fun update(list: List<CloudMessage>, time: Float): Boolean {
        if (list === messages) return false
        val before = messages
        messages = list
        if (list.isEmpty()) expanded = false
        if (before.isEmpty() && list.isNotEmpty()) shownAt = time
        val newer = list.isNotEmpty() && (before.isEmpty() || list.first() != before.first() || list.sumOf { it.count } > before.sumOf { it.count })
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

    /** Places the cloud beside [her] (her box, layer px) or hides it when [visible] is false. */
    fun layout(her: Rect, layerWidth: Float, visible: Boolean, time: Float) {
        if (expanded && time - expandedAt > OPEN_FOR_S) expanded = false
        if (!visible || messages.isEmpty()) {
            bounds = Rect.Zero
            return
        }
        val margin = 8f.dp()
        val head = Offset(her.center.x, her.top)
        if (expanded) {
            val width = minOf(260f.dp(), layerWidth - 2 * margin)
            val height = 12f.dp() * 2 + 22f.dp() + 44f.dp() * messages.size.coerceAtMost(ROWS)
            val right = (her.center.x + 30f.dp()).coerceIn(margin + width, layerWidth - margin)
            val bottom = her.top - 4f.dp()
            bounds = Rect(right - width, bottom - height, right, bottom)
            tail = head
        } else {
            val w = 54f.dp()
            val h = 40f.dp()
            val left = if (her.left - w + 14f.dp() >= margin) her.left - w + 14f.dp() else her.right - 14f.dp()
            val top = her.top - 6f.dp()
            bounds = Rect(left, top, left + w, top + h)
            // The edge of her head facing the cloud, so the dots stay off her hair.
            tail = Offset(if (left < her.left) her.left + her.width * 0.2f else her.right - her.width * 0.2f, her.top + her.height * 0.32f)
        }
    }

    /** Draws at [bounds]; [origin] is where this scope's (0, 0) is in the layer. */
    fun DrawScope.draw(origin: Offset, time: Float) {
        if (bounds == Rect.Zero) return
        val b = bounds.translate(-origin)
        val t = tail - origin
        val bob = if (expanded) 0f else 2f.dp() * sin(time * 2f)
        val since = time - shownAt
        // Pops in with a little overshoot.
        val pop = if (since in 0f..0.6f) 1f - exp(-since * 12f) + 0.18f * exp(-since * 6f) * sin(since * 14f) else 1f
        translate(0f, bob) {
            scale(pop.coerceAtLeast(0.01f), pivot = b.center) {
                // Thought dots trailing toward her head.
                val from = Offset(b.center.x, b.bottom)
                for ((f, r) in listOf(0.35f to 3.4f, 0.62f to 2.2f)) {
                    val c = from + (t - from) * f
                    drawCircle(CloudLine, (r + 1.1f).dp(), c)
                    drawCircle(CloudFill, r.dp(), c)
                }
                if (expanded) drawPreview(b) else drawPuff(b, time)
            }
        }
    }

    private fun DrawScope.drawPuff(b: Rect, time: Float) {
        val puffs = listOf(
            Offset(0.30f, 0.62f) to 0.36f, Offset(0.55f, 0.40f) to 0.42f, Offset(0.76f, 0.62f) to 0.34f,
            Offset(0.52f, 0.70f) to 0.34f,
        )
        val u = b.height
        for ((p, r) in puffs) drawCircle(CloudLine, r * u + 1.3f.dp(), Offset(b.left + p.x * b.width, b.top + p.y * b.height))
        for ((p, r) in puffs) drawCircle(CloudFill, r * u, Offset(b.left + p.x * b.width, b.top + p.y * b.height))
        // A green chat bubble with typing dots.
        val c = Offset(b.left + b.width * 0.53f, b.top + b.height * 0.56f)
        drawCircle(Chat, 9f.dp(), c)
        drawPath(
            Path().apply {
                moveTo(c.x - 7f.dp(), c.y + 4f.dp()); lineTo(c.x - 10f.dp(), c.y + 10f.dp()); lineTo(c.x - 2f.dp(), c.y + 7.5f.dp()); close()
            },
            Chat,
        )
        for (i in -1..1) {
            val lift = 1.2f.dp() * sin(time * 6f - i * 0.9f).coerceAtLeast(0f)
            drawCircle(Color.White, 1.5f.dp(), Offset(c.x + i * 3.6f.dp(), c.y - lift))
        }
        val count = messages.sumOf { it.count }
        val badge = Offset(b.right - 9f.dp(), b.top + 9f.dp())
        drawCircle(Badge, 8f.dp(), badge)
        drawCircle(Color.White, 8f.dp(), badge, style = Stroke(1.2f.dp()))
        val label = if (count > 9) "9+" else count.toString()
        drawContext.canvas.nativeCanvas.drawText(label, badge.x, badge.y - (badgePaint.ascent() + badgePaint.descent()) / 2f, badgePaint)
    }

    private fun DrawScope.drawPreview(b: Rect) {
        // A cloud-edged card: scalloped top over a rounded body.
        val r = 18f.dp()
        val bumps = ((b.width - 2 * r) / (26f.dp())).toInt().coerceAtLeast(2)
        val step = (b.width - 2 * r) / bumps
        for (pass in 0..1) {
            val color = if (pass == 0) CloudLine else CloudFill
            val grow = if (pass == 0) 1.3f.dp() else 0f
            drawRoundRect(color, Offset(b.left - grow, b.top - grow), Size(b.width + 2 * grow, b.height + 2 * grow), CornerRadius(r + grow))
            for (i in 0 until bumps) {
                drawCircle(color, step * 0.55f + grow, Offset(b.left + r + step * (i + 0.5f), b.top + 4f.dp()))
            }
        }
        val pad = 14f.dp()
        val canvas = drawContext.canvas.nativeCanvas
        val unread = messages.sumOf { it.count }
        canvas.drawText("WhatsApp · $unread unread", b.left + pad, b.top + 12f.dp() + 14f.dp(), headerPaint)
        val shown = messages.take(ROWS)
        val textWidth = b.width - pad * 2 - 16f.dp()
        if (linesFor !== messages || linesWidth != textWidth) {
            linesFor = messages
            linesWidth = textWidth
            lines = shown.map { m ->
                val title = if (m.sender != null) "${m.chat} · ${m.sender}" else m.chat
                TextUtils.ellipsize(title, titlePaint, textWidth, TextUtils.TruncateAt.END).toString() to
                    TextUtils.ellipsize(m.text.replace('\n', ' '), textPaint, textWidth, TextUtils.TruncateAt.END).toString()
            }
        }
        lines.forEachIndexed { i, (title, text) ->
            val top = b.top + 12f.dp() + 22f.dp() + i * 44f.dp()
            if (i > 0) drawLine(CloudLine.copy(alpha = 0.12f), Offset(b.left + pad, top), Offset(b.right - pad, top), 1f.dp())
            drawCircle(Chat, 4f.dp(), Offset(b.left + pad + 4f.dp(), top + 15f.dp()))
            val x = b.left + pad + 16f.dp()
            canvas.drawText(title, x, top + 19f.dp(), titlePaint)
            canvas.drawText(text, x, top + 37f.dp(), textPaint)
        }
    }
}
