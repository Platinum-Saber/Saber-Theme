package com.sabertheme.core.briefing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where her rendered lines live: `noBackupFilesDir/briefing_audio`, one WAV
 * per distinct text (named by its hash), so an unchanged briefing is never
 * rendered twice. Device-only; cleared with the briefing.
 */
@Singleton
class BriefingClips @Inject constructor(@ApplicationContext context: Context) {
    private val dir = File(context.noBackupFilesDir, "briefing_audio")

    fun fileFor(text: String): File = File(dir, "${name(text)}.wav")

    /** The sample line Settings plays, rendered once per voice. */
    val sample: File get() = fileFor(SAMPLE)

    /** Deletes every clip but [keep]. */
    fun keepOnly(keep: Collection<File>) {
        val names = keep.mapTo(HashSet()) { it.name }
        dir.listFiles()?.filter { it.name !in names }?.forEach { it.delete() }
    }

    fun clear() {
        dir.deleteRecursively()
    }

    companion object {
        const val SAMPLE = "Notice. One matter requires your attention, Master. " +
            "Assignment 3 for Introduction to Engineering Optimization is due on Monday at 11:59 PM. End of report."

        /** Voice and text both name the clip, so a new voice re-renders. */
        internal fun name(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("${SageVoiceEngine.MODEL_NAME}/${SageVoiceEngine.VOICE_NAME}/$text".encodeToByteArray())
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}
