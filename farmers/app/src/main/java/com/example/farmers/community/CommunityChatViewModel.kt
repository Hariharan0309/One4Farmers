package com.example.farmers.community

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.farmers.data.UserManager
import com.example.farmers.service.CommunityMessage
import com.example.farmers.service.FirebaseApiService
import com.example.farmers.service.SendCommunityMessageRequest
import com.example.farmers.service.TextToSpeechService
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// --- NEW: UI-specific data class, separate from the API model ---
data class UiCommunityMessage(
    val id: String, // A unique ID for UI purposes (timestamp or random)
    val senderId: String,
    val senderName: String,
    val text: String,
    val text_ta: String?,
    val text_hi: String?,
    val audioUrl: String?,
    val timestamp: String?,
    val isLoading: Boolean = false
)

// --- NEW: UI state holder, similar to ChatUiState ---
data class CommunityChatUiState(
    val messages: List<UiCommunityMessage> = emptyList(),
    val isLoading: Boolean = true,
    val currentlyPlayingMessageId: String? = null
)

@HiltViewModel
class CommunityChatViewModel @Inject constructor(
    private val apiService: FirebaseApiService,
    private val userManager: UserManager,
    private val textToSpeechService: TextToSpeechService,
    private val application: Application
) : ViewModel() {

    // The ViewModel now holds the new UI state object
    private val _uiState = MutableStateFlow(CommunityChatUiState())
    val uiState = _uiState.asStateFlow()

    private val _messageText = MutableStateFlow("")
    val messageText = _messageText.asStateFlow()

    val preferredLanguage = userManager.getPreferredLanguage()
    private val storage = Firebase.storage

    init {
        fetchInitialMessages()
        viewModelScope.launch {
            newMessageEvent.collect { newMessageFromApi ->
                // When a new message arrives, map it to a UI message and add it
                val newUiMessage = mapToUiMessage(newMessageFromApi)
                _uiState.update { currentState ->
                    currentState.copy(messages = currentState.messages + newUiMessage)
                }
            }
        }
    }

    private fun fetchInitialMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val response = apiService.getCommunityMessages()
                // Map the list of API models to a list of UI models
                val uiMessages = response.messages.map { mapToUiMessage(it) }
                _uiState.update { it.copy(messages = uiMessages, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onMessageTextChanged(text: String) {
        _messageText.value = text
    }

    fun sendMessage(senderId: String, senderName: String, audioUri: Uri? = null) {
        val text = _messageText.value.trim()
        if (text.isBlank() && audioUri == null) return

        val tempId = System.currentTimeMillis().toString()

        // 1. Create a temporary UI message with a loading state
        val optimisticMessage = UiCommunityMessage(
            id = tempId,
            senderId = senderId,
            senderName = senderName,
            text = text,
            text_ta = null,
            text_hi = null,
            audioUrl = null,
            timestamp = tempId,
            isLoading = true
        )
        _uiState.update { it.copy(messages = it.messages + optimisticMessage) }
        _messageText.value = ""

        viewModelScope.launch {
            val finalAudioUrl = audioUri?.let { uploadAudioToStorage(it) }

            // 2. Find the temporary message and update it with the final URL and loading state
            _uiState.update { currentState ->
                val updatedMessages = currentState.messages.map {
                    if (it.id == tempId) {
                        it.copy(isLoading = false, audioUrl = finalAudioUrl)
                    } else {
                        it
                    }
                }
                currentState.copy(messages = updatedMessages)
            }

            // 3. Create the separate API request model and send it
            val request = SendCommunityMessageRequest(
                senderId = senderId,
                senderName = senderName,
                text = text,
                audio_url = finalAudioUrl
            )
            try {
                apiService.sendCommunityMessage(request)
            } catch (e: Exception) {
                Log.e("CommunityChatVM", "Failed to send API request", e)
            }
        }
    }

    private suspend fun uploadAudioToStorage(audioUri: Uri): String? {
        // ... (this function remains the same)
        return try {
            val fileName = "community_audio/${System.currentTimeMillis()}.m4a"
            val storageRef = storage.reference.child(fileName)
            storageRef.putFile(audioUri).await()
            storageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e("FirebaseUpload", "Audio upload failed", e)
            null
        }
    }

    fun onPlayToggle(message: UiCommunityMessage) {
        // ... (this function's logic remains the same, just uses UiCommunityMessage)
        val textToPlay = when (preferredLanguage) {
            "Tamil" -> message.text_ta ?: message.text
            "Hindi" -> message.text_hi ?: message.text
            else -> message.text
        }
        if (textToPlay.isBlank()) return

        val currentPlayingId = _uiState.value.currentlyPlayingMessageId

        if (currentPlayingId == message.id) {
            textToSpeechService.stop()
            _uiState.update { it.copy(currentlyPlayingMessageId = null) }
        } else {
            textToSpeechService.stop()
            _uiState.update { it.copy(currentlyPlayingMessageId = message.id) }
            textToSpeechService.speak(textToPlay) {
                viewModelScope.launch {
                    _uiState.update { it.copy(currentlyPlayingMessageId = null) }
                }
            }
        }
    }

    // Helper function to map from the API model to the UI model
    private fun mapToUiMessage(apiMessage: CommunityMessage): UiCommunityMessage {
        val id = apiMessage.senderId + apiMessage.timestamp
        return UiCommunityMessage(
            id = id,
            senderId = apiMessage.senderId,
            senderName = apiMessage.senderName,
            text = apiMessage.text,
            text_ta = apiMessage.text_ta,
            text_hi = apiMessage.text_hi,
            audioUrl = apiMessage.audio_url,
            timestamp = apiMessage.timestamp,
            isLoading = false
        )
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeechService.shutdown()
    }

    companion object {
        private val newMessageEvent = MutableSharedFlow<CommunityMessage>()
        fun onNewMessageReceived(message: CommunityMessage) {
            newMessageEvent.tryEmit(message)
        }
    }
}