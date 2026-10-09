package com.sabertheme.feature.drawer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsSearchTest {
    private fun titles(q: String) = SettingsSearch.search(q, limit = 5).map { it.title }

    @Test
    fun titlePrefix() {
        assertThat(titles("blue")).containsExactly("Bluetooth")
        assertThat(titles("Bat").first()).isEqualTo("Battery")
    }

    @Test
    fun wifiSpellings() {
        assertThat(titles("wifi")).contains("Wi-Fi")
        assertThat(titles("wi-fi")).contains("Wi-Fi")
        assertThat(titles("wi")).contains("Wi-Fi")
    }

    @Test
    fun keywords() {
        assertThat(titles("brightness")).containsExactly("Display")
        assertThat(titles("fingerprint")).containsExactly("Security")
        assertThat(titles("launcher")).containsExactly("Default home app")
    }

    @Test
    fun everyWordMustMatch() {
        assertThat(titles("about phone")).containsExactly("About phone")
        assertThat(titles("lock screen")).containsExactly("Security")
        assertThat(titles("phone wifi")).isEmpty()
    }

    @Test
    fun titleMatchesOutrankKeywordMatches() {
        // "home" is in the title of "Default home app"; no other entry has it at all.
        assertThat(titles("home").first()).isEqualTo("Default home app")
        assertThat(titles("a").first()).isEqualTo("Apps")
    }

    @Test
    fun blankAndUnknown() {
        assertThat(titles("")).isEmpty()
        assertThat(titles("zzz")).isEmpty()
    }
}
