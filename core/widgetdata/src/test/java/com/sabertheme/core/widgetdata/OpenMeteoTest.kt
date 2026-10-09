package com.sabertheme.core.widgetdata

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class OpenMeteoTest {

    private val body = """
        {
          "latitude": 6.92, "longitude": 79.86, "timezone": "Asia/Colombo",
          "current_units": {"temperature_2m": "°C"},
          "current": {"time": "2026-10-09T09:45", "interval": 900, "temperature_2m": 24.4, "weather_code": 2},
          "hourly": {
            "time": ["2026-10-09T08:00", "2026-10-09T09:00", "2026-10-09T10:00", "2026-10-09T11:00", "2026-10-09T12:00"],
            "temperature_2m": [22.0, 23.6, 24.5, 25.5, 26.1],
            "weather_code": [0, 1, 2, 61, 95]
          },
          "daily": {"time": ["2026-10-09"], "temperature_2m_max": [27.4], "temperature_2m_min": [18.6]}
        }
    """.trimIndent()

    @Test
    fun `parses current conditions and today's range`() {
        val w = OpenMeteo.parse(body)
        assertThat(w.temperature).isEqualTo(24)
        assertThat(w.condition).isEqualTo(WeatherCondition.PartlyCloudy)
        assertThat(w.high).isEqualTo(27)
        assertThat(w.low).isEqualTo(19)
    }

    @Test
    fun `hourly starts after the current time`() {
        val w = OpenMeteo.parse(body, hours = 2)
        assertThat(w.hourly).containsExactly(
            HourForecast(10, WeatherCondition.PartlyCloudy, 25),
            HourForecast(11, WeatherCondition.Rain, 26),
        ).inOrder()
    }

    @Test
    fun `tolerates missing hourly and daily blocks`() {
        val w = OpenMeteo.parse("""{"current": {"time": "2026-10-09T09:45", "temperature_2m": -0.6, "weather_code": 73}}""")
        assertThat(w.temperature).isEqualTo(-1)
        assertThat(w.condition).isEqualTo(WeatherCondition.Snow)
        assertThat(w.high).isNull()
        assertThat(w.hourly).isEmpty()
    }

    @Test
    fun `maps WMO codes`() {
        assertThat(WeatherCondition.fromCode(0)).isEqualTo(WeatherCondition.Clear)
        assertThat(WeatherCondition.fromCode(3)).isEqualTo(WeatherCondition.Cloudy)
        assertThat(WeatherCondition.fromCode(48)).isEqualTo(WeatherCondition.Fog)
        assertThat(WeatherCondition.fromCode(55)).isEqualTo(WeatherCondition.Drizzle)
        assertThat(WeatherCondition.fromCode(81)).isEqualTo(WeatherCondition.Rain)
        assertThat(WeatherCondition.fromCode(86)).isEqualTo(WeatherCondition.Snow)
        assertThat(WeatherCondition.fromCode(99)).isEqualTo(WeatherCondition.Thunder)
    }

    @Test
    fun `url rounds coordinates and picks the unit by country`() {
        val metric = OpenMeteo.url(6.927079, 79.861244, Locale.UK)
        assertThat(metric).contains("latitude=6.93&longitude=79.86")
        assertThat(metric).doesNotContain("fahrenheit")
        assertThat(OpenMeteo.url(40.7, -74.0, Locale.US)).contains("temperature_unit=fahrenheit")
    }
}
