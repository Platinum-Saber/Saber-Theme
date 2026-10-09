package com.sabertheme.core.widgetdata

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private val Context.weatherStore by preferencesDataStore("weather")
private val BODY = stringPreferencesKey("body")
private val FETCHED_AT = longPreferencesKey("fetched_at")

/** Open-Meteo at the last known coarse location, refreshed every 30 min and cached across restarts. */
@Singleton
class WeatherSource @Inject constructor(
    @ApplicationContext private val context: Context,
    permissions: WidgetPermissions,
    scope: WidgetScope,
) : WidgetDataSource<WeatherData> {

    private class Entry(val weather: WeatherData, val fetchedAt: Long)

    override val state: Flow<WidgetState<WeatherData>> = permissions
        .gated(WidgetPermission.Location) { updates() }
        .shareIn(scope, WhileShown, replay = 1)

    private fun updates(): Flow<WidgetState<WeatherData>> = flow {
        var entry = readCache()
        entry?.let { emit(WidgetState.Ready(it.weather)) }
        while (true) {
            val age = System.currentTimeMillis() - (entry?.fetchedAt ?: 0L)
            if (age in 0 until REFRESH_MS) {
                delay(REFRESH_MS - age)
                continue
            }
            val fresh = try {
                fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Weather fetch failed", e)
                null
            }
            if (fresh != null) {
                entry = fresh
                emit(WidgetState.Ready(fresh.weather))
                delay(REFRESH_MS)
            } else {
                if (entry == null) {
                    val locationOn = context.getSystemService(LocationManager::class.java).isLocationEnabled
                    emit(WidgetState.Error(if (locationOn) "Weather unavailable" else "Location is off"))
                }
                delay(RETRY_MS)
            }
        }
    }

    private suspend fun readCache(): Entry? = try {
        val prefs = context.weatherStore.data.first()
        prefs[BODY]?.let { Entry(OpenMeteo.parse(it), prefs[FETCHED_AT] ?: 0L) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun fetch(): Entry {
        val location = location() ?: throw IOException("No location")
        val body = get(OpenMeteo.url(location.latitude, location.longitude))
        val weather = OpenMeteo.parse(body)
        val now = System.currentTimeMillis()
        context.weatherStore.edit {
            it[BODY] = body
            it[FETCHED_AT] = now
        }
        return Entry(weather, now)
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** A recent last-known fix if any, else one fresh coarse fix (15 s cap). */
    @SuppressLint("MissingPermission") // Only called while ACCESS_COARSE_LOCATION is granted.
    private suspend fun location(): Location? {
        val manager = context.getSystemService(LocationManager::class.java)
        val known = manager.allProviders
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (known != null && System.currentTimeMillis() - known.time < MAX_FIX_AGE_MS) return known
        val provider = when {
            manager.hasProvider(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return known
        }
        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    manager.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
                } catch (e: Exception) {
                    cont.resume(null)
                }
            }
        }
        return fresh ?: known
    }

    private companion object {
        const val TAG = "WeatherSource"
        val REFRESH_MS = TimeUnit.MINUTES.toMillis(30)
        val RETRY_MS = TimeUnit.MINUTES.toMillis(5)
        val MAX_FIX_AGE_MS = TimeUnit.HOURS.toMillis(3)
    }
}
