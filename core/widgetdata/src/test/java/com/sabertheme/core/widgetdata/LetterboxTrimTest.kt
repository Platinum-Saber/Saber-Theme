package com.sabertheme.core.widgetdata

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LetterboxTrimTest {
    /** A [w]x[h] image that is bright inside [l, t, r, b) and black outside. */
    private fun image(w: Int, h: Int, l: Int, t: Int, r: Int, b: Int) = { x: Int, y: Int ->
        if (x in l until r && y in t until b) 180 else 4
    }

    @Test
    fun trimsTopAndBottomBars() {
        val bounds = LetterboxTrim.bounds(512, 512, image(512, 512, 0, 144, 512, 368))
        assertThat(bounds.toList()).containsExactly(0, 144, 512, 368).inOrder()
    }

    @Test
    fun trimsPillarbox() {
        val bounds = LetterboxTrim.bounds(400, 300, image(400, 300, 50, 0, 350, 300))
        assertThat(bounds.toList()).containsExactly(50, 0, 350, 300).inOrder()
    }

    @Test
    fun keepsImagesWithoutBars() {
        val bounds = LetterboxTrim.bounds(300, 300) { _, _ -> 120 }
        assertThat(bounds.toList()).containsExactly(0, 0, 300, 300).inOrder()
    }

    @Test
    fun keepsMostlyDarkImagesWhole() {
        // A small bright spot on black is a dark picture, not a letterbox.
        val bounds = LetterboxTrim.bounds(300, 300, image(300, 300, 140, 140, 160, 160))
        assertThat(bounds.toList()).containsExactly(0, 0, 300, 300).inOrder()
    }
}
