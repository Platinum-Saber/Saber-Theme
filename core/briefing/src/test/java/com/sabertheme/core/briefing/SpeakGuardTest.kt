package com.sabertheme.core.briefing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeakGuardTest {

    private fun may(
        hour: Int = 9,
        ringer: Boolean = true,
        all: Boolean = true,
        music: Boolean = false,
        recorded: Boolean = false,
        visible: Boolean = true,
    ) = SpeakGuard.mayAnnounce(hour, ringer, all, music, recorded, visible)

    @Test
    fun `speaks on a normal daytime Home`() {
        assertThat(may()).isTrue()
    }

    @Test
    fun `silent, vibrate, DND, music, recording and a hidden Home all keep her quiet`() {
        assertThat(may(ringer = false)).isFalse()
        assertThat(may(all = false)).isFalse()
        assertThat(may(music = true)).isFalse()
        assertThat(may(recorded = true)).isFalse()
        assertThat(may(visible = false)).isFalse()
    }

    @Test
    fun `quiet hours run from 22 to 7`() {
        assertThat(may(hour = 21)).isTrue()
        assertThat(may(hour = 22)).isFalse()
        assertThat(may(hour = 3)).isFalse()
        assertThat(may(hour = 6)).isFalse()
        assertThat(may(hour = 7)).isTrue()
    }
}
