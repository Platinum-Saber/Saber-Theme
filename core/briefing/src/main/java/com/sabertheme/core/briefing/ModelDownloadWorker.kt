package com.sabertheme.core.briefing

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.StatFs
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Where a [ModelPack] stands, as Settings shows it. */
sealed interface ModelState {
    /** Not on the phone; [partBytes] of it are downloaded already. */
    data class Missing(val partBytes: Long) : ModelState
    data class Waiting(val wifiOnly: Boolean, val partBytes: Long) : ModelState
    data class Downloading(val done: Long, val total: Long) : ModelState
    data object Verifying : ModelState
    data object Unpacking : ModelState
    data class Ready(val bytes: Long) : ModelState
    data class Failed(val message: String, val partBytes: Long) : ModelState
}

/**
 * Downloads one [ModelPack] (main process: it only moves bytes, nothing is
 * loaded). Runs as a dataSync foreground service while the owner started it
 * from Settings; retries resume from the `.part` file. The pack is verified
 * against its pinned SHA-256 before it is moved where the brain loads it.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pack = ModelPack.entries.firstOrNull { it.name == inputData.getString(KEY_PACK) } ?: return Result.failure()
        val files = ModelFiles(applicationContext)
        if (ModelFiles.complete(pack, files.downloaded(pack))) return Result.success()
        foreground(pack, files.part(pack).length())

        val part = files.part(pack)
        files.root.mkdirs()
        val needed = pack.bytes - part.length().coerceAtMost(pack.bytes) + pack.unpackedBytes + MARGIN
        if (StatFs(files.root.path).availableBytes < needed) return failed("Needs ${sizeLabel(needed)} free")

        val downloader = ModelDownloader()
        return try {
            var reported = 0L
            var notified = 0L
            downloader.fetch(pack.url, part, pack.bytes) { done ->
                val now = SystemClock.elapsedRealtime()
                if (now - reported >= PROGRESS_MS) {
                    reported = now
                    setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to pack.bytes))
                }
                if (now - notified >= NOTIFY_MS) {
                    notified = now
                    foreground(pack, done)
                }
            }
            setProgress(workDataOf(KEY_PHASE to PHASE_VERIFY))
            downloader.verify(part, pack.sha256)
            install(pack, files, downloader)
            Log.i(TAG, "Downloaded ${pack.name}")
            Result.success()
        } catch (e: CorruptDownload) {
            Log.w(TAG, "${pack.name} download rejected: ${e.message}")
            failed("Download was corrupted; try again")
        } catch (e: IOException) {
            Log.w(TAG, "${pack.name} download interrupted at ${part.length()} bytes: ${e.javaClass.simpleName}: ${e.message}")
            if (runAttemptCount < MAX_RETRIES) Result.retry() else failed("Couldn't download; try again")
        }
    }

    private suspend fun install(pack: ModelPack, files: ModelFiles, downloader: ModelDownloader) = withContext(Dispatchers.IO) {
        val part = files.part(pack)
        val target = files.downloaded(pack)
        target.deleteRecursively()
        if (!pack.archive) {
            if (!part.renameTo(target)) throw IOException("Couldn't move ${pack.name} into place")
            return@withContext
        }
        setProgress(workDataOf(KEY_PHASE to PHASE_UNPACK))
        val unpacking = files.unpacking(pack)
        try {
            downloader.unpack(part, unpacking)
            val inner = unpacking.resolve(pack.installedName)
            if (!ModelFiles.complete(pack, inner)) throw CorruptDownload("Archive is missing model files")
            if (!inner.renameTo(target)) throw IOException("Couldn't move ${pack.name} into place")
            part.delete()
        } finally {
            unpacking.deleteRecursively()
        }
    }

    private fun failed(message: String) = Result.failure(workDataOf(KEY_ERROR to message))

    private suspend fun foreground(pack: ModelPack, done: Long) {
        val manager = NotificationManagerCompat.from(applicationContext)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW).setName("Model downloads").build(),
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading ${pack.title}")
            .setContentText("${sizeLabel(done)} of ${sizeLabel(pack.bytes)}")
            .setProgress(PERCENT, (done * PERCENT / pack.bytes).toInt(), false)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Cancel", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        try {
            setForeground(ForegroundInfo(NOTIFICATION_BASE + pack.ordinal, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        } catch (e: IllegalStateException) {
            // A retry started while the app is in the background may not start a
            // foreground service; it runs as plain work and resumes if stopped.
            Log.i(TAG, "${pack.name} download runs without a foreground service")
        }
    }

    internal companion object {
        const val TAG = "SaberModels"
        const val KEY_PACK = "pack"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_PHASE = "phase"
        const val KEY_ERROR = "error"
        const val PHASE_VERIFY = "verify"
        const val PHASE_UNPACK = "unpack"
        const val CHANNEL = "model-downloads"
        const val NOTIFICATION_BASE = 4100
        const val PERCENT = 100
        const val PROGRESS_MS = 500L
        const val NOTIFY_MS = 2_000L
        const val MAX_RETRIES = 6
        const val MARGIN = 200_000_000L
    }
}

val ModelPack.title: String get() = when (this) {
    ModelPack.Gemma -> "Gemma 4 E2B"
    ModelPack.Kokoro -> "Saber's voice"
}

/** "2.6 GB" or "132 MB", the way Settings shows sizes. */
fun sizeLabel(bytes: Long): String =
    if (bytes >= 1_000_000_000) String.format(Locale.ROOT, "%.1f GB", bytes / 1e9) else "${bytes / 1_000_000} MB"

