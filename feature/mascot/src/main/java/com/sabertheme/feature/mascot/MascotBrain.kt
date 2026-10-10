package com.sabertheme.feature.mascot

import kotlin.random.Random

/**
 * What she is doing and feeling. Pure Kotlin; see MascotBrainTest. Times are
 * milliseconds from any monotonic clock. The layer turns [mood] (and
 * [moment]) into a pose and adds the procedural motion.
 */
class MascotBrain(private val random: Random = Random.Default) {
    enum class Mood {
        Idle, Surprised, Pout, Crying, Happy, Held, Falling, SoftLanding, Dizzy, Walking,
        /** A short idle animation, see [moment]. */
        Moment,
        /** Strolling to [wanderDp] from her spot; [arrived] ends it. */
        Wander,
        Sleeping,
        Dancing,
    }

    enum class Moment { LookAround, Stretch, SwordPractice, Sit, HeartHands, Curious, Thinking }

    var mood = Mood.Idle
        private set
    var moment = Moment.LookAround
        private set

    /** Wander target, in dp from her spot on the bar. */
    var wanderDp = 0f
        private set

    /** When the current mood started. */
    var since = 0L
        private set
    private var until = Long.MAX_VALUE
    private var nextMomentAt = -1L
    private val pokes = ArrayDeque<Long>()

    /** Set by the layer: is she away from her spot on the bar? */
    var awayFromHome = false

    private val busy get() = mood == Mood.Held || mood == Mood.Falling

    fun poke(now: Long) {
        if (busy) return
        pokes.addLast(now)
        while (pokes.isNotEmpty() && now - pokes.first() > POKE_WINDOW_MS) pokes.removeFirst()
        when {
            pokes.size >= CRY_POKES -> set(Mood.Crying, now, CRY_MS)
            pokes.size >= POUT_POKES -> set(Mood.Pout, now, POUT_MS)
            mood == Mood.Crying || mood == Mood.Pout -> Unit // keep sulking; more pokes escalate
            else -> set(Mood.Surprised, now, SURPRISE_MS)
        }
    }

    fun longPress(now: Long) {
        if (busy) return
        pokes.clear()
        set(Mood.Happy, now, HAPPY_MS)
    }

    fun grab(now: Long) {
        pokes.clear()
        set(Mood.Held, now, null)
    }

    fun release(now: Long) {
        if (mood == Mood.Held) set(Mood.Falling, now, null)
    }

    /** She hit the bar (first contact of a fall) at peak speed [impactDp] dp/s. */
    fun land(impactDp: Float, now: Long) {
        when {
            impactDp >= CRY_IMPACT -> set(Mood.Crying, now, CRY_MS)
            impactDp >= DIZZY_IMPACT -> set(Mood.Dizzy, now, DIZZY_MS)
            else -> set(Mood.SoftLanding, now, SOFT_LANDING_MS)
        }
    }

    /**
     * Advances time. [quietForMs] is how long Home has gone without a touch;
     * [music] is whether a media session is playing.
     */
    fun tick(now: Long, quietForMs: Long = 0L, music: Boolean = false) {
        if (now >= until) set(if (awayFromHome) Mood.Walking else Mood.Idle, now, null)
        when (mood) {
            Mood.Sleeping -> when {
                quietForMs < WAKE_TOUCH_MS -> set(Mood.Surprised, now, SURPRISE_MS) // woken with a start
                music -> set(Mood.Dancing, now, null)
            }
            Mood.Dancing -> if (!music) set(Mood.Idle, now, null)
            Mood.Idle, Mood.Moment -> when {
                music && !awayFromHome -> set(Mood.Dancing, now, null)
                mood == Mood.Idle && awayFromHome -> set(Mood.Walking, now, null)
                mood == Mood.Idle && quietForMs >= SLEEP_AFTER_MS -> set(Mood.Sleeping, now, null)
                mood == Mood.Idle && now >= nextMomentAt -> startMoment(now)
            }
            else -> Unit
        }
    }

    /** Called by the layer when a walk (home or wander) reaches its target. */
    fun arrived(now: Long) {
        if (mood == Mood.Walking || mood == Mood.Wander) set(Mood.Idle, now, null)
    }

    private fun startMoment(now: Long) {
        if (nextMomentAt < 0) {
            // First idle after start: wait before the first moment.
            nextMomentAt = now + gap()
            return
        }
        if (random.nextFloat() < WANDER_CHANCE) {
            wanderDp = (if (random.nextBoolean()) 1f else -1f) * (40f + random.nextFloat() * 80f)
            set(Mood.Wander, now, null)
        } else {
            moment = Moment.entries[random.nextInt(Moment.entries.size)]
            set(Mood.Moment, now, momentMs(moment))
        }
        nextMomentAt = now + gap()
    }

    private fun gap() = MOMENT_GAP_MIN_MS + random.nextLong(MOMENT_GAP_MAX_MS - MOMENT_GAP_MIN_MS)

    private fun set(m: Mood, now: Long, durationMs: Long?) {
        mood = m
        since = now
        until = if (durationMs == null) Long.MAX_VALUE else now + durationMs
    }

    companion object {
        const val POKE_WINDOW_MS = 2_000L
        const val POUT_POKES = 3
        const val CRY_POKES = 5
        const val SURPRISE_MS = 900L
        const val POUT_MS = 2_200L
        const val CRY_MS = 3_000L
        const val HAPPY_MS = 2_000L
        const val SOFT_LANDING_MS = 600L
        const val DIZZY_MS = 2_500L

        // A plain drop from the top of the screen reaches ~1500 dp/s; crying needs a hard throw down.
        const val DIZZY_IMPACT = 700f
        const val CRY_IMPACT = 1_700f

        const val SLEEP_AFTER_MS = 60_000L
        /** A touch this recent wakes her. */
        const val WAKE_TOUCH_MS = 1_000L
        const val MOMENT_GAP_MIN_MS = 8_000L
        const val MOMENT_GAP_MAX_MS = 20_000L
        const val WANDER_CHANCE = 0.25f

        fun momentMs(m: Moment): Long = when (m) {
            Moment.LookAround -> 2_600L
            Moment.Stretch -> 1_800L
            Moment.SwordPractice -> 2_600L
            Moment.Sit -> 6_000L
            Moment.HeartHands -> 2_200L
            Moment.Curious -> 2_000L
            Moment.Thinking -> 3_000L
        }
    }
}
