package com.sabertheme.core.widgetdata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.roundToInt

enum class WeatherCondition(val label: String) {
    Clear("Clear"),
    PartlyCloudy("Partly cloudy"),
    Cloudy("Cloudy"),
    Fog("Fog"),
    Drizzle("Drizzle"),
    Rain("Rain"),
    Snow("Snow"),
    Thunder("Thunderstorm"),
    ;

    companion object {
        /** WMO weather interpretation codes, as Open-Meteo reports them. */
        fun fromCode(code: Int): WeatherCondition = when (code) {
            0, 1 -> Clear
            2 -> PartlyCloudy
            3 -> Cloudy
            45, 48 -> Fog
            in 51..57 -> Drizzle
            in 61..67, in 80..82 -> Rain
            in 71..77, 85, 86 -> Snow
            in 95..99 -> Thunder
            else -> Cloudy
        }
    }
}

data class HourForecast(val hour: Int, val condition: WeatherCondition, val temperature: Int)

data class WeatherData(
    val temperature: Int,
    val condition: WeatherCondition,
    val high: Int?,
    val low: Int?,
    val hourly: List<HourForecast>,
)

internal object OpenMeteo {
    private val json = Json { ignoreUnknownKeys = true }
    private val FAHRENHEIT_COUNTRIES = setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")

    fun url(latitude: Double, longitude: Double, locale: Locale = Locale.getDefault()): String {
        val unit = if (locale.country in FAHRENHEIT_COUNTRIES) "&temperature_unit=fahrenheit" else ""
        val lat = "%.2f".format(Locale.ROOT, latitude)
        val lon = "%.2f".format(Locale.ROOT, longitude)
        return "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code" +
            "&hourly=temperature_2m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=2$unit"
    }

    /** Current conditions, today's range and the next [hours] whole hours. */
    fun parse(body: String, hours: Int = 5): WeatherData {
        val r = json.decodeFromString<Response>(body)
        val now = LocalDateTime.parse(r.current.time)
        val hourly = r.hourly?.let { h ->
            h.time.indices
                .map { it to LocalDateTime.parse(h.time[it]) }
                .filter { (_, t) -> t.isAfter(now) }
                .take(hours)
                .map { (i, t) -> HourForecast(t.hour, WeatherCondition.fromCode(h.code[i]), h.temperature[i].roundToInt()) }
        }.orEmpty()
        return WeatherData(
            temperature = r.current.temperature.roundToInt(),
            condition = WeatherCondition.fromCode(r.current.code),
            high = r.daily?.max?.firstOrNull()?.roundToInt(),
            low = r.daily?.min?.firstOrNull()?.roundToInt(),
            hourly = hourly,
        )
    }

    @Serializable
    private data class Response(val current: Current, val hourly: Hourly? = null, val daily: Daily? = null)

    @Serializable
    private data class Current(
        val time: String,
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("weather_code") val code: Int,
    )

    @Serializable
    private data class Hourly(
        val time: List<String>,
        @SerialName("temperature_2m") val temperature: List<Double>,
        @SerialName("weather_code") val code: List<Int>,
    )

    @Serializable
    private data class Daily(
        @SerialName("temperature_2m_max") val max: List<Double>,
        @SerialName("temperature_2m_min") val min: List<Double>,
    )
}
