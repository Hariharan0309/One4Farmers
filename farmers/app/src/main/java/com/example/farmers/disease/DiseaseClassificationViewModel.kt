package com.example.farmers.disease

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.farmers.data.UserManager
import com.example.farmers.service.FirebaseApiService
import com.example.farmers.service.StreamQueryRequest
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class DiseaseScreenUiState(
    val selectedImageUri: Uri? = null,
    val isLoading: Boolean = false,
    val isAnalyzing: Boolean = false,
    val uploadProgress: Float = 0f,
    val analysisResult: String? = null,
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class DiseaseClassificationViewModel @Inject constructor(
    private val apiService: FirebaseApiService,
    private val userManager: UserManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiseaseScreenUiState())
    val uiState = _uiState.asStateFlow()

    private val storage = Firebase.storage

    fun onImageSelected(uri: Uri?) {
        uri?.let {
            _uiState.value = DiseaseScreenUiState(selectedImageUri = it)
        }
    }

    fun uploadImageAndAnalyze(userId: String, sessionId: String, description: String) {
        val imageUri = _uiState.value.selectedImageUri ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isSuccess = false) }

            val fileName = "plant_diseases/${System.currentTimeMillis()}.jpg"
            val storageRef = storage.reference.child(fileName)

            try {
                storageRef.putFile(imageUri)
                    .addOnProgressListener { taskSnapshot ->
                        val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                        _uiState.update { it.copy(uploadProgress = (progress / 100).toFloat()) }
                    }
                    .await()

                val downloadUrl = storageRef.downloadUrl.await().toString()
                Log.d("FirebaseUpload", "Upload successful. URL: $downloadUrl")

                _uiState.update { it.copy(isLoading = false, isAnalyzing = true) }
                classifyDiseaseWithApi(userId, sessionId, downloadUrl, description)

            } catch (e: Exception) {
                Log.e("Firebase", "Upload or analysis failed", e)
                _uiState.update {
                    it.copy(isLoading = false, isAnalyzing = false, error = "Operation failed: ${e.message}")
                }
            }
        }
    }

    private suspend fun classifyDiseaseWithApi(userId: String, sessionId: String, imageUrl: String, description: String) {
        val language = userManager.getPreferredLanguage()
        val additionalText = "even if you provided analysis give result, and give me result in $language"

        val finalMessage = if (description.isNotBlank()) {
            "$description\n\n$additionalText"
        } else {
            additionalText
        }

        val request = StreamQueryRequest(
            session_id = sessionId,
            image_url = imageUrl,
            message = finalMessage,
            user_id = userId,
            audio_url = null
        )

        try {
            val response = apiService.streamQueryAgent(request)
            Log.d("API_CALL", "Disease classification request successful. Response: ${response.response}")
            _uiState.update {
                it.copy(
                    isAnalyzing = false,
                    isSuccess = true,
                    analysisResult = response.response
                )
            }
        } catch (e: Exception) {
            Log.e("API_CALL", "Disease classification request failed", e)
            _uiState.update {
                it.copy(isAnalyzing = false, error = "Analysis failed: ${e.message}")
            }
        }
    }

}
