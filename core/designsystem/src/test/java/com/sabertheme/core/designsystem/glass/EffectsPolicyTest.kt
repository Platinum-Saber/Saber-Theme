package com.sabertheme.core.designsystem.glass

import com.google.common.truth.Truth.assertThat
import com.sabertheme.core.designsystem.glass.EffectsPolicy.Mode
import com.sabertheme.core.designsystem.glass.EffectsPolicy.SensorRate
import org.junit.Test

class EffectsPolicyTest {
    private val policy = EffectsPolicy().apply { resumed = true }

    @Test
    fun touchMakesActiveAndIdlesOutAfterThreeSeconds() {
        policy.onInteraction(1_000)
        assertThat(policy.evaluate(1_000).mode).isEqualTo(Mode.Active)
        assertThat(policy.evaluate(1_000).sensor).isEqualTo(SensorRate.Fast)
        assertThat(policy.evaluate(3_999).mode).isEqualTo(Mode.Active)
        val idle = policy.evaluate(4_000)
        assertThat(idle.mode).isEqualTo(Mode.Idle)
        // Tilt keeps following while home is visible, just slower.
        assertThat(idle.sensor).isEqualTo(SensorRate.Slow)
        assertThat(policy.idleAtMs()).isEqualTo(4_000)
    }

    @Test
    fun idleKeepsFullIntensitySoGlassDoesNotVisiblyChange() {
        policy.userIntensity = 0.8f
        assertThat(policy.evaluate(10_000).intensity).isEqualTo(0.8f)
    }

    @Test
    fun powerSaveTurnsSensorsOffAndHalvesIntensity() {
        policy.onInteraction(0)
        policy.powerSave = true
        val s = policy.evaluate(10)
        assertThat(s.mode).isEqualTo(Mode.Saver)
        assertThat(s.sensorsOn).isFalse()
        assertThat(s.intensity).isEqualTo(0.5f)
    }

    @Test
    fun thermalBeatsPowerSaveAndReducedMotionBeatsAll() {
        policy.onInteraction(0)
        policy.powerSave = true
        policy.thermalThrottled = true
        assertThat(policy.evaluate(10).mode).isEqualTo(Mode.Throttled)
        policy.reducedMotion = true
        val s = policy.evaluate(10)
        assertThat(s.mode).isEqualTo(Mode.Reduced)
        assertThat(s.sensorsOn).isFalse()
    }

    @Test
    fun pausedLauncherIsIdle() {
        policy.onInteraction(0)
        policy.resumed = false
        assertThat(policy.evaluate(10).sensorsOn).isFalse()
    }

    @Test
    fun zeroUserIntensityKeepsSensorsOff() {
        policy.userIntensity = 0f
        policy.onInteraction(0)
        assertThat(policy.evaluate(10).sensorsOn).isFalse()
    }
}
