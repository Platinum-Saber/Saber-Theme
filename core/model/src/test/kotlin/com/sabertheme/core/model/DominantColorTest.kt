package com.sabertheme.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DominantColorTest {
    private fun pixels(vararg runs: Pair<Int, Int>) =
        runs.flatMap { (color, count) -> List(count) { color } }.toIntArray()

    @Test
    fun greenLogoOnWhitePlateIsGreen() {
        val green = 0xFF25D366.toInt()
        val result = DominantColor.of(pixels(0xFFFFFFFF.toInt() to 700, green to 300))
        assertThat(result).isEqualTo(green)
    }

    @Test
    fun saturatedColourBeatsALargerDullOne() {
        val red = 0xFFE53935.toInt()
        val dullBlue = 0xFF7080A0.toInt()
        assertThat(DominantColor.of(pixels(dullBlue to 500, red to 300))).isEqualTo(red)
    }

    @Test
    fun greyscaleIconHasNoColour() {
        val result = DominantColor.of(pixels(0xFF000000.toInt() to 300, 0xFF808080.toInt() to 300, 0xFFFFFFFF.toInt() to 400))
        assertThat(result).isNull()
    }

    @Test
    fun transparentPixelsAreIgnored() {
        val blue = 0xFF1E88E5.toInt()
        val faintRed = 0x20FF0000
        assertThat(DominantColor.of(pixels(faintRed to 900, blue to 100))).isEqualTo(blue)
        assertThat(DominantColor.of(IntArray(64))).isNull()
    }

    @Test
    fun aFewColourfulPixelsDoNotTintAGreyIcon() {
        val result = DominantColor.of(pixels(0xFF808080.toInt() to 990, 0xFFFF0000.toInt() to 10))
        assertThat(result).isNull()
    }
}
