package com.sabertheme.core.briefing

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two models the briefing downloads from Settings. Size and SHA-256 are
 * pinned: Gemma to a Hugging Face commit (its LFS oid), Kokoro to the
 * sherpa-onnx release asset's digest. Anything else is refused.
 */
enum class ModelPack(
    val url: String,
    /** Bytes of the download itself. */
    val bytes: Long,
    val sha256: String,
    /** File (Gemma) or folder (Kokoro) name once installed. */
    val installedName: String,
    /** The download is a `.tar.bz2` holding one top folder named [installedName]. */
    val archive: Boolean,
    /** Extra room the unpacked files need beside the archive. */
    val unpackedBytes: Long,
) {
    Gemma(
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
            "b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it.litertlm",
        bytes = 2_588_147_712,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        installedName = "gemma-4-E2B-it.litertlm",
        archive = false,
        unpackedBytes = 0,
    ),
    Kokoro(
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2",
        bytes = 132_303_094,
        sha256 = "4c3052abaa60943a341f193888cf6abd68787dae6ab8ae5c925a706caa247e4e",
        installedName = SageVoiceEngine.MODEL_NAME,
        archive = true,
        unpackedBytes = 189_882_497,
    ),
}

/**
 * Where each model lives. Downloads go to `noBackupFilesDir/briefing-models`
 * (app-owned, never backed up); a copy pushed over adb to the Phase 1
 * external paths still works when nothing was downloaded.
 */
@Singleton
class ModelFiles @Inject constructor(@ApplicationContext private val context: Context) {

    val root: File get() = File(context.noBackupFilesDir, "briefing-models")

    fun downloaded(pack: ModelPack) = File(root, pack.installedName)

    /** The partial download; kept across cancel and retry so it resumes. */
    fun part(pack: ModelPack) = File(root, "${pack.name.lowercase()}.part")

    /** Where an archive is unpacked before it is moved into place. */
    fun unpacking(pack: ModelPack) = File(root, "${pack.name.lowercase()}.unpacking")

    fun legacy(pack: ModelPack): File = when (pack) {
        ModelPack.Gemma -> File(context.getExternalFilesDir("models"), pack.installedName)
        ModelPack.Kokoro -> File(context.getExternalFilesDir("voices"), pack.installedName)
    }

    /** The copy the brain loads: the downloaded one, else the adb-pushed one. */
    fun current(pack: ModelPack): File = pick(downloaded(pack), legacy(pack))

    fun ready(pack: ModelPack): Boolean = complete(pack, current(pack))

    /** Bytes the ready model takes on the phone. */
    fun size(pack: ModelPack): Long = current(pack).walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    companion object {
        /** Files sherpa-onnx needs from the Kokoro pack (bf_ voices read the British lexicon). */
        val KOKORO_FILES = listOf("model.int8.onnx", "voices.bin", "tokens.txt", "espeak-ng-data", "lexicon-gb-en.txt")

        fun pick(downloaded: File, legacy: File): File = if (downloaded.exists()) downloaded else legacy

        fun complete(pack: ModelPack, at: File): Boolean = when (pack) {
            ModelPack.Gemma -> at.isFile
            ModelPack.Kokoro -> KOKORO_FILES.all { File(at, it).exists() }
        }
    }
}
