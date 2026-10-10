package com.sabertheme.feature.mascot

import kotlin.math.abs
import kotlin.math.sign

/**
 * Where her feet are and how she moves, in px. Pure Kotlin; see
 * MascotPhysicsTest. The floor is the top of the search bar across the whole
 * width; walls are the layer edges (keeping her whole body on screen) and the
 * ceiling stops her head at the top.
 */
class MascotPhysics(private val dp: Float) {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var airborne = false
        private set

    // Set every frame from the layout.
    var minX = 0f
    var maxX = 0f
    var floor = 0f
    var ceiling = 0f

    /** Fastest downward speed of the current fall, for the landing reaction (dp/s). */
    private var peakFall = 0f
    private var hitFloor = false

    fun place(x: Float, y: Float) {
        this.x = x
        this.y = y
        vx = 0f
        vy = 0f
        airborne = false
    }

    /** Let go at [vx], [vy] (px/s): she flies, bounces off walls and falls to the bar. */
    fun throwAt(vx: Float, vy: Float) {
        this.vx = vx.coerceIn(-MAX_SPEED * dp, MAX_SPEED * dp)
        this.vy = vy.coerceIn(-MAX_SPEED * dp, MAX_SPEED * dp)
        airborne = true
        peakFall = 0f
        hitFloor = false
    }

    /**
     * Advances [dt] seconds. Returns the impact speed in dp/s on her first
     * contact with the floor after a throw (she may still bounce), else null.
     */
    fun step(dt: Float): Float? {
        if (!airborne) return null
        vy += GRAVITY * dp * dt
        x += vx * dt
        y += vy * dt
        if (x < minX) { x = minX; vx = -vx * WALL_BOUNCE }
        if (x > maxX) { x = maxX; vx = -vx * WALL_BOUNCE }
        if (y < ceiling) { y = ceiling; vy = abs(vy) * WALL_BOUNCE }
        if (vy > 0f) peakFall = maxOf(peakFall, vy / dp)
        if (y >= floor && vy >= 0f) {
            y = floor
            val impact = if (hitFloor) null else peakFall
            hitFloor = true
            if (vy / dp > SETTLE_SPEED) {
                vy = -vy * FLOOR_BOUNCE
                vx *= FLOOR_FRICTION
            } else {
                vx = 0f
                vy = 0f
                airborne = false
            }
            return impact
        }
        return null
    }

    /** Walks toward [targetX] at [speedDp] dp/s on the floor; returns true once there. */
    fun walkToward(targetX: Float, speedDp: Float, dt: Float): Boolean {
        if (airborne) return false
        val d = targetX - x
        val stepPx = speedDp * dp * dt
        if (abs(d) <= stepPx) {
            x = targetX
            return true
        }
        x += sign(d) * stepPx
        return false
    }

    companion object {
        const val GRAVITY = 2600f
        const val MAX_SPEED = 4200f
        const val WALL_BOUNCE = 0.55f
        const val FLOOR_BOUNCE = 0.32f
        const val FLOOR_FRICTION = 0.6f
        /** Below this bounce speed (dp/s) she stops bouncing and stands. */
        const val SETTLE_SPEED = 260f
    }
}
