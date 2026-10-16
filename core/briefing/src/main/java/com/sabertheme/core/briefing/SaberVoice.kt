package com.sabertheme.core.briefing

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saber's voice: the phone's own text-to-speech engine (free, on-device),
 * a little higher than its default. Music ducks under her while she speaks.
 * [speaking] drives her mouth. Home only; created on first use.
 */
@Singleton
class SaberVoice @Inject constructor(@ApplicationContext private val context: Context) {

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun speak(text: String) {
        if (text.isBlank()) return
        val engine = tts ?: create()
        if (ready) say(engine, text) else pending = text
    }

    fun stop() {
        pending = null
        tts?.stop()
        finished()
    }

    /** Frees the engine (Home stopped); the next [speak] starts it again. */
    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun create(): TextToSpeech {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS || tts !== engine) return@TextToSpeech
            if (engine.setLanguage(Locale.UK) < TextToSpeech.LANG_AVAILABLE) engine.setLanguage(Locale.ENGLISH)
            engine.setPitch(PITCH)
            engine.setAudioAttributes(attributes)
            ready = true
            pending?.let { say(engine, it) }
            pending = null
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speaking.value = true
            }

            override fun onDone(utteranceId: String?) = finished()

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished()

            override fun onError(utteranceId: String?, errorCode: Int) = finished()

            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished()
        })
        tts = engine
        return engine
    }

    private fun say(engine: TextToSpeech, text: String) {
        audio.requestAudioFocus(focus)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE)
    }

    private fun finished() {
        _speaking.value = false
        audio.abandonAudioFocusRequest(focus)
    }

    private companion object {
        const val PITCH = 1.15f
        const val UTTERANCE = "saber-briefing"
    }
}

/** When she may speak without being asked. Pure, so the rules are tested. */
object SpeakGuard {
    const val QUIET_FROM_HOUR = 22
    const val QUIET_UNTIL_HOUR = 7

    fun mayAnnounce(
        hour: Int,
        ringerNormal: Boolean,
        interruptionsAll: Boolean,
        musicPlaying: Boolean,
        screenRecorded: Boolean,
        homeVisible: Boolean,
    ): Boolean =
        homeVisible && ringerNormal && interruptionsAll && !musicPlaying && !screenRecorded &&
            hour in QUIET_UNTIL_HOUR until QUIET_FROM_HOUR
}
