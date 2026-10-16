package com.sabertheme.core.briefing

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Labels mail with Gemma 4 E2B on the GPU, one fresh conversation per mail
 * (one big prompt failed; one mail at a time held up). Runs only in `:brain`:
 * the engine takes about 2 GB and loads in about 12 s, so it lives for one
 * batch and is closed after. Without the model file, [MailRules] label alone.
 */
@Singleton
class MailLabeler @Inject constructor(@ApplicationContext private val context: Context) {

    /** Where Settings tells the owner to put the model (`adb push`/`cp` in Phase 1). */
    val modelFile: File get() = File(context.getExternalFilesDir(MODEL_DIR), MODEL_NAME)

    suspend fun label(mails: List<QueuedMail>): Map<String, MailVerdict> = withContext(Dispatchers.Default) {
        val verdicts = mutableMapOf<String, MailVerdict>()
        val forModel = mails.filter { mail ->
            MailRules.before(mail)?.let { verdicts[mail.key] = MailVerdict(it) } == null
        }
        if (forModel.isEmpty()) return@withContext verdicts
        if (!modelFile.isFile) {
            forModel.forEach { verdicts[it.key] = MailRules.fallback(it) }
            return@withContext verdicts
        }

        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMM yyyy", Locale.ENGLISH))
        val loadStart = SystemClock.elapsedRealtime()
        val engine = Engine(EngineConfig(modelPath = modelFile.path, backend = Backend.GPU(), cacheDir = context.cacheDir.path))
        try {
            engine.initialize()
            Log.i(TAG, "Model loaded in ${SystemClock.elapsedRealtime() - loadStart} ms")
            for (mail in forModel) {
                ensureActive()
                val start = SystemClock.elapsedRealtime()
                val verdict = engine.createConversation(Config).use { conversation ->
                    val reply = conversation.sendMessage(MailPrompt.build(mail, today))
                    LabelParser.parse(reply.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text })
                }
                verdicts[mail.key] = verdict ?: MailRules.fallback(mail)
                Log.i(TAG, "Labelled 1 mail in ${SystemClock.elapsedRealtime() - start} ms (${if (verdict == null) "rules" else "model"})")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A GPU or model failure must not lose the briefing: rules label the rest.
            // The engine's own message (paths, backend, status codes) carries no mail text.
            Log.w(TAG, "Model failed: ${e.javaClass.simpleName}: ${e.message?.take(300)}")
            forModel.filter { it.key !in verdicts }.forEach { verdicts[it.key] = MailRules.fallback(it) }
        } finally {
            // close() throws on an engine that never initialised; that must not lose the run.
            runCatching { engine.close() }
        }
        verdicts
    }

    private companion object {
        const val TAG = "SaberBrain"
        const val MODEL_DIR = "models"
        const val MODEL_NAME = "gemma-4-E2B-it.litertlm"
        val Config = ConversationConfig(samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.25))
    }
}