/** Starts, cancels, deletes and watches model downloads (main process). */
object ModelDownloads {
    private val deletions = MutableStateFlow(0)

    private fun work(pack: ModelPack) = "model-${pack.name.lowercase()}"

    /** Wi-Fi (unmetered) only unless [anyNetwork]; a part already downloaded is resumed. */
    fun start(context: Context, pack: ModelPack, anyNetwork: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (anyNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(ModelDownloadWorker.KEY_PACK to pack.name))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(work(pack), ExistingWorkPolicy.REPLACE, request)
    }

    /** Stops the download; the part stays so the next start resumes. */
    fun cancel(context: Context, pack: ModelPack) {
        WorkManager.getInstance(context).cancelUniqueWork(work(pack))
    }

    /** Removes the model, any partial download, and an adb-pushed copy. */
    suspend fun delete(context: Context, pack: ModelPack, files: ModelFiles) {
        cancel(context, pack)
        withContext(Dispatchers.IO) {
            listOf(files.downloaded(pack), files.part(pack), files.unpacking(pack), files.legacy(pack)).forEach {
                if (!it.deleteRecursively()) Log.w(ModelDownloadWorker.TAG, "Couldn't delete all of ${it.name}")
            }
        }
        deletions.value++
    }

    fun state(context: Context, pack: ModelPack, files: ModelFiles): Flow<ModelState> =
        combine(WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(work(pack)), deletions) { infos, _ ->
            val info = infos.firstOrNull()
            val part = files.part(pack).length()
            when (info?.state) {
                WorkInfo.State.RUNNING -> when (info.progress.getString(ModelDownloadWorker.KEY_PHASE)) {
                    ModelDownloadWorker.PHASE_VERIFY -> ModelState.Verifying
                    ModelDownloadWorker.PHASE_UNPACK -> ModelState.Unpacking
                    else -> ModelState.Downloading(info.progress.getLong(ModelDownloadWorker.KEY_DONE, part), pack.bytes)
                }
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                    ModelState.Waiting(info.constraints.requiredNetworkType == NetworkType.UNMETERED, part)
                else -> when {
                    files.ready(pack) -> ModelState.Ready(files.size(pack))
                    info?.state == WorkInfo.State.FAILED ->
                        ModelState.Failed(info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Couldn't download; try again", part)
                    else -> ModelState.Missing(part)
                }
            }
        }.flowOn(Dispatchers.IO)
}
