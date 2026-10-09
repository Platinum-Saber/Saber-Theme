package com.sabertheme.core.designsystem.glass

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring

/** Named springs. "Nothing teleports": every state change uses one of these. */
object GlassMotion {
    /** Finger down: quick, no bounce. */
    fun <T> press(reduced: Boolean = false): AnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 1f, stiffness = 1400f)

    /** Finger up: slight overshoot past rest. */
    fun <T> release(reduced: Boolean = false): AnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.6f, stiffness = 500f)

    fun <T> page(reduced: Boolean = false): AnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)

    fun <T> sheet(reduced: Boolean = false): AnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.82f, stiffness = 420f)

    fun <T> morph(reduced: Boolean = false): AnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = 0.78f, stiffness = 380f)

    /** Tilt-driven values chase the sensor with this. */
    fun <T> follow(): AnimationSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow)
}
