package com.sabertheme.widgets.glance

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Every Saber widget exported to other launchers. */
object SaberGlanceWidgets {
    private fun all() = listOf(
        ClockGlanceWidget(),
        WeatherGlanceWidget(),
        CalendarGlanceWidget(),
        BatteryGlanceWidget(),
        AlarmGlanceWidget(),
    )

    suspend fun updateAll(context: Context) {
        all().forEach { it.updateAll(context) }
    }

    /**
     * Generated picker previews (API 35+). The platform rate-limits this, so
     * the app calls it once per installed version.
     */
    suspend fun publishPreviews(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val manager = GlanceAppWidgetManager(context)
        listOf(
            ClockGlanceReceiver::class,
            WeatherGlanceReceiver::class,
            CalendarGlanceReceiver::class,
            BatteryGlanceReceiver::class,
            AlarmGlanceReceiver::class,
        ).forEach { manager.setWidgetPreviews(it) }
    }

    /**
     * On app start (also after an update): if any Saber widget is on a home
     * screen, schedule the background refresh and redraw them, so they never
     * keep data or tap intents from before the restart.
     */
    suspend fun onAppStart(context: Context) {
        if (!anyPlaced(context)) return
        WidgetRefreshWorker.schedule(context)
        updateAll(context)
    }

    suspend fun anyPlaced(context: Context): Boolean {
        val manager = GlanceAppWidgetManager(context)
        return all().any { manager.getGlanceIds(it.javaClass).isNotEmpty() }
    }
}

/**
 * Every 15 min while a Saber widget is on some home screen: refresh weather
 * when its cache is stale (30 min), then redraw all widgets (battery level,
 * calendar, Today/Tomorrow labels). Cancels itself once none are placed.
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!SaberGlanceWidgets.anyPlaced(context)) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
            return Result.success()
        }
        context.widgetSources().weather.refresh()
        SaberGlanceWidgets.updateAll(context)
        return Result.success()
    }

    companion object {
        private const val NAME = "saber-glance-refresh"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}
