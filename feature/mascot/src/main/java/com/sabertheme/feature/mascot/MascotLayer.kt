package com.sabertheme.feature.mascot

import kotlin.math.PI
import com.sabertheme.core.designsystem.glass.GlassEnvironment
import kotlinx.coroutines.delay
import android.os.PowerManager
import android.view.WindowInsets
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sabertheme.core.designsystem.glass.LocalGlassEnvironment
import com.sabertheme.core.designsystem.theme.LocalSaberColors
import com.sabertheme.feature.mascot.MascotBrain.Mood
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/** Mascot height on Home; width follows the 100 x 140 rig box. */
internal val MASCOT_HEIGHT = 101.dp // 72 dp + 40%: readable on Home
internal val MASCOT_WIDTH = MASCOT_HEIGHT * (100f / 140f)

private const val WALK_DP_PER_S = 48f
private const val RUN_DP_PER_S = 170f
/** A pill within this many of her heights above her feet gets a swing from the bar, no jump. */
private const val REACH_HEIGHTS = 1.1f
/** Her feet at the top of the jump, below the pill's centre (in her heights): the pill is at her head. */
private const val APEX_BELOW_HEIGHTS = 0.85f
private const val GROUND_SWING_S = 0.25f
private const val SWING_MS = 350L
private const val POSE_RATE = 14f
private const val FRAME_MS = 22L
private const val SAVER_FRAME_MS = 110L
/** A finger this close to her chest gets a duel; farther away she points. */
private const val NEAR_DP = 130f
/** Talking ends on its own after this if the voice never reports it stopped. */
private const val TALK_MAX_MS = 120_000L

/**
 * Saber on the search bar. [anchor] is the search pill's window bounds; her
 * spot is on its top edge near its right end. The layer fills the screen so
 * she can be thrown anywhere, but only her own box takes touches: tap pokes
 * her, long-press makes her happy, dragging picks her up and letting go
 * throws her (physics in [MascotPhysics], moods in [MascotBrain]).
 */
