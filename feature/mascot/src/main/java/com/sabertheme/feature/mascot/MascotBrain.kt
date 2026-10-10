package com.sabertheme.feature.mascot

/**
 * What she is doing and feeling. Pure Kotlin; see MascotBrainTest. Times are
 * milliseconds from any monotonic clock. The layer turns [mood] into a pose
 * and adds the procedural motion (walk cycle, dangling, landing squash).
 */
class MascotBrain {
    enum class Mood { Idle, Surprised, Pout, Crying, Happy, Held, Falling, SoftLanding, Dizzy, Walking }

    var mood = Mood.Idle
        private set

    /** When the current timed mood started / ends ([Long.MAX_VALUE]: until an event). */
    var since = 0L
        private set
    private var until = Long.MAX_VALUE
    private val pokes = ArrayDeque<Long>()

    /** Set by the layer: is she away from her spot on the bar? */
    var awayFromHome = false

    fun poke(now: Long) {
        if (mood == Mood.Held || mood == Mood.Falling) return
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
        if (mood == Mood.Held || mood == Mood.Falling) return
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

    /** Ends timed moods; then she walks home if she was moved, else idles. */
    fun tick(now: Long) {
        if (now >= until) set(if (awayFromHome) Mood.Walking else Mood.Idle, now, null)
        if (mood == Mood.Idle && awayFromHome) set(Mood.Walking, now, null)
    }

    /** Called by the layer when the walk reaches her spot. */
    fun arrived(now: Long) {
        if (mood == Mood.Walking) set(Mood.Idle, now, null)
    }

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
        /** Landing speeds (dp/s) for the reactions. */
        // A plain drop from the top of the screen reaches ~1500 dp/s; crying needs a hard throw down.
        const val DIZZY_IMPACT = 700f
        const val CRY_IMPACT = 1_700f
    }
}
