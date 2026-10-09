package com.sabertheme.core.widgetdata

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

data class BatteryData(val percent: Int, val charging: Boolean)

/** Sticky `ACTION_BATTERY_CHANGED`; no permission needed. */
@Singleton
class BatterySource @Inject constructor(@ApplicationContext context: Context, scope: WidgetScope) : WidgetDataSource<BatteryData> {
    override val state: Flow<WidgetState<BatteryData>> = context
        .broadcasts(Intent.ACTION_BATTERY_CHANGED)
        .mapNotNull { it?.toBattery() }
        .distinctUntilChanged()
        .map { WidgetState.Ready(it) }
        .shareIn(scope, WhileShown, replay = 1)
}

private fun Intent.toBattery(): BatteryData? {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryData(level * 100 / scale, charging)
}