@Composable
fun MascotLayer(
    anchor: () -> Rect,
    modifier: Modifier = Modifier,
    outfit: Outfit = Outfit.Armor,
    /** True while a media session plays: she dances. */
    musicPlaying: () -> Boolean = { false },
    /** True while the phone charges: she stands guard with a glowing sword. */
    charging: () -> Boolean = { false },
    /** 0..1 fade, e.g. while the drawer opens over Home. */
    alpha: () -> Float = { 1f },
    /** Unread chats for her message cloud; empty hides it. */
    messages: () -> List<CloudMessage> = { emptyList() },
    /** The glass pill around the camera that she slashes when it is dropped on Home. */
    pillEnabled: () -> Boolean = { true },
    /** True while her voice is speaking (the briefing): she talks, mouth moving. */
    speaking: () -> Boolean = { false },
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val env = LocalGlassEnvironment.current
    val power = remember { view.context.getSystemService(PowerManager::class.java) }
    // The frame loop outlives recompositions: read the latest values through these.
    val music by rememberUpdatedState(musicPlaying)
    val currentCharging by rememberUpdatedState(charging)
    val currentMessages by rememberUpdatedState(messages)
    val currentColors by rememberUpdatedState(LocalSaberColors.current)
    val cloud = remember { MessageCloud(density) }
    var cloudBox by remember { mutableStateOf(Rect.Zero) }
    val pill = remember { GlassPill(density) }
    var pillZone by remember { mutableStateOf(Rect.Zero) }
    val currentPillEnabled by rememberUpdatedState(pillEnabled)
    val currentSpeaking by rememberUpdatedState(speaking)
    // Camera cutout (layer px) and status bar bottom (window px), read from the insets on layout.
    val cutout = remember { arrayOfNulls<Rect>(1) }
    val statusBar = remember { floatArrayOf(0f) }
    val pillBox = remember { arrayOf(Offset.Zero) }
    val currentAnchor by rememberUpdatedState(anchor)
    val currentAlpha by rememberUpdatedState(alpha)
    val currentOutfit by rememberUpdatedState(outfit)
    val surface = remember { MascotSurface(view.context) }
    DisposableEffect(surface) {
        surface.attach(view)
        onDispose { surface.detach() }
    }
    val physics = remember { MascotPhysics(density.density) }
    val brain = remember { MascotBrain() }
    val motion = remember { Motion() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var layer by remember { mutableStateOf(IntSize.Zero) }
    var feet by remember { mutableStateOf(Offset.Unspecified) }
    // Drawn into [surface], not Compose: plain fields, so animating never invalidates Home.
    val pose = remember { arrayOf(Pose.Neutral) }
    val clock = remember { floatArrayOf(0f) }
    val touchBox = remember { arrayOf(Offset.Zero) }
    val w = with(density) { MASCOT_WIDTH.toPx() }
    val h = with(density) { MASCOT_HEIGHT.toPx() }

    fun home(): Offset? {
        val bar = currentAnchor()
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
        var saver = false
        var saverCheckedAt = 0L
        // The finger elsewhere on Home she reacts to (touches on her are pokes and drags).
        var fingerDownNanos = env.touchDownNanos
        var fingerOnHer = false
        var fingerHeld = false
        var lastMood = brain.mood
        var groundSwingAt = -1f
        var wasSpeaking = false
        // Always animating while Home shows her, capped near 30 fps (~8 fps in Power Saving).
        while (true) {
            delay(if (saver) SAVER_FRAME_MS else FRAME_MS)
            withFrameNanos { now ->
            val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
            clock[0] += dt
            val time = clock[0]
            val ms = now / 1_000_000
            if (ms - saverCheckedAt > 2_000) {
                saverCheckedAt = ms
                saver = power.isPowerSaveMode
            }
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
                    when (brain.mood) {
                        Mood.Walking -> if (physics.walkToward(spot.x, WALK_DP_PER_S, dt)) brain.arrived(ms)
                        Mood.Chase -> {
                            val target = pill.bounds.center.x.coerceIn(physics.minX, physics.maxX)
                            motion.facing = sign(target - physics.x).takeIf { it != 0f } ?: motion.facing
                            if (physics.walkToward(target, RUN_DP_PER_S, dt)) {
                                brain.reachedPill(ms)
                                val apex = pill.bounds.center.y + APEX_BELOW_HEIGHTS * h
                                groundSwingAt = -1f
                                if (physics.y - pill.bounds.center.y <= REACH_HEIGHTS * h || !physics.leapTo(target, apex.coerceAtLeast(physics.ceiling))) {
                                    groundSwingAt = time + GROUND_SWING_S
                                }
                            }
                        }
                        Mood.Leap -> if (groundSwingAt in 0f..time && pill.state == GlassPill.State.Resting) {
                            pill.breakAt(motion.facing * 18f, time)
                            brain.slashed(ms, airborne = false)
                            groundSwingAt = -1f
                            haptic(HapticFeedbackConstants.CONFIRM)
                        }
                        Mood.Wander -> {
                            val target = (spot.x + brain.wanderDp * density.density).coerceIn(physics.minX, physics.maxX)
                            motion.facing = sign(target - physics.x).takeIf { it != 0f } ?: motion.facing
                            if (physics.walkToward(target, WALK_DP_PER_S * 0.7f, dt)) brain.arrived(ms)
                        }
                        else -> Unit
                    }
                }
            }
            // In the air, the slash lands at the top of the jump.
            if (brain.mood == Mood.Leap && physics.airborne && physics.vy >= 0f && pill.state == GlassPill.State.Resting) {
                pill.breakAt(-motion.facing * 22f, time)
                brain.slashed(ms, airborne = true)
                haptic(HapticFeedbackConstants.CONFIRM)
            }
            val pillOn = currentPillEnabled() && currentAlpha() > 0.5f
            if (!pillOn) pill.reset()
            // The pill went back (grabbed again, or Home hid) before she got to it.
            if ((brain.mood == Mood.Chase || (brain.mood == Mood.Leap && brain.slashAt < brain.since)) && pill.state != GlassPill.State.Resting) {
                brain.pillTaken(ms)
                groundSwingAt = -1f
            }
            pill.layout(cutout[0], statusBar[0] - origin.y, layer.width.toFloat(), spot.y, time)
            val zone = if (pillOn) pill.grabZone else Rect.Zero
            if (zone != pillZone) pillZone = zone
            brain.awayFromHome = !physics.airborne && abs(physics.x - spot.x) > 2f * density.density
            val quietMs = (System.nanoTime() - env.lastInteractionNanos) / 1_000_000
            brain.tick(ms, quietMs, music())
            // Back to talking once a poke or landing is over, while the voice still goes.
            val talking = currentSpeaking()
            if (talking && brain.mood != Mood.Talking) brain.talk(ms, TALK_MAX_MS)
            if (!talking && wasSpeaking) brain.quiet(ms)
            wasSpeaking = talking

            val u = h / 140f // px per rig unit
            val herBox = Rect(physics.x - w / 2f, physics.y - h, physics.x + w / 2f, physics.y)
            // A quick tap can go down and up between two frames: a new touch counts for at least one.
            val newTouch = env.touchDownNanos != fingerDownNanos
            if (newTouch) {
                fingerDownNanos = env.touchDownNanos
                val p = env.touchPosition - origin
                val onCloud = cloud.bounds.contains(p)
                fingerOnHer = herBox.contains(p) || onCloud
                if (!onCloud) cloud.collapse() // a tap elsewhere closes the preview
            }
            if ((env.touchDown || newTouch) && !fingerOnHer && env.touchPosition.isSpecified && currentAlpha() > 0.5f) {
                val p = env.touchPosition - origin
                val chest = Offset(physics.x, physics.y - 50f * u)
                brain.finger((p - chest).getDistance() <= NEAR_DP * density.density, ms)
                motion.aimAt(p, physics.x, physics.y, u, density.density)
                fingerHeld = true
            } else if (fingerHeld) {
                fingerHeld = false
                brain.fingerUp(ms)
            }
            if (brain.mood == Mood.Duel && lastMood != Mood.Duel) haptic(HapticFeedbackConstants.CLOCK_TICK)
            lastMood = brain.mood

            val newMessage = cloud.update(currentMessages(), time)
            cloud.layout(herBox, layer.width.toFloat(), brain.mood != Mood.Held && brain.mood != Mood.Falling && currentAlpha() > 0.5f, time)
            if (cloud.bounds != cloudBox) cloudBox = cloud.bounds
            if (newMessage && cloud.bounds != Rect.Zero) {
                brain.message(ms)
                if (brain.mood == Mood.Moment) motion.facing = sign(cloud.bounds.center.x - physics.x).takeIf { it != 0f } ?: motion.facing
            }

            motion.facing = when {
                brain.mood == Mood.Duel || brain.mood == Mood.Point -> motion.aimFacing
                brain.mood == Mood.Walking -> sign(spot.x - physics.x).takeIf { it != 0f } ?: motion.facing
                brain.mood == Mood.Idle || brain.mood == Mood.Dancing || brain.mood == Mood.Sleeping || brain.mood == Mood.Talking -> 1f
                else -> motion.facing
            }
            feet = Offset(physics.x, physics.y)

            val powered = currentCharging()
            val target = basePose(brain, time, ms, motion, powered).let { if (powered) it.copy(glow = 1f) else it }
            motion.blended = if (env.reducedMotion) target else motion.blended.approach(target, 1f - exp(-dt * POSE_RATE))
            // Lean with the phone: the tilt light moves off its rest direction.
            val tilt = if (env.effects.tilt) (env.light.x - GlassEnvironment.DEFAULT_LIGHT.x).coerceIn(-1f, 1f) else 0f
            pose[0] = if (env.reducedMotion) motion.blended else motion.animate(brain, time, tilt, talking)
            val topLeft = Offset(physics.x - w / 2f, physics.y - h) + origin
            surface.draw(density, topLeft, w, h, currentAlpha()) {
                if (currentPillEnabled()) with(pill) { draw(topLeft - origin, origin, time, env, currentColors) }
                drawSaber(pose[0], time, currentOutfit)
                with(cloud) { draw(topLeft - origin, origin, time, env, currentColors) }
            }
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInWindow()
                layer = it.size
                val insets = view.rootWindowInsets
                statusBar[0] = insets?.getInsets(WindowInsets.Type.statusBars())?.top?.toFloat() ?: 0f
                cutout[0] = insets?.displayCutout?.boundingRectTop?.takeUnless { r -> r.isEmpty }?.let { r ->
                    Rect(r.left - origin.x, r.top - origin.y, r.right - origin.x, r.bottom - origin.y)
                }
            },
    ) {
        if (feet == Offset.Unspecified) return@Box
        // The glass pill's touch target: pull it down from under the status bar, drop it for her.
        if (pillZone != Rect.Zero) {
            Box(
                Modifier
                    .offset { IntOffset(pillZone.left.roundToInt(), pillZone.top.roundToInt()) }
                    .layout { measurable, _ ->
                        val box = pillZone
                        val placeable = measurable.measure(Constraints.fixed(box.width.roundToInt(), box.height.roundToInt()))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    // The box moves with the pill: pointer positions are local to where it is laid out.
                    .onGloballyPositioned { pillBox[0] = it.positionInParent() }
                    .pointerInput(pill) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            pill.grab()
                            brain.pillTaken(System.nanoTime() / 1_000_000)
                            haptic(HapticFeedbackConstants.LONG_PRESS)
                            var last = down.position + pillBox[0]
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) break
                                val now = change.position + pillBox[0]
                                pill.dragBy(now - last)
                                last = now
                            }
                            if (pill.drop()) {
                                brain.pillDropped(System.nanoTime() / 1_000_000)
                                haptic(HapticFeedbackConstants.CONFIRM)
                            } else {
                                haptic(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                    },
            )
        }
        // Her message cloud's touch target: tap to preview, tap a chat to open it, flick it away to dismiss.
        Box(
            Modifier
                .offset { IntOffset(cloudBox.left.roundToInt(), cloudBox.top.roundToInt()) }
                .layout { measurable, _ ->
                    val box = cloudBox
                    val placeable = measurable.measure(Constraints.fixed(box.width.roundToInt(), box.height.roundToInt()))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .pointerInput(cloud) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) {
                                if (cloud.dragging) {
                                    val v = tracker.calculateVelocity()
                                    if (cloud.release(Offset(v.x, v.y), clock[0])) haptic(HapticFeedbackConstants.CONFIRM)
                                } else {
                                    haptic(HapticFeedbackConstants.CLOCK_TICK)
                                    if (!cloud.expanded) {
                                        cloud.toggle(clock[0])
                                    } else {
                                        val chat = cloud.rowAt(change.position.y)
                                        cloud.collapse()
                                        chat?.open?.invoke()
                                    }
                                }
                                break
                            }
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val moved = change.position - down.position
                            if (!cloud.dragging && moved.getDistance() > viewConfiguration.touchSlop) cloud.dragging = true
                            if (cloud.dragging) cloud.drag = moved
                        }
                        cloud.dragging = false
                    }
                },
        )
        Box(
            Modifier
                .offset { IntOffset((feet.x - w / 2f).roundToInt(), (feet.y - h).roundToInt()) }
                .size(MASCOT_WIDTH, MASCOT_HEIGHT)
                // Where the box actually is in the layer: pointer positions are relative to
                // this laid-out spot, which lags her physics position while dragging.
                .onGloballyPositioned { touchBox[0] = it.positionInParent() }
                .pointerInput(physics) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
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
                            val inLayer = change.position + touchBox[0]
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
                                        motion.pokedAt = clock[0]
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
        )
    }
}

