package com.sabertheme.core.briefing

import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saber's neural voice: Kokoro (Apache-2.0) through sherpa-onnx, speaker
 * bf_emma, picked by the owner from the audition. A stock voice, not a clone
 * of anyone. Runs only in `:brain`: each briefing is rendered to a WAV right
 * after it is composed, so a tap on Home plays at once and the model never
 * loads in Home's process. Without the model files, Samsung TTS speaks instead.
 */
@Singleton
class SageVoiceEngine @Inject constructor(private val files: ModelFiles) {

    /** The downloaded Kokoro pack, else one pushed over adb (Phase 1). */
    val modelDir: File get() = files.current(ModelPack.Kokoro)

    val available: Boolean get() = files.ready(ModelPack.Kokoro)

    /** Renders each [lines] entry to its file; returns the files written. Loads the model once for all. */
    suspend fun render(lines: Map<File, String>): Set<File> = withContext(Dispatchers.Default) {
        if (lines.isEmpty() || !available) return@withContext emptySet()
        val written = mutableSetOf<File>()
        val loadStart = SystemClock.elapsedRealtime()
        val tts = try {
            OfflineTts(config = config())
        } catch (e: Exception) {
            Log.w(TAG, "Voice model failed to load: ${e.javaClass.simpleName}")
            val phontab = File(modelDir, "espeak-ng-data/phontab")
            Log.w(TAG, "phontab exists=${phontab.exists()} canRead=${phontab.canRead()} len=${phontab.length()}")
            return@withContext emptySet()
        }
        try {
            Log.i(TAG, "Voice loaded in ${SystemClock.elapsedRealtime() - loadStart} ms")
            for ((file, text) in lines) {
                if (text.isBlank()) continue
                val start = SystemClock.elapsedRealtime()
                val audio = tts.generateWithConfig(text, GenerationConfig(sid = SPEAKER, speed = SPEED))
                file.parentFile?.mkdirs()
                if (audio.samples.isNotEmpty() && audio.save(file.path)) {
                    written += file
                    val seconds = audio.samples.size / audio.sampleRate.toFloat()
                    Log.i(TAG, "Rendered %.1f s of speech in %d ms".format(seconds, SystemClock.elapsedRealtime() - start))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Voice render failed: ${e.javaClass.simpleName}")
        } finally {
            tts.release()
        }
        written
    }

    private fun config(): OfflineTtsConfig {
        val dir = modelDir.path
        return OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = "$dir/model.int8.onnx",
                    voices = "$dir/voices.bin",
                    tokens = "$dir/tokens.txt",
                    dataDir = "$dir/espeak-ng-data",
                    // bf_ voices read with the British lexicon.
                    lexicon = "$dir/lexicon-gb-en.txt",
                ),
                numThreads = THREADS,
            ),
        )
    }

    companion object {
        private const val TAG = "SaberBrain"
        const val MODEL_NAME = "kokoro-int8-multi-lang-v1_0"
        const val VOICE_NAME = "Emma"

        /** bf_emma in the kokoro-multi-lang-v1_0 speaker table. */
        private const val SPEAKER = 21
        private const val SPEED = 1.0f
        private const val THREADS = 4
    }
}
