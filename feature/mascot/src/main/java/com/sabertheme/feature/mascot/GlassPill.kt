package com.sabertheme.feature.mascot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import com.sabertheme.core.designsystem.glass.GlassBlobPainter
import com.sabertheme.core.designsystem.glass.GlassEnvironment
import com.sabertheme.core.designsystem.theme.GlassMaterial
import com.sabertheme.core.designsystem.theme.SaberColors
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

private const val PILL_W_DP = 100f
private const val PILL_H_DP = 32f
/**
 * One UI hands a touch that starts just under the status bar to its own
 * pull-down gesture (measured on the S23: drags from 40 dp down stay with
 * Home), so the docked pill is pulled from an invisible strip this far below it.
 */
private const val GRAB_GAP_DP = 16f
private const val GRAB_HEIGHT_DP = 32f
/** Only a small patch centred under the pill grabs it, so Home icons beside it stay tappable. */
private const val GRAB_WIDTH_DP = 48f
private const val SNAP_BACK_DP = 40f
private const val BREAK_S = 1.1f
private const val STREAK_S = 0.15f
private const val REFORM_AFTER_S = 3f
private const val FALL_DP_S2 = 1_800f

/**
 * The glass pill around the selfie camera (like a dynamic island): pull it
 * down from just under the status bar and drop it on Home, and Saber comes to
 * slash it in half; the halves fall away and a new pill forms around the
 * camera. Drawn on her surface like the rest of her; positions are layer px,
 * times her clock in seconds.
 */
internal class GlassPill(private val density: Density) {
    enum class State { Docked, Dragging, Resting, Breaking, Gone }

    var state = State.Docked
        private set

    /** Where the pill is (layer px); while docked, around the camera. */
    var bounds = Rect.Zero
        private set

    private var dock = Rect.Zero
    private var statusBottom = 0f
    private var floor = Float.MAX_VALUE
    private var layerWidth = Float.MAX_VALUE
    private var brokeAt = 0f
    private var cutAngle = 0f
    private var formedAt = -10f
    private val left = GlassBlobPainter()
    private val right = GlassBlobPainter()

    private fun Float.dp() = this * density.density

    /** Where touches grab it: while docked, the invisible strip just below it (see [GRAB_GAP_DP]). */
    val grabZone: Rect
        get() = when (state) {
            State.Docked -> if (dock == Rect.Zero) Rect.Zero else {
                val top = maxOf(dock.bottom, statusBottom) + GRAB_GAP_DP.dp()
                val half = GRAB_WIDTH_DP.dp() / 2
                Rect(dock.center.x - half, top, dock.center.x + half, top + GRAB_HEIGHT_DP.dp())
            }
            State.Dragging, State.Resting -> bounds.inflate(12f.dp())
            else -> Rect.Zero
        }

    /**
     * Places the dock around [cutout] (layer px; null without a camera
     * cutout: top centre of the status bar). [floorY] is the top of the
     * search bar, the lowest it may be dropped.
     */
    fun layout(cutout: Rect?, statusBarBottom: Float, layerWidth: Float, floorY: Float, time: Float) {
        statusBottom = statusBarBottom
        floor = floorY
        this.layerWidth = layerWidth
        val w = PILL_W_DP.dp()
        val h = PILL_H_DP.dp()
        val c = cutout?.let { Offset(it.center.x, it.top + minOf(it.height, statusBarBottom) / 2f) }
            ?: Offset(layerWidth / 2f, statusBarBottom / 2f)
        val top = (c.y - h / 2f).coerceAtLeast(1f.dp())
        dock = Rect(c.x - w / 2f, top, c.x + w / 2f, top + h)
        when (state) {
            State.Docked -> bounds = dock
            State.Gone -> if (time - brokeAt >= REFORM_AFTER_S) reform(time)
            State.Breaking -> if (time - brokeAt >= BREAK_S) state = State.Gone
            else -> Unit
        }
    }

    fun grab() {
        if (state == State.Docked || state == State.Resting) state = State.Dragging
    }

    /** The finger moved by [delta] (layer px) while dragging; it stays on screen and above the search bar. */
    fun dragBy(delta: Offset) {
        if (state != State.Dragging) return
        val m = bounds.translate(delta)
        val dx = (-m.left).coerceAtLeast(0f) + (layerWidth - m.right).coerceAtMost(0f)
        val dy = (dock.top - m.top).coerceAtLeast(0f) + (floor - m.bottom).coerceAtMost(0f)
        bounds = m.translate(Offset(dx, dy))
    }

