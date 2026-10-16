package com.sabertheme.core.briefing

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.PresetReverb
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saber's voice, in the manner of a sage, with a faint room echo as if heard
 * inside your head. Her lines normally arrive pre-rendered in the neural voice
 * ([SageVoiceEngine], made in `:brain`); without a clip, the phone's own
 * text-to-speech engine (Samsung TTS on the S23) renders the text here, calm
 * and a touch slow. Either way it plays through the reverb, dry where the
 * effect is missing. Music ducks under her. [speaking] drives her mouth.
 * Home only; created on first use.
 */
@Singleton
class SaberVoice @Inject constructor(@ApplicationContext private val context: Context) {

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    private val clip = File(context.cacheDir, "saber_voice.wav")

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private var player: MediaPlayer? = null
    private var reverb: PresetReverb? = null
    private var utterance = 0

    /** Plays [clip] when it exists (instant); otherwise renders [text] with the system engine first. */
    fun speak(text: String, clip: String? = null) {
        if (text.isBlank()) return
        stopPlayback()
        utterance++
        clip?.let(::File)?.takeIf { it.isFile }?.let { return play(it) }
        val engine = tts ?: create()
        if (ready) render(engine, text) else pending = text
    }

    fun stop() {
        pending = null
        utterance++
        tts?.stop()
        stopPlayback()
        finished()
    }

    /** Frees the engine and effect (Home stopped); the next [speak] starts them again. */
    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
        reverb?.release()
        reverb = null
        clip.delete()
    }

    private fun create(): TextToSpeech {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS || tts !== engine) return@TextToSpeech
            if (engine.setLanguage(Locale.UK) < TextToSpeech.LANG_AVAILABLE) engine.setLanguage(Locale.ENGLISH)
            engine.setPitch(PITCH)
            engine.setSpeechRate(RATE)
            ready = true
            pending?.let { render(engine, it) }
            pending = null
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                main.post { if (utteranceId == current()) play(clip) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post(::finished)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                main.post(::finished)
            }
        })
        tts = engine
        return engine
    }

    private fun current() = "saber-$utterance"

    /** Renders the words to [clip]; [play] follows when the engine is done. */
    private fun render(engine: TextToSpeech, text: String) {
        utterance++
        engine.synthesizeToFile(text, null, clip, current())
    }

    private fun play(file: File) {
        stopPlayback()
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(attributes)
            mp.setDataSource(file.path)
            mp.prepare()
            attachReverb(mp)
            mp.setOnCompletionListener { finished() }
            mp.setOnErrorListener { _, _, _ ->
                finished()
                true
            }
            audio.requestAudioFocus(focus)
            player = mp
            _speaking.value = true
            mp.start()
        } catch (e: Exception) {
            Log.w(TAG, "Voice playback failed: ${e.javaClass.simpleName}")
            mp.release()
            finished()
        }
    }

    /** A faint room on her voice; skipped quietly where the effect is missing. */
    private fun attachReverb(mp: MediaPlayer) {
        try {
            val fx = reverb ?: PresetReverb(0, 0).also {
                it.preset = PresetReverb.PRESET_MEDIUMROOM
                it.enabled = true
                reverb = it
            }
            mp.attachAuxEffect(fx.id)
            mp.setAuxEffectSendLevel(REVERB_SEND)
        } catch (e: Exception) {
            Log.w(TAG, "No reverb: ${e.javaClass.simpleName}")
        }
    }

    private fun stopPlayback() {
        player?.let {
            it.setOnCompletionListener(null)
            runCatching { it.stop() }
            it.release()
        }
        player = null
    }

    private fun finished() {
        stopPlayback()
        _speaking.value = false
        audio.abandonAudioFocusRequest(focus)
    }

    private companion object {
        const val TAG = "SaberVoice"
        /** Calm and even: a little lower and slower than the engine's default. */
        const val PITCH = 0.95f
        const val RATE = 0.9f
        const val REVERB_SEND = 0.4f
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
