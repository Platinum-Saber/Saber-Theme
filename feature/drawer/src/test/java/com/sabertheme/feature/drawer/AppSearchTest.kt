package com.sabertheme.feature.drawer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppSearchTest {
    private data class App(val label: String, val pkg: String)

    private val apps = listOf(
        App("Calculator", "com.sec.android.app.popupcalculator"),
        App("Calendar", "com.samsung.android.calendar"),
        App("Camera", "com.sec.android.app.camera"),
        App("Google Maps", "com.google.android.apps.maps"),
        App("Cal", "org.example.cal"),
        App("Music", "com.example.player"),
        App("Clock", "com.sec.android.app.clockpackage"),
        App("Café Finder", "com.example.cafe"),
    )

    private fun rank(q: String) = AppSearch.rank(q, apps, App::label, App::pkg).map { it.label }

    @Test
    fun exactBeatsPrefixBeatsSubstring() {
        assertThat(rank("cal")).containsAtLeast("Cal", "Calculator", "Calendar").inOrder()
        assertThat(rank("cal").first()).isEqualTo("Cal")
    }

    @Test
    fun wordStartAndInitials() {
        assertThat(rank("maps").first()).isEqualTo("Google Maps")
        assertThat(rank("gm").first()).isEqualTo("Google Maps")
    }

    @Test
    fun fuzzySubsequenceRanksLast() {
        val r = rank("clk")
        assertThat(r).containsExactly("Clock")
    }

    @Test
    fun packageMatchesButBelowLabel() {
        // "player" only appears in Music's package.
        assertThat(rank("player")).containsExactly("Music")
        assertThat(AppSearch.score("cam", "Camera", "x.camera")).isGreaterThan(AppSearch.score("cam", "Other", "x.camera"))
    }

    @Test
    fun boilerplatePackageSegmentsDoNotMatch() {
        assertThat(rank("android")).isEmpty()
        assertThat(rank("samsung")).isEmpty()
    }

    @Test
    fun ignoresCaseAndAccents() {
        assertThat(rank("CAFE")).containsExactly("Café Finder")
    }

    @Test
    fun blankQueryMatchesNothing() {
        assertThat(rank("  ")).isEmpty()
    }

    @Test
    fun railSections() {
        val labels = listOf("1Password", "9GAG", "Calculator", "Camera", "Éclair", "Zoom")
        assertThat(AppSearch.sections(labels)).containsExactly("#" to 0, "C" to 2, "E" to 4, "Z" to 5).inOrder()
    }
}