    /** Let go: back home when dropped near the camera (false), else it rests there for her (true). */
    fun drop(): Boolean {
        if (state != State.Dragging) return false
        return if ((bounds.center - dock.center).getDistance() < SNAP_BACK_DP.dp() || bounds.top < statusBottom) {
            state = State.Docked
            bounds = dock
            false
        } else {
            state = State.Resting
            true
        }
    }

    /** The sword hit it along [angleDeg] (0 = a vertical cut). */
    fun breakAt(angleDeg: Float, time: Float) {
        if (state != State.Resting) return
        state = State.Breaking
        brokeAt = time
        cutAngle = angleDeg.coerceIn(-35f, 35f)
    }

    /** Home hid (drawer, settings): a waiting pill goes back around the camera. */
    fun reset() {
        if (state == State.Resting || state == State.Dragging) {
            state = State.Docked
            bounds = dock
        }
    }

    private fun reform(time: Float) {
        state = State.Docked
        bounds = dock
        formedAt = time
    }

    /** As [MessageCloud.draw]: [origin] is this scope's (0, 0) in the layer, [window] the layer in the window. */
    fun DrawScope.draw(origin: Offset, window: Offset, time: Float, env: GlassEnvironment, colors: SaberColors) {
        val windowOffset = window + origin
        when (state) {
            State.Gone -> return
            State.Breaking -> drawHalves(origin, windowOffset, time - brokeAt, env, colors)
            else -> {
                val bob = if (state == State.Resting && !env.reducedMotion) 2f.dp() * sin(time * 2.2f) else 0f
                val b = bounds.translate(Offset(0f, bob) - origin)
                val since = time - formedAt
                // A new pill swells into place around the camera.
                val pop = if (since in 0f..0.6f && !env.reducedMotion) 1f - exp(-since * 12f) + 0.15f * exp(-since * 6f) * sin(since * 14f) else 1f
                scale(pop.coerceAtLeast(0.01f), pivot = b.center) { pill(left, b, windowOffset, env, colors, 1f) }
            }
        }
    }

    private fun DrawScope.pill(painter: GlassBlobPainter, b: Rect, windowOffset: Offset, env: GlassEnvironment, colors: SaberColors, alpha: Float) {
        with(painter) {
            drawGlassBlob(
                env, colors, GlassMaterial.Regular, windowOffset, b.inflate(2f.dp()), FloatArray(0),
                box = b, boxRadius = b.height / 2f, alpha = alpha,
            )
        }
    }

    /** Two halves, cut along [cutAngle] through the centre, fly apart, spin, fall and fade. */
    private fun DrawScope.drawHalves(origin: Offset, windowOffset: Offset, t: Float, env: GlassEnvironment, colors: SaberColors) {
        val b = bounds.translate(-origin)
        val c = b.center
        val fade = (1f - t / BREAK_S).coerceIn(0f, 1f)
        val fall = 0.5f * FALL_DP_S2.dp() * t * t
        for (side in listOf(-1f, 1f)) {
            val drift = Offset(side * 70f.dp() * (1f - exp(-t * 4f)), -24f.dp() * (1f - exp(-t * 6f)) + fall)
            val spin = side * 26f * (1f - exp(-t * 3f))
            translate(drift.x, drift.y) {
                rotate(spin, pivot = c) {
                    rotate(cutAngle, pivot = c) {
                        // Keep only this side of the cut (a big rect, so the rotated pill still fits).
                        val reach = b.width
                        clipRect(
                            left = if (side < 0) c.x - reach else c.x,
                            top = c.y - reach,
                            right = if (side < 0) c.x else c.x + reach,
                            bottom = c.y + reach,
                        ) {
                            rotate(-cutAngle, pivot = c) {
                                pill(if (side < 0) left else right, b, windowOffset, env, colors, fade)
                            }
                        }
                    }
                }
            }
        }
        // The sword's streak across the cut.
        if (t < STREAK_S) {
            val a = Math.toRadians(cutAngle.toDouble())
            val dir = Offset(sin(a).toFloat(), -cos(a).toFloat())
            val len = b.height * 1.6f
            drawLine(
                Color.White.copy(alpha = 1f - t / STREAK_S), c - dir * len, c + dir * len,
                strokeWidth = 3f.dp(), cap = StrokeCap.Round,
            )
        }
    }
}
