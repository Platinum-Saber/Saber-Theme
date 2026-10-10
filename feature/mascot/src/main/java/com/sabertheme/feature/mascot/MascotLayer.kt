package com.sabertheme.feature.mascot

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.glass.LocalGlassEnvironment
import com.sabertheme.feature.mascot.MascotBrain.Mood
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/** Mascot height on Home; width follows the 100 x 140 rig box. */
internal val MASCOT_HEIGHT = 101.dp // 72 dp + 40%: readable on Home
internal val MASCOT_WIDTH = MASCOT_HEIGHT * (100f / 140f)

private const val WALK_DP_PER_S = 48f
private const val POSE_RATE = 14f

/**
 * Saber on the search bar. [anchor] is the search pill's window bounds; her
 * spot is on its top edge near its right end. The layer fills the screen so
 * she can be thrown anywhere, but only her own box takes touches: tap pokes
 * her, long-press makes her happy, dragging picks her up and letting go
 * throws her (physics in [MascotPhysics], moods in [MascotBrain]).
 */
@Composable
fun MascotLayer(anchor: () -> Rect, modifier: Modifier = Modifier, outfit: Outfit = Outfit.Armor) {
    val density = LocalDensity.current
    val view = LocalView.current
    val env = LocalGlassEnvironment.current
    val physics = remember { MascotPhysics(density.density) }
    val brain = remember { MascotBrain() }
    val motion = remember { Motion() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var layer by remember { mutableStateOf(IntSize.Zero) }
    var feet by remember { mutableStateOf(Offset.Unspecified) }
    var pose by remember { mutableStateOf(Pose.Neutral) }
    var time by remember { mutableFloatStateOf(0f) }
    val w = with(density) { MASCOT_WIDTH.toPx() }
    val h = with(density) { MASCOT_HEIGHT.toPx() }

    fun home(): Offset? {
        val bar = anchor()
        if (bar.isEmpty || layer == IntSize.Zero) return null
        return with(density) {
            // Boots sit a little into the bar's rim, so she stands on it.
            Offset(bar.right - 22.dp.toPx() - w / 2f - origin.x, bar.top + 3.dp.toPx() - origin.y)
        }
    }

    fun haptic(kind: Int) {
        view.performHapticFeedback(kind)
    }

    LaunchedEffect(physics) {
        var last = 0L
        while (true) withFrameNanos { now ->
            val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
            time += dt
            val ms = now / 1_000_000
            val spot = home() ?: return@withFrameNanos
            physics.minX = w / 2f
            physics.maxX = layer.width - w / 2f
            physics.floor = spot.y
            physics.ceiling = h
            if (feet == Offset.Unspecified) physics.place(spot.x, spot.y)

            if (brain.mood != Mood.Held) {
                physics.step(dt)?.let { impact ->
                    brain.land(impact, ms)
                    motion.landedAt = time
                    haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                if (!physics.airborne) {
                    // The bar may move (insets, rotation): stay on it.
                    physics.y = spot.y
                    if (brain.mood == Mood.Walking && physics.walkToward(spot.x, WALK_DP_PER_S, dt)) brain.arrived(ms)
                }
            }
            brain.awayFromHome = !physics.airborne && abs(physics.x - spot.x) > 2f * density.density
            brain.tick(ms)
            motion.facing = when {
                brain.mood == Mood.Walking -> sign(spot.x - physics.x).takeIf { it != 0f } ?: motion.facing
                brain.mood == Mood.Idle -> 1f
                else -> motion.facing
            }
            feet = Offset(physics.x, physics.y)

            val target = basePose(brain.mood)
            motion.blended = if (env.reducedMotion) target else motion.blended.approach(target, 1f - exp(-dt * POSE_RATE))
            pose = motion.animate(brain, time, ms, density.density)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInWindow()
                layer = it.size
            },
    ) {
        if (feet == Offset.Unspecified) return@Box
        Canvas(
            Modifier
                .offset { IntOffset((feet.x - w / 2f).roundToInt(), (feet.y - h).roundToInt()) }
                .size(MASCOT_WIDTH, MASCOT_HEIGHT)
                .pointerInput(physics) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val topLeft = { Offset(physics.x - w / 2f, physics.y - h) }
                        val tracker = VelocityTracker()
                        val longAt = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                        var pressedLong = false
                        var dragging = false
                        var grab = Offset.Zero
                        while (true) {
                            val event = if (!pressedLong && !dragging) {
                                val remaining = longAt - SystemClock.uptimeMillis()
                                if (remaining <= 0) null else withTimeoutOrNull(remaining) { awaitPointerEvent() }
                            } else {
                                awaitPointerEvent()
                            }
                            if (event == null) {
                                pressedLong = true
                                brain.longPress(System.nanoTime() / 1_000_000)
                                haptic(HapticFeedbackConstants.LONG_PRESS)
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val inLayer = change.position + topLeft()
                            val nowMs = System.nanoTime() / 1_000_000
                            if (!change.pressed) {
                                change.consume()
                                when {
                                    dragging -> {
                                        val v = tracker.calculateVelocity()
                                        physics.throwAt(v.x, v.y)
                                        brain.release(nowMs)
                                    }
                                    !pressedLong -> {
                                        brain.poke(nowMs)
                                        motion.pokedAt = time
                                        haptic(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                                break
                            }
                            if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                                grab = Offset(physics.x, physics.y) - inLayer
                                brain.grab(nowMs)
                                haptic(HapticFeedbackConstants.LONG_PRESS)
                            }
                            if (dragging) {
                                tracker.addPosition(change.uptimeMillis, inLayer)
                                val to = inLayer + grab
                                motion.dragVx = (to.x - physics.x) / 0.016f / density.density
                                physics.place(
                                    to.x.coerceIn(physics.minX, physics.maxX),
                                    to.y.coerceIn(physics.ceiling, physics.floor),
                                )
                            }
                            change.consume()
                        }
                    }
                },
        ) {
            drawSaber(pose, time, outfit)
        }
    }
}

/** The pose each mood blends toward; [Motion.animate] adds the moving parts. */
private fun basePose(mood: Mood): Pose = when (mood) {
    Mood.Idle, Mood.Walking, Mood.SoftLanding -> Pose.Neutral
    Mood.Surprised -> Pose.Surprised
    Mood.Pout -> Pose.Pout
    Mood.Crying -> Pose.Crying
    Mood.Happy -> Pose.Happy
    Mood.Held -> Pose.Held
    Mood.Falling -> Pose.Held.copy(armLeft = 120f, armRight = 120f, mouth = Mouth.Shock)
    Mood.Dizzy -> Pose.Dizzy
}

/** Procedural motion on top of the blended mood pose. Times in seconds. */
private class Motion {
    var blended = Pose.Neutral
    var facing = 1f
    var landedAt = -10f
    var pokedAt = -10f
    var dragVx = 0f
    private var lean = 0f

    fun animate(brain: MascotBrain, t: Float, nowMs: Long, dp: Float): Pose {
        var p = blended
        // Breathing and a lazy ahoge.
        p = p.copy(squash = p.squash * (1f + 0.012f * sin(t * 2.4f)), ahoge = p.ahoge + 5f * sin(t * 1.7f))
        // Blink for 130 ms every ~4 s (open eyes only).
        if (p.eyes == Eyes.Open && (t % 4.1f) < 0.13f) p = p.copy(eyes = Eyes.Closed)
        when (brain.mood) {
            Mood.Walking -> {
                val s = sin(t * 11f)
                p = p.copy(
                    legLeft = 22f * s, legRight = -22f * s,
                    armLeft = Pose.REST_ARM - 14f * s, armRight = Pose.REST_ARM + 14f * s,
                    lift = abs(s) * 1.6f, bodyTilt = 3f * facing,
                )
            }
            Mood.Held -> {
                // Dangling: swings against the finger, legs kick, arms flail.
                lean += (-(dragVx * 0.015f).coerceIn(-28f, 28f) - lean) * 0.2f
                dragVx *= 0.85f
                p = p.copy(
                    bodyTilt = lean,
                    legLeft = 16f + 18f * sin(t * 9f), legRight = -10f + 18f * sin(t * 9f + 1.6f),
                    armLeft = p.armLeft + 14f * sin(t * 12f), armRight = p.armRight + 14f * sin(t * 12f + 2f),
                )
            }
            Mood.Falling -> p = p.copy(legLeft = 25f, legRight = 25f, bodyTilt = 6f * sin(t * 7f))
            Mood.Crying -> p = p.copy(headTilt = p.headTilt + 2.5f * sin(t * 18f))
            Mood.Happy -> p = p.copy(bodyTilt = 5f * sin(t * 6f), lift = 2.5f * abs(sin(t * 6f)))
            else -> Unit
        }
        if (brain.mood != Mood.Held) lean = 0f
        // Landing squash: a quick spring.
        val sinceLanding = t - landedAt
        if (sinceLanding in 0f..0.7f) p = p.copy(squash = p.squash * (1f - 0.2f * exp(-sinceLanding * 8f) * cos(sinceLanding * 20f)))
        // Poke flinch.
        val sincePoke = t - pokedAt
        if (sincePoke in 0f..0.4f) p = p.copy(squash = p.squash * (1f - 0.1f * exp(-sincePoke * 10f) * cos(sincePoke * 25f)))
        return p.copy(facing = facing)
    }
}
