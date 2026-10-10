package com.sabertheme.core.widgetdata

import com.google.common.truth.Truth.assertThat
import com.sabertheme.core.widgetdata.MediaPicker.Result
import com.sabertheme.core.widgetdata.MediaPicker.Session
import org.junit.Test

class MediaPickerTest {
    private val spotify = "com.spotify.music"
    private val vlc = "org.videolan.vlc"

    @Test
    fun nothingWithoutSessionsOrPick() {
        assertThat(MediaPicker.pick(emptyList(), null, emptySet())).isEqualTo(Result(null, null))
    }

    @Test
    fun withoutPickPlayingWinsThenMostRecent() {
        val sessions = listOf(Session(spotify, false), Session(vlc, true))
        assertThat(MediaPicker.pick(sessions, null, setOf(vlc)).shown).isEqualTo(vlc)
        val paused = listOf(Session(spotify, false), Session(vlc, false))
        assertThat(MediaPicker.pick(paused, null, emptySet()).shown).isEqualTo(spotify)
    }

    @Test
    fun pickHoldsWhileTheOtherAppKeepsPlaying() {
        val sessions = listOf(Session(vlc, true))
        assertThat(MediaPicker.pick(sessions, spotify, wasPlaying = setOf(vlc))).isEqualTo(Result(spotify, spotify))
    }

    @Test
    fun pickedAppWithoutSessionIsShownForResume() {
        val result = MediaPicker.pick(emptyList(), spotify, emptySet())
        assertThat(result.shown).isEqualTo(spotify)
    }

    @Test
    fun anotherAppStartingToPlayOverridesThePick() {
        val sessions = listOf(Session(vlc, true), Session(spotify, false))
        assertThat(MediaPicker.pick(sessions, spotify, wasPlaying = emptySet())).isEqualTo(Result(vlc, null))
    }

    @Test
    fun pickedAppStartingToPlayKeepsThePick() {
        val sessions = listOf(Session(spotify, true), Session(vlc, false))
        assertThat(MediaPicker.pick(sessions, spotify, wasPlaying = setOf(vlc))).isEqualTo(Result(spotify, spotify))
    }
}
