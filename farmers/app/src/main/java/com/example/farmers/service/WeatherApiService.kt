package com.example.farmers.service

// No longer need to import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    @GET("v1/forecast?hourly=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m")
    suspend fun getWeatherData(
        @Query("latitude") lat: Double,
        @Query("longitude") long: Double,
        @Query("timezone") timezone: String = "auto"
    ): WeatherDto
}

// Corrected Data Classes with matching names
data class WeatherDto(
    val hourly: HourlyWeatherDto? = null,
    val daily: DailyWeatherDto? = null,
    val current: CurrentWeatherDto? = null
)

data class CurrentWeatherDto(
    val temperature_2m: Double? = null,
    val relative_humidity_2m: Int? = null,
    val weather_code: Int? = null,
    val wind_speed_10m: Double? = null
)

data class HourlyWeatherDto(
    val time: List<String>? = null,
    val temperature_2m: List<Double>? = null,
    val weather_code: List<Int>? = null
)

data class DailyWeatherDto(
    val time: List<String>? = null,
    val weather_code: List<Int>? = null,
    val temperature_2m_max: List<Double>? = null,
    val temperature_2m_min: List<Double>? = null
)