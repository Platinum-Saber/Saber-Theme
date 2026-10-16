package com.sabertheme.core.briefing

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteCoroutineWorker
import androidx.work.multiprocess.RemoteListenableWorker
import androidx.work.multiprocess.RemoteWorkerService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Runs [BriefingRunner] in the `:brain` process (`RemoteWorkerService` is
 * declared there by `:app`), so the 2 GB model never sits in Home's process;
 * `:brain` is free to be killed once the run ends.
 */
class BriefingWorker(context: Context, params: WorkerParameters) : RemoteCoroutineWorker(context, params) {

    override suspend fun doRemoteWork(): Result = try {
        EntryPointAccessors.fromApplication(applicationContext, BriefingEntryPoint::class.java).runner().run()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Stack traces name code, not mail: the runner never puts message text into exceptions.
        Log.w("SaberBrain", "Briefing run failed", e)
        if (runAttemptCount < 2) Result.retry() else Result.failure()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BriefingEntryPoint {
    fun runner(): BriefingRunner
}

/** Schedules briefing runs; all of them go to `:brain`. */
object Briefings {
    private const val HOURLY = "briefing-hourly"
    private const val NOW = "briefing-now"

    /** Hourly while the briefing is on; keeps an existing schedule. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BriefingWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .setInputData(remote(context))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(HOURLY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * One run after [delayMs]; a run already waiting absorbs this one, so a
     * burst of mail is labelled together (the runner drains the queue).
     */
    fun runSoon(context: Context, delayMs: Long = 2 * 60_000L) {
        val request = OneTimeWorkRequestBuilder<BriefingWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(remote(context))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).run {
            cancelUniqueWork(HOURLY)
            cancelUniqueWork(NOW)
        }
    }

    private fun remote(context: Context): Data = Data.Builder()
        .putString(RemoteListenableWorker.ARGUMENT_PACKAGE_NAME, context.packageName)
        .putString(RemoteListenableWorker.ARGUMENT_CLASS_NAME, ComponentName(context, RemoteWorkerService::class.java).className)
        .build()
}