/** The pose each mood (and idle moment) blends toward; [Motion.animate] adds the moving parts. */
private fun basePose(brain: MascotBrain, t: Float, nowMs: Long, motion: Motion, charging: Boolean): Pose = when (brain.mood) {
    Mood.Chase -> Pose.SwordReady.copy(mouth = Mouth.Open, brows = Brows.Angry)
    Mood.Leap -> if (nowMs - brain.slashAt < SWING_MS) Pose.SwordSwing else Pose.SwordReady.copy(brows = Brows.Angry, mouth = Mouth.Open)
    Mood.Idle -> if (charging) Pose.Guard else Pose.Neutral
    Mood.Walking, Mood.Wander, Mood.SoftLanding -> Pose.Neutral
    Mood.Surprised -> Pose.Surprised
    Mood.Pout -> Pose.Pout
    Mood.Crying -> Pose.Crying
    Mood.Happy -> Pose.Happy
    Mood.Held -> Pose.Held
    Mood.Falling -> Pose.Held.copy(armLeft = 120f, armRight = 120f, mouth = Mouth.Shock)
    Mood.Dizzy -> Pose.Dizzy
    Mood.Sleeping -> Pose.Sleepy
    Mood.Dancing -> Pose.Dancing
    // Friendly and open while she reads out the briefing; [Motion.animate] moves her mouth.
    Mood.Talking -> Pose.Neutral.copy(brows = Brows.Calm, mouth = Mouth.Smile, blush = 0.4f, armLeft = 20f, armRight = 28f, headTilt = 3f)
    // Aimed at the finger, eyes on it. The arm stops short of her face; the blade still points at it.
    Mood.Duel -> Pose(
        prop = Prop.Sword, armRight = motion.aimAngle.coerceAtMost(110f), swordAngle = 180f - motion.aimAngle, armLeft = 38f,
        eyes = if ((t * 1.3f).toInt() % 3 == 2) Eyes.Happy else Eyes.Open, brows = Brows.Calm, mouth = Mouth.Smile, blush = 0.6f,
        headTurn = 0.5f * motion.gazeX, gazeX = motion.gazeX, gazeY = motion.gazeY,
    )
    Mood.Point -> Pose(
        prop = Prop.Point, armRight = motion.aimAngle.coerceAtMost(135f), armLeft = 10f, eyes = Eyes.Open, brows = Brows.Calm, mouth = Mouth.Open,
        headTurn = 0.5f * motion.gazeX, gazeX = motion.gazeX, gazeY = motion.gazeY,
    )
    Mood.Moment -> when (brain.moment) {
        MascotBrain.Moment.LookAround -> Pose.Neutral.copy(brows = Brows.Calm)
        MascotBrain.Moment.Stretch -> Pose.Neutral.copy(eyes = Eyes.Closed, mouth = Mouth.Open, armLeft = 165f, armRight = 165f, squash = 1.05f, lift = 1.5f)
        MascotBrain.Moment.SwordPractice -> if ((t * 1.6f).toInt() % 2 == 0) Pose.SwordReady else Pose.SwordSwing
        MascotBrain.Moment.Sit -> Pose.Sitting
        MascotBrain.Moment.HeartHands -> Pose.HeartHands
        MascotBrain.Moment.Curious -> Pose.Curious
        MascotBrain.Moment.Thinking -> Pose.Thinking
    }
}

