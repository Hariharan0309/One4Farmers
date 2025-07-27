package com.example.farmers.weather

import android.app.Application
import android.location.Geocoder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.farmers.data.UserManager
import com.example.farmers.service.FirebaseApiService
import com.example.farmers.service.StreamQueryRequest
import com.example.farmers.service.WeatherApiService
import com.example.farmers.service.WeatherDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weatherApi: WeatherApiService,
    private val firebaseApiService: FirebaseApiService,
    private val app: Application,
    private val userManager: UserManager
): ViewModel() {

    var state by mutableStateOf(WeatherState())
        private set

    init {
        val latString = userManager.getLatitude()
        val lonString = userManager.getLongitude()

        if (!latString.isNullOrBlank() && !lonString.isNullOrBlank()) {
            val lat = latString.toDoubleOrNull()
            val lon = lonString.toDoubleOrNull()

            if (lat != null && lon != null) {
                loadWeatherInfo(lat, lon)
            } else {
                state = state.copy(error = "Invalid location data format.")
            }
        } else {
            state = state.copy(error = "Farm location not set. Please update your profile.")
        }
    }

    fun generateAnalysisReport() {
        val userId = userManager.getUserId()
        val sessionId = userManager.getSessionId()

        if (userId == null || sessionId == null) {
            state = state.copy(analysisReport = "Error: User session not found. Please log in again.")
            return
        }

        viewModelScope.launch {
            // Set loading state for the analysis
            state = state.copy(isAnalyzing = true, analysisReport = null)
            val language = userManager.getPreferredLanguage()

            try {
                val request = StreamQueryRequest(
                    user_id = userId,
                    session_id = sessionId,
                    message = "Generate weather analysis for the next 7 days, even if you provided analysis before give result now in $language",
                    audio_url = null,
                    image_url = null
                )

                val response = firebaseApiService.streamQueryAgent(request)
                state = state.copy(
                    isAnalyzing = false,
                    analysisReport = response.response // Assuming 'response' is a field in your response object
                )
            } catch (e: Exception) {
                val errorMessage = "Sorry, I'm having trouble connecting. Please try again."
                state = state.copy(isAnalyzing = false, analysisReport = errorMessage)
                Log.e("generateAnalysisReport", "API call failed", e)
            }
        }
    }

    fun loadWeatherInfo(lat: Double, long: Double) {
        viewModelScope.launch {
            state = state.copy(isLoading = true, error = null)
            try {
                val geocoder = Geocoder(app, Locale.getDefault())
                val address = geocoder.getFromLocation(lat, long, 1)?.firstOrNull()
                val locationName = if (address != null) {
                    val city = address.locality ?: address.subAdminArea ?: "Unknown City"
                    val stateName = address.adminArea ?: "Unknown State"
                    "$city, $stateName"
                } else {
                    "Unknown Location"
                }

                val weatherDto = weatherApi.getWeatherData(lat, long)
                val weatherInfo = weatherDto.toWeatherInfo()

                if (weatherInfo != null) {
                    state = state.copy(
                        weatherInfo = weatherInfo,
                        locationName = locationName,
                        isLoading = false,
                        error = null
                    )
                } else {
                    state = state.copy(
                        isLoading = false,
                        error = "Failed to parse weather data."
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                state = state.copy(
                    isLoading = false,
                    error = "Failed to load weather: ${e.message}"
                )
            }
        }
    }

    /**
     * Correctly maps the DTO using the new property names.
     */
    private fun WeatherDto.toWeatherInfo(): WeatherInfo? {
        val currentData = current?.let {
            WeatherData(
                time = LocalDateTime.now(),
                temperature = it.temperature_2m ?: 0.0,
                humidity = it.relative_humidity_2m ?: 0,
                windSpeed = it.wind_speed_10m ?: 0.0,
                weatherType = WeatherType.fromWMO(it.weather_code ?: 0)
            )
        } ?: return null

        val hourlyData = hourly?.time?.mapIndexedNotNull { index, timeString ->
            val temperature = hourly.temperature_2m?.getOrNull(index)
            val weatherCode = hourly.weather_code?.getOrNull(index)
            if (temperature != null && weatherCode != null) {
                WeatherData(
                    time = LocalDateTime.parse(timeString, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    temperature = temperature,
                    weatherType = WeatherType.fromWMO(weatherCode)
                )
            } else {
                null
            }
        } ?: emptyList()

        val dailyData = daily?.time?.mapIndexedNotNull { index, dateString ->
            val maxTemp = daily.temperature_2m_max?.getOrNull(index)
            val minTemp = daily.temperature_2m_min?.getOrNull(index)
            val weatherCode = daily.weather_code?.getOrNull(index)
            if (maxTemp != null && minTemp != null && weatherCode != null) {
                WeatherData(
                    time = LocalDateTime.parse(dateString + "T00:00:00"),
                    temperatureMax = maxTemp,
                    temperatureMin = minTemp,
                    weatherType = WeatherType.fromWMO(weatherCode),
                    temperature = 0.0
                )
            } else {
                null
            }
        } ?: emptyList()

        return WeatherInfo(
            currentWeatherData = currentData,
            hourlyWeatherData = hourlyData,
            dailyWeatherData = dailyData
        )
    }
}