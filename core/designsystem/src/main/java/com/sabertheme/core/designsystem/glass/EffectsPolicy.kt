package com.sabertheme.core.designsystem.glass

/**
 * Decides how alive the glass may be. Pure Kotlin; the Android glue
 * ([EffectsController]) feeds it events and timestamps.
 *
 * - Active: full effects, tilt sensor on.
 * - Idle: no touch for [idleTimeoutMs]; sensor off, glass keeps its look.
 * - Saver / Throttled: Power Saving or thermal pressure; sensor off, effects halved.
 * - Reduced: "Remove animations"; sensor off, springs snap.
 */
class EffectsPolicy(private val idleTimeoutMs: Long = IDLE_TIMEOUT_MS) {

    enum class Mode { Active, Idle, Saver, Throttled, Reduced }

    data class State(val mode: Mode, val intensity: Float, val sensorsOn: Boolean)

    var userIntensity: Float = 1f
        set(value) { field = value.coerceIn(0f, 1f) }
    var powerSave = false
    var thermalThrottled = false
    var reducedMotion = false
    var resumed = false
    private var lastInteractionMs = Long.MIN_VALUE / 2

    fun onInteraction(nowMs: Long) {
        lastInteractionMs = nowMs
    }

    /** Time at which the current Active period ends, for scheduling the next evaluation. */
    fun idleAtMs(): Long = lastInteractionMs + idleTimeoutMs

    fun evaluate(nowMs: Long): State {
        val mode = when {
            reducedMotion -> Mode.Reduced
            thermalThrottled -> Mode.Throttled
            powerSave -> Mode.Saver
            !resumed || nowMs - lastInteractionMs >= idleTimeoutMs -> Mode.Idle
            else -> Mode.Active
        }
        val intensity = when (mode) {
            Mode.Saver, Mode.Throttled -> userIntensity * REDUCED_FACTOR
            else -> userIntensity
        }
        return State(mode, intensity, sensorsOn = mode == Mode.Active && userIntensity > 0f)
    }

    companion object {
        const val IDLE_TIMEOUT_MS = 3_000L
        const val REDUCED_FACTOR = 0.5f
    }
}
