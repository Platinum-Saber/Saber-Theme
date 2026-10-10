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
}
