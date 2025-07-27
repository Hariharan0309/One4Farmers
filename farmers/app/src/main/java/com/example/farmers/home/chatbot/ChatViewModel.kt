package com.example.chatbot

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.farmers.data.UserManager
import com.example.farmers.service.FirebaseApiService
import com.example.farmers.service.StreamQueryRequest
import com.example.farmers.service.TextToSpeechService
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

enum class Participant { USER, BOT }

data class Message(
    val id: Long,
    val participant: Participant,
    val text: String? = null,
    val audioUrl: String? = null,
    val imageUrl: String? = null,
    val localImageUri: Uri? = null,
    val isLoading: Boolean = false
)

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val currentlyPlayingMessageId: Long? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val userManager: UserManager,
    private val apiService: FirebaseApiService,
    private val textToSpeechService: TextToSpeechService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val storage = Firebase.storage

    init {
        _uiState.update {
            it.copy(
                messages = listOf(
                    Message(id = System.currentTimeMillis(), participant = Participant.BOT, text = "Hello! How can I assist you today?")
                )
            )
        }
    }

    fun sendMessage(text: String? = null, audioUri: Uri? = null, imageUri: Uri? = null) {
        val cleanText = text?.takeIf { it.isNotBlank() }
        if (cleanText == null && audioUri == null && imageUri == null) {
            return
        }

        val userMessageId = System.currentTimeMillis()

        _uiState.update {
            it.copy(
                messages = it.messages + Message(
                    id = userMessageId,
                    participant = Participant.USER,
                    text = cleanText,
                    localImageUri = imageUri,
                    isLoading = true
                )
            )
        }

        viewModelScope.launch {
            val imageUrlDeferred = async { imageUri?.let { uploadImageToStorage(it) } }
            val audioUrlDeferred = async { audioUri?.let { uploadAudioToStorage(it) } }

            val finalImageUrl = imageUrlDeferred.await()
            val finalAudioUrl = audioUrlDeferred.await()

            _uiState.update { currentState ->
                val updatedMessages = currentState.messages.map { msg ->
                    if (msg.id == userMessageId) {
                        msg.copy(isLoading = false, imageUrl = finalImageUrl, audioUrl = finalAudioUrl)
                    } else {
                        msg
                    }
                }
                currentState.copy(messages = updatedMessages)
            }

            val loadingBotMessageId = System.currentTimeMillis()
            _uiState.update {
                it.copy(messages = it.messages + Message(id = loadingBotMessageId, participant = Participant.BOT, isLoading = true))
            }

            val userId = userManager.getUserId()
            val sessionId = userManager.getSessionId()
            if (userId == null || sessionId == null) {
                updateBotMessage("Error: Could not find user session. Please restart the app.", loadingBotMessageId)
                return@launch
            }

            try {
                val request = StreamQueryRequest(
                    user_id = userId,
                    session_id = sessionId,
                    message = cleanText,
                    audio_url = finalAudioUrl,
                    image_url = finalImageUrl
                )
                val response = apiService.streamQueryAgent(request)
                updateBotMessage(response.response, loadingBotMessageId)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "API call failed", e)
                updateBotMessage("Sorry, I'm having trouble connecting. Please try again later.", loadingBotMessageId)
            }
        }
    }

    private suspend fun uploadAudioToStorage(audioUri: Uri): String? {
        return try {
            val fileName = "audio_messages/${System.currentTimeMillis()}.m4a"
            val storageRef = storage.reference.child(fileName)
            storageRef.putFile(audioUri).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.d("FirebaseUpload", "Audio upload successful. URL: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e("FirebaseUpload", "Audio upload failed", e)
            null
        }
    }

    private suspend fun uploadImageToStorage(imageUri: Uri): String? {
        return try {
            val fileName = "image_messages/${System.currentTimeMillis()}.jpg"
            val storageRef = storage.reference.child(fileName)
            storageRef.putFile(imageUri).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            Log.d("FirebaseUpload", "Image upload successful. URL: $downloadUrl")
            downloadUrl
        } catch (e: Exception) {
            Log.e("FirebaseUpload", "Image upload failed", e)
            null
        }
    }

    private fun updateBotMessage(text: String, messageIdToUpdate: Long) {
        _uiState.update { currentState ->
            val updatedMessages = currentState.messages.map {
                if (it.id == messageIdToUpdate) {
                    it.copy(text = text, isLoading = false)
                } else {
                    it
                }
            }
            currentState.copy(messages = updatedMessages)
        }
    }

    /**
     * **New helper function** to remove Markdown characters for clean TTS playback.
     */
    private fun cleanMarkdownForTts(markdownText: String): String {
        // Removes common markdown syntax like *, #, _, and ~
        return markdownText.replace(Regex("[*#_~]"), "")
    }

    fun onPlayToggle(message: Message) {
        val markdownText = message.text ?: return
        val plainTextToPlay = cleanMarkdownForTts(markdownText)

        val currentPlayingId = uiState.value.currentlyPlayingMessageId

        if (currentPlayingId == message.id) {
            textToSpeechService.stop()
            _uiState.update { it.copy(currentlyPlayingMessageId = null) }
        } else {
            if (currentPlayingId != null) {
                textToSpeechService.stop()
            }

            _uiState.update { it.copy(currentlyPlayingMessageId = message.id) }
            textToSpeechService.speak(plainTextToPlay) {
                viewModelScope.launch {
                    _uiState.update { it.copy(currentlyPlayingMessageId = null) }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeechService.shutdown()
    }
}