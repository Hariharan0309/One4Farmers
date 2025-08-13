package com.example.farmers.weather

import androidx.annotation.DrawableRes
import com.example.farmers.R
import java.time.LocalDateTime

// UI State holder
data class WeatherState(
    val weatherInfo: WeatherInfo? = null,
    val locationName: String? = "Loading location...",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAnalyzing: Boolean = false,
    val analysisReport: String? = null
)

// Processed data ready for the UI
data class WeatherInfo(
    val currentWeatherData: WeatherData,
    val dailyWeatherData: List<WeatherData>,
    val hourlyWeatherData: List<WeatherData>
)

// A single data point for either current, hourly, or daily forecast
data class WeatherData(
    val time: LocalDateTime,
    val temperature: Double,
    val temperatureMax: Double? = null,
    val temperatureMin: Double? = null,
    val humidity: Int? = null,
    val windSpeed: Double? = null,
    val weatherType: WeatherType
)

// Maps WMO codes from the API to a description and an icon
sealed class WeatherType(
    val weatherDesc: String,
    @DrawableRes val iconRes: Int
) {
    data object ClearSky : WeatherType("Clear sky", R.drawable.ic_sunny)
    data object MainlyClear : WeatherType("Mainly clear", R.drawable.ic_cloudy)
    data object PartlyCloudy : WeatherType("Partly cloudy", R.drawable.ic_cloudy)
    data object Overcast : WeatherType("Overcast", R.drawable.ic_cloudy)
    data object Foggy : WeatherType("Foggy", R.drawable.ic_cloudy)
    data object Drizzle : WeatherType("Drizzle", R.drawable.ic_rainy)
    data object Rain : WeatherType("Rain", R.drawable.ic_rainy)
    data object RainShowers : WeatherType("Rain showers", R.drawable.ic_rainy)
    data object Snow : WeatherType("Snow", R.drawable.ic_snowy)
    data object Thunderstorm : WeatherType("Thunderstorm", R.drawable.ic_thunder)

    companion object {
        fun fromWMO(code: Int): WeatherType {
            return when (code) {
                0 -> ClearSky
                1 -> MainlyClear
                2 -> PartlyCloudy
                3 -> Overcast
                45, 48 -> Foggy
                51, 53, 55 -> Drizzle
                61, 63, 65 -> Rain
                66, 67 -> Rain
                80, 81, 82 -> RainShowers
                71, 73, 75, 77, 85, 86 -> Snow
                95, 96, 99 -> Thunderstorm
                else -> ClearSky
            }
        }
    }
}