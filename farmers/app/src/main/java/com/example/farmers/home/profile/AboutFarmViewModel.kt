package com.example.farmers.home.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.farmers.data.UserManager
import com.example.farmers.service.CreateSessionRequest
import com.example.farmers.service.FirebaseApiService
import com.example.farmers.service.RequestState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

data class CropUiState(
    var name: String = "",
    var area: String = "",
    var months: String = "",
    var nameError: String? = null,
    var areaError: String? = null,
    var monthsError: String? = null
)

@HiltViewModel
class AboutFarmViewModel @Inject constructor(
    private val firebaseApiService: FirebaseApiService,
    private val userManager: UserManager
) : ViewModel() {

    // --- State for location dropdowns ---
    var selectedState by mutableStateOf("")
        private set
    var selectedDistrict by mutableStateOf("")
        private set
    var availableDistricts by mutableStateOf<List<String>>(emptyList())
        private set
    var isStateMenuExpanded by mutableStateOf(false)
        private set
    var isDistrictMenuExpanded by mutableStateOf(false)
        private set

    // --- NEW: State for language dropdown ---
    var selectedLanguage by mutableStateOf(userManager.getPreferredLanguage())
        private set
    var isLanguageMenuExpanded by mutableStateOf(false)
        private set
    val languages = listOf("English", "Tamil", "Hindi")

    val states = listOf("Kerala", "Tamil Nadu", "Karnataka", "Andhra Pradesh")
    private val districtsByState = mapOf(
        "Kerala" to listOf("Thiruvananthapuram", "Kochi"),
        "Tamil Nadu" to listOf("Chennai", "Villupuram"),
        "Karnataka" to listOf("Bengaluru", "Mysuru"),
        "Andhra Pradesh" to listOf("Visakhapatnam", "Vijayawada")
    )

    // --- State for other form fields ---
    var totalLand by mutableStateOf("")
        private set
    var experience by mutableStateOf("")
        private set
    var latitude by mutableStateOf("")
        private set
    var longitude by mutableStateOf("")
        private set
    var timezone by mutableStateOf("")
        private set

    val cultivatedCrops = mutableStateListOf<CropUiState>()
    var isLoading by mutableStateOf(false)
        private set

    // --- State for validation errors ---
    var locationError by mutableStateOf<String?>(null)
    var totalLandError by mutableStateOf<String?>(null)
    var experienceError by mutableStateOf<String?>(null)
    var areaSumError by mutableStateOf<String?>(null)

    init {
        if (cultivatedCrops.isEmpty()) {
            cultivatedCrops.add(CropUiState())
        }
    }

    // --- Event Handlers ---
    fun onStateSelected(state: String) {
        selectedState = state
        isStateMenuExpanded = false
        availableDistricts = districtsByState[state] ?: emptyList()
        selectedDistrict = ""
        locationError = null
    }

    fun onDistrictSelected(district: String) {
        selectedDistrict = district
        isDistrictMenuExpanded = false
        locationError = null
    }

    // --- NEW: Language event handlers ---
    fun onLanguageSelected(language: String) {
        selectedLanguage = language
        isLanguageMenuExpanded = false
    }

    fun onLanguageMenuToggle(expanded: Boolean) {
        isLanguageMenuExpanded = expanded
    }

    fun onStateMenuToggle(expanded: Boolean) { isStateMenuExpanded = expanded }
    fun onDistrictMenuToggle(expanded: Boolean) {
        if (selectedState.isNotEmpty()) { isDistrictMenuExpanded = expanded }
    }

    fun onTotalLandChange(newArea: String) {
        totalLand = newArea
        totalLandError = null
    }

    fun onExperienceChange(newExperience: String) {
        experience = newExperience
        experienceError = null
    }

    fun updateGpsLocation(lat: Double, lon: Double) {
        latitude = "%.6f".format(lat)
        longitude = "%.6f".format(lon)
        timezone = TimeZone.getDefault().id
    }

    fun onCropNameChange(index: Int, newName: String) {
        cultivatedCrops[index] = cultivatedCrops[index].copy(name = newName, nameError = null)
    }

    fun onCropAreaChange(index: Int, newArea: String) {
        cultivatedCrops[index] = cultivatedCrops[index].copy(area = newArea, areaError = null)
    }

    fun onCropMonthsChange(index: Int, newMonths: String) {
        cultivatedCrops[index] = cultivatedCrops[index].copy(months = newMonths, monthsError = null)
    }

    fun addCrop() {
        cultivatedCrops.add(CropUiState())
    }

    fun removeCrop(index: Int) {
        if (cultivatedCrops.size > 1) {
            cultivatedCrops.removeAt(index)
        }
    }

    suspend fun saveFarmDetails(): Boolean {
        if (!validate()) {
            println("Validation Failed.")
            return false
        }

        isLoading = true
        return try {
            val finalLocation = "$selectedDistrict, $selectedState"
            println("Validation Successful! Saving data for location: $finalLocation")



            val sessionResponse = firebaseApiService.createSession(
                request = CreateSessionRequest(
                    user_id = userManager.getUserId(),
                    state = RequestState(
                        experience = experience.toInt(),
                        latitude = latitude.toDoubleOrNull(),
                        longitude = longitude.toDoubleOrNull(),
                        timeZone = timezone,
                        district = selectedDistrict,
                        state = selectedState,
                        name = userManager.getUserName(),
                        crops = cultivatedCrops.map { it.name },
                        user_id = userManager.getUserId(),
                        acres = totalLand.toInt(),
                        language = selectedLanguage
                    )
                )
            )
            userManager.setLoggedIn(true)
            userManager.setSessionId(sessionResponse.session_id)
            userManager.setLongitude(longitude)
            userManager.setLatitude(latitude)
            userManager.setFarmingYears(experience.toInt())
            userManager.setTimeZone(timezone)
            userManager.setPreferredLanguage(selectedLanguage)

            isLoading = false
            true
        } catch (e: Exception) {
            println("API call failed: ${e.message}")
            isLoading = false
            false
        }
    }

    private fun validate(): Boolean {
        locationError = null
        totalLandError = null
        experienceError = null
        areaSumError = null
        cultivatedCrops.forEachIndexed { index, _ ->
            cultivatedCrops[index] = cultivatedCrops[index].copy(
                nameError = null, areaError = null, monthsError = null
            )
        }

        var isValid = true

        if (selectedState.isBlank() || selectedDistrict.isBlank()) {
            locationError = "State and District must be selected"
            isValid = false
        }
        if (totalLand.isBlank()) {
            totalLandError = "Total land area cannot be empty"
            isValid = false
        }
        if (experience.isBlank()) {
            experienceError = "Experience cannot be empty"
            isValid = false
        }

        if (latitude.isBlank() || longitude.isBlank()) {
            areaSumError = "Please fetch the farm's GPS location before saving."
            isValid = false
        }

        cultivatedCrops.forEachIndexed { index, crop ->
            if (crop.name.isBlank()) {
                cultivatedCrops[index] = crop.copy(nameError = "Cannot be empty")
                isValid = false
            }
            if (crop.area.isBlank()) {
                cultivatedCrops[index] = crop.copy(areaError = "Cannot be empty")
                isValid = false
            }
            if (crop.months.isBlank()) {
                cultivatedCrops[index] = crop.copy(monthsError = "Cannot be empty")
                isValid = false
            }
        }

        if (!isValid) return false

        val totalLandValue = totalLand.toDoubleOrNull()
        if (totalLandValue == null) {
            totalLandError = "Please enter a valid number for total land"
            isValid = false
        }
        if (experience.toIntOrNull() == null) {
            experienceError = "Please enter a valid number for years"
            isValid = false
        }

        var cultivatedSum = 0.0
        for ((index, crop) in cultivatedCrops.withIndex()) {
            val areaValue = crop.area.toDoubleOrNull()
            if (areaValue == null) {
                cultivatedCrops[index] = crop.copy(areaError = "Invalid number")
                isValid = false
            } else {
                cultivatedSum += areaValue
            }

            if (crop.months.toIntOrNull() == null) {
                cultivatedCrops[index] = crop.copy(monthsError = "Invalid number")
                isValid = false
            }
        }

        if (!isValid) return false

        if (totalLandValue != null && cultivatedSum > totalLandValue) {
            areaSumError = "Sum of cultivated areas ($cultivatedSum acres) cannot exceed total land area ($totalLandValue acres)."
            isValid = false
        }

        return isValid
    }
}