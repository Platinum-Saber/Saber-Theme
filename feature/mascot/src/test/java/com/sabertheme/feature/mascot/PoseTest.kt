package com.sabertheme.feature.mascot

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PoseTest {
    @Test
    fun approachSwitchesTheExpressionAtOnceAndEasesNumbers() {
        val p = Pose.Neutral.approach(Pose.Dizzy, 0.1f)
        assertThat(p.eyes).isEqualTo(Eyes.Dizzy)
        assertThat(p.dizzy).isTrue()
        assertThat(p.ahoge).isWithin(0.01f).of(Pose.Dizzy.ahoge * 0.1f)
    }

    @Test
    fun lerpSwitchesVariantsAtHalfway() {
        assertThat(Pose.Neutral.lerp(Pose.Crying, 0.49f).tears).isFalse()
        assertThat(Pose.Neutral.lerp(Pose.Crying, 0.5f).tears).isTrue()
    }
}