/** Procedural motion on top of the blended mood pose. Times in seconds. */
private class Motion {
    var blended = Pose.Neutral
    var facing = 1f
    var landedAt = -10f
    var pokedAt = -10f
    var dragVx = 0f
    private var lean = 0f

    // Where the finger is, from her: set by [aimAt].
    var aimFacing = 1f
    /** Right-arm angle (rig convention: 0 hangs down, 90 straight out, 180 up) toward the finger. */
    var aimAngle = Pose.REST_ARM
    /** Gaze toward the finger: x away from her along [aimFacing], y down. */
    var gazeX = 0f
    var gazeY = 0f
    private var swing = 0f

    /** Aims at [finger] (layer px) for a rig of [u] px per unit standing at [footX], [footY]. */
    fun aimAt(finger: Offset, footX: Float, footY: Float, u: Float, density: Float) {
        val dx = finger.x - footX
        if (abs(dx) > 4f * density) aimFacing = sign(dx)
        // Right shoulder (50 + 11.5, 87.5 in the 100 x 140 box, feet at 138), mirrored with her.
        val shoulder = Offset(footX + aimFacing * 11.5f * u, footY - 50.5f * u)
        val v = finger - shoulder
        aimAngle = (atan2(abs(v.x), v.y) * 180f / PI.toFloat()).coerceIn(0f, 175f)
        val g = finger - Offset(footX, footY - 83f * u) // eyes
        val len = g.getDistance().coerceAtLeast(1f)
        gazeX = abs(g.x) / len
        gazeY = g.y / len
    }

