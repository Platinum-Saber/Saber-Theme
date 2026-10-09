package com.sabertheme.core.designsystem.glass

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TintSolverTest {
    private val ink = 0xFF0E0F12.toInt()
    private val paper = 0xFFF5F6F8.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val darkTint = 0xFF1A1C22.toInt()

    private fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    @Test
    fun lightThemeKeepsInkReadableOnEveryBackdrop() = assertContrastAcrossLuminance(text = ink, tint = white, minAlpha = 0.14f)

    @Test
    fun darkThemeKeepsPaperReadableOnEveryBackdrop() = assertContrastAcrossLuminance(text = paper, tint = darkTint, minAlpha = 0.2f)

    private fun assertContrastAcrossLuminance(text: Int, tint: Int, minAlpha: Float) {
        for (v in 0..255 step 5) {
            val bg = grey(v)
            val a = TintSolver.tintAlpha(bg, tint, text, minAlpha)
            assertThat(a).isAtLeast(minAlpha)
            assertThat(a).isAtMost(TintSolver.MAX_ALPHA)
            val glass = TintSolver.mix(bg, tint, a)
            val c = TintSolver.contrast(TintSolver.luminance(glass), TintSolver.luminance(text))
            assertThat(c).isAtLeast(TintSolver.MIN_CONTRAST)
        }
    }

    @Test
    fun alreadyLegibleBackdropKeepsMaterialAlpha() {
        assertThat(TintSolver.tintAlpha(grey(240), white, ink, 0.14f)).isEqualTo(0.14f)
    }

    @Test
    fun brightBackdropUnderLightTextRaisesTint() {
        assertThat(TintSolver.tintAlpha(grey(250), darkTint, paper, 0.2f)).isGreaterThan(0.6f)
    }
}
