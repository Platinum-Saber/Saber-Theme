package com.sabertheme.feature.mascot

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MascotPhysicsTest {
    private val dp = 3f
    private val physics = MascotPhysics(dp).apply {
        minX = 30f; maxX = 1050f; floor = 1500f; ceiling = 300f
        place(800f, 1500f)
    }

    private fun runUntilLanded(maxSteps: Int = 2_000): Float? {
        repeat(maxSteps) { physics.step(1f / 120f)?.let { return it } }
        return null
    }

    @Test
    fun droppedFromAHeightFallsBouncesAndSettlesOnTheFloor() {
        physics.place(500f, 700f)
        physics.throwAt(0f, 0f)
        val impact = runUntilLanded()
        assertThat(impact).isNotNull()
        // Later bounces don't report again; she then settles on the floor.
        repeat(2_000) { assertThat(physics.step(1f / 120f)).isNull() }
        assertThat(physics.y).isEqualTo(1500f)
        assertThat(physics.airborne).isFalse()
        // Fell 800 px = 267 dp: v = sqrt(2 g h) ~ 1178 dp/s.
        assertThat(impact!!).isWithin(60f).of(1178f)
    }

    @Test
    fun wallsReflectAndKeepHerOnScreen() {
        physics.place(100f, 1000f)
        physics.throwAt(-3000f * dp, 0f)
        repeat(3) {
            physics.step(1f / 120f)
            assertThat(physics.x).isAtLeast(30f)
        }
        // Reflected off the left wall, slower.
        assertThat(physics.vx).isWithin(1f).of(3000f * dp * MascotPhysics.WALL_BOUNCE)
    }

    @Test
    fun ceilingStopsAnUpwardThrow() {
        physics.place(500f, 400f)
        physics.throwAt(0f, -4000f * dp)
        repeat(20) {
            physics.step(1f / 120f)
            assertThat(physics.y).isAtLeast(300f)
        }
        assertThat(runUntilLanded()).isNotNull()
    }

    @Test
    fun throwSpeedIsCapped() {
        physics.throwAt(1e9f, -1e9f)
        assertThat(physics.vx).isEqualTo(MascotPhysics.MAX_SPEED * dp)
        assertThat(physics.vy).isEqualTo(-MascotPhysics.MAX_SPEED * dp)
    }

    @Test
    fun walksAtTheGivenSpeedAndStopsAtTheTarget() {
        assertThat(physics.walkToward(900f, speedDp = 40f, dt = 0.5f)).isFalse()
        assertThat(physics.x).isEqualTo(860f) // 40 dp/s * 0.5 s * 3 px/dp = 60 px
        assertThat(physics.walkToward(900f, speedDp = 40f, dt = 1f)).isTrue()
        assertThat(physics.x).isEqualTo(900f)
    }
}
