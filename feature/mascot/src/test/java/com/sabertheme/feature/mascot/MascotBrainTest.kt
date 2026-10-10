package com.sabertheme.feature.mascot

import com.google.common.truth.Truth.assertThat
import com.sabertheme.feature.mascot.MascotBrain.Mood
import org.junit.Test

class MascotBrainTest {
    private val brain = MascotBrain()

    @Test
    fun onePokeSurprisesThenReturnsToIdle() {
        brain.poke(0)
        assertThat(brain.mood).isEqualTo(Mood.Surprised)
        brain.tick(MascotBrain.SURPRISE_MS - 1)
        assertThat(brain.mood).isEqualTo(Mood.Surprised)
        brain.tick(MascotBrain.SURPRISE_MS)
        assertThat(brain.mood).isEqualTo(Mood.Idle)
    }

    @Test
    fun quickPokesEscalateToPoutThenCrying() {
        brain.poke(0); brain.poke(300); brain.poke(600)
        assertThat(brain.mood).isEqualTo(Mood.Pout)
        brain.poke(900)
        assertThat(brain.mood).isEqualTo(Mood.Pout)
        brain.poke(1_200)
        assertThat(brain.mood).isEqualTo(Mood.Crying)
    }

    @Test
    fun slowPokesDoNotEscalate() {
        brain.poke(0); brain.poke(2_500); brain.poke(5_000)
        assertThat(brain.mood).isEqualTo(Mood.Surprised)
    }

    @Test
    fun longPressMakesHerHappy() {
        brain.longPress(0)
        assertThat(brain.mood).isEqualTo(Mood.Happy)
        brain.tick(MascotBrain.HAPPY_MS)
        assertThat(brain.mood).isEqualTo(Mood.Idle)
    }

    @Test
    fun grabReleaseFallAndLandingReactions() {
        brain.grab(0)
        assertThat(brain.mood).isEqualTo(Mood.Held)
        brain.poke(10)
        assertThat(brain.mood).isEqualTo(Mood.Held)
        brain.release(100)
        assertThat(brain.mood).isEqualTo(Mood.Falling)
        brain.land(500f, 400)
        assertThat(brain.mood).isEqualTo(Mood.SoftLanding)
        brain.land(MascotBrain.DIZZY_IMPACT, 500)
        assertThat(brain.mood).isEqualTo(Mood.Dizzy)
        brain.land(MascotBrain.CRY_IMPACT, 600)
        assertThat(brain.mood).isEqualTo(Mood.Crying)
    }

    @Test
    fun walksHomeAfterAReactionWhenMoved() {
        brain.land(100f, 0)
        brain.awayFromHome = true
        brain.tick(MascotBrain.SOFT_LANDING_MS)
        assertThat(brain.mood).isEqualTo(Mood.Walking)
        brain.arrived(2_000)
        brain.awayFromHome = false
        brain.tick(2_001)
        assertThat(brain.mood).isEqualTo(Mood.Idle)
    }

    @Test
    fun fallsAsleepWhenHomeIsQuietAndWakesWithAStartOnTouch() {
        brain.tick(0, quietForMs = 0)
        brain.tick(1_000, quietForMs = MascotBrain.SLEEP_AFTER_MS)
        assertThat(brain.mood).isEqualTo(Mood.Sleeping)
        brain.tick(2_000, quietForMs = 30)
        assertThat(brain.mood).isEqualTo(Mood.Surprised)
    }

    @Test
    fun dancesWhileMusicPlaysAndStopsAfter() {
        brain.tick(0, music = true)
        assertThat(brain.mood).isEqualTo(Mood.Dancing)
        brain.tick(10_000, quietForMs = MascotBrain.SLEEP_AFTER_MS * 2, music = true)
        assertThat(brain.mood).isEqualTo(Mood.Dancing) // no sleeping through music
        brain.tick(11_000, music = false)
        assertThat(brain.mood).isEqualTo(Mood.Idle)
    }

    @Test
    fun idleMomentsComeAfterAGapAndEnd() {
        val seeded = MascotBrain(kotlin.random.Random(7))
        seeded.tick(0)
        assertThat(seeded.mood).isEqualTo(Mood.Idle)
        seeded.tick(MascotBrain.MOMENT_GAP_MIN_MS - 1)
        assertThat(seeded.mood).isEqualTo(Mood.Idle)
        var t = 0L
        while (seeded.mood == Mood.Idle && t < MascotBrain.MOMENT_GAP_MAX_MS + 1) { t += 100; seeded.tick(t) }
        assertThat(seeded.mood).isAnyOf(Mood.Moment, Mood.Wander)
        if (seeded.mood == Mood.Moment) {
            seeded.tick(t + MascotBrain.momentMs(seeded.moment))
        } else {
            assertThat(kotlin.math.abs(seeded.wanderDp)).isAtLeast(40f)
            seeded.arrived(t + 1)
        }
        assertThat(seeded.mood).isEqualTo(Mood.Idle)
    }

    @Test
    fun pokesStillWorkWhileSleeping() {
        brain.tick(0)
        brain.tick(1, quietForMs = MascotBrain.SLEEP_AFTER_MS)
        brain.poke(2)
        assertThat(brain.mood).isEqualTo(Mood.Surprised)
    }
}
