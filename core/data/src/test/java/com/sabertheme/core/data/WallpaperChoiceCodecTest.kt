package com.sabertheme.core.data

import com.google.common.truth.Truth.assertThat
import com.sabertheme.core.data.SettingsRepository.Companion.decode
import com.sabertheme.core.data.SettingsRepository.Companion.encode
import com.sabertheme.core.model.WallpaperChoice
import org.junit.Test

class WallpaperChoiceCodecTest {
    @Test
    fun roundTrips() {
        for (choice in listOf(WallpaperChoice.Bundled("aurora-dawn"), WallpaperChoice.Photo("a-b.webp"))) {
            assertThat(decode(encode(choice))).isEqualTo(choice)
        }
    }

    @Test
    fun missingOrUnknownFallsBackToDefault() {
        assertThat(decode(null)).isEqualTo(WallpaperChoice.Default)
        assertThat(decode("video:x")).isEqualTo(WallpaperChoice.Default)
    }
}
