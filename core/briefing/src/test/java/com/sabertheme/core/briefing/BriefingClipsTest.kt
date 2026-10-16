package com.sabertheme.core.briefing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BriefingClipsTest {

    @Test
    fun `same text names the same clip, different text a different one`() {
        val a = BriefingClips.name("Notice. One matter requires your attention, Master.")
        assertThat(BriefingClips.name("Notice. One matter requires your attention, Master.")).isEqualTo(a)
        assertThat(BriefingClips.name("Report. There are no pending matters, Master.")).isNotEqualTo(a)
        assertThat(a).matches("[0-9a-f]{16}")
    }
}