    /** [speaking]: her voice is on, so her mouth moves (unless she's asleep). */
    fun animate(brain: MascotBrain, t: Float, tilt: Float, speaking: Boolean = false): Pose {
        var p = blended
        // Swings ease in so the first one doesn't snap.
        swing += ((if (brain.mood == Mood.Duel) 1f else 0f) - swing) * 0.15f
        // Breathing and a lazy ahoge.
        p = p.copy(squash = p.squash * (1f + 0.012f * sin(t * 2.4f)), ahoge = p.ahoge + 5f * sin(t * 1.7f))
        // Blink for 130 ms every ~4 s (open eyes only).
        if (p.eyes == Eyes.Open && (t % 4.1f) < 0.13f) p = p.copy(eyes = Eyes.Closed)
        when (brain.mood) {
            Mood.Chase -> {
                val s = sin(t * 16f)
                p = p.copy(legLeft = 30f * s, legRight = -30f * s, armLeft = Pose.REST_ARM - 20f * s, lift = abs(s) * 2.4f, bodyTilt = 8f * facing)
            }
            Mood.Leap -> p = p.copy(legLeft = 35f, legRight = 15f, bodyTilt = 6f * facing)
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
            Mood.Wander -> {
                val s = sin(t * 8f)
                p = p.copy(legLeft = 16f * s, legRight = -16f * s, armLeft = Pose.REST_ARM - 10f * s, armRight = Pose.REST_ARM + 10f * s, lift = abs(s) * 1.1f)
            }
            Mood.Dancing -> {
                // ~110 bpm bob: a step every beat, a sway every two.
                val beat = t * (110f / 60f) * 2f * PI.toFloat()
                p = p.copy(
                    lift = 3f * abs(sin(beat / 2f)), bodyTilt = 8f * sin(beat / 2f), headTilt = -8f * sin(beat / 2f),
                    armLeft = 40f + 25f * sin(beat), armRight = 40f - 25f * sin(beat),
                    legLeft = 8f * sin(beat / 2f), legRight = -8f * sin(beat / 2f),
                )
            }
            Mood.Duel -> {
                // Playful swishes across the finger: the blade flicks further than the arm.
                val s = sin(t * 17f)
                p = p.copy(
                    armRight = p.armRight + 22f * s * swing, swordAngle = p.swordAngle - 48f * s * swing,
                    bodyTilt = 4f * s * swing, lift = 2f * abs(sin(t * 8.5f)) * swing,
                )
            }
            Mood.Point -> p = p.copy(armRight = p.armRight + 2.5f * sin(t * 4.5f))
            Mood.Talking -> p = p.copy(headTilt = p.headTilt + 2f * sin(t * 2.2f), armRight = p.armRight + 8f * sin(t * 1.6f))
            Mood.Sleeping -> p = p.copy(headTilt = p.headTilt + 3f * sin(t * 1.3f), squash = p.squash * (1f + 0.02f * sin(t * 1.3f)))
            Mood.Moment -> when (brain.moment) {
                MascotBrain.Moment.LookAround -> p = p.copy(headTurn = 0.8f * sin(t * 1.8f), headTilt = 4f * sin(t * 0.9f))
                MascotBrain.Moment.Sit -> p = p.copy(legLeft = 55f + 14f * sin(t * 3f), legRight = 45f + 14f * sin(t * 3f + 1.5f))
                else -> Unit
            }
            else -> Unit
        }
        // Syllables at ~6 Hz; the wobbling phase keeps them from ticking like a metronome.
        if (speaking && brain.mood != Mood.Sleeping) {
            val syllable = t * 12f + 0.8f * sin(t * 2.7f) + 0.5f * sin(t * 7.3f)
            p = p.copy(mouth = if (syllable.toInt() % 2 == 0) Mouth.Open else Mouth.Smile)
        }
        if (brain.mood != Mood.Held) {
            lean = 0f
            if (brain.mood != Mood.Falling) p = p.copy(bodyTilt = p.bodyTilt - tilt * 14f, ahoge = p.ahoge + tilt * 22f)
        }
        // Landing squash: a quick spring.
        val sinceLanding = t - landedAt
        if (sinceLanding in 0f..0.7f) p = p.copy(squash = p.squash * (1f - 0.2f * exp(-sinceLanding * 8f) * cos(sinceLanding * 20f)))
        // Poke flinch.
        val sincePoke = t - pokedAt
        if (sincePoke in 0f..0.4f) p = p.copy(squash = p.squash * (1f - 0.1f * exp(-sincePoke * 10f) * cos(sincePoke * 25f)))
        return p.copy(facing = facing)
    }
}
