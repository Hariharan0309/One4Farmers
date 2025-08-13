package com.example.farmers.login

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.farmers.data.UserManager
import com.example.farmers.service.CreateSessionRequest
import com.example.farmers.service.FirebaseApiService
import com.example.farmers.service.RequestState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userManager: UserManager,
    private val apiService: FirebaseApiService,
) : ViewModel() {

    var name by mutableStateOf("")
        private set

    var email by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    var isLoginMode by mutableStateOf(true)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun onNameChange(newName: String) {
        name = newName
        errorMessage = null
    }

    fun onEmailChange(newEmail: String) {
        email = newEmail
        errorMessage = null
    }

    fun onPasswordChange(newPassword: String) {
        password = newPassword
        errorMessage = null
    }

    fun toggleLoginMode() {
        isLoginMode = !isLoginMode
        name = ""
        email = ""
        password = ""
        errorMessage = null
    }

    private fun subscribeToCommunityChatTopic() {
        Firebase.messaging.subscribeToTopic("community_chat_updates")
            .addOnCompleteListener { task ->
                val msg = if (task.isSuccessful) {
                    "Subscribed to community chat!"
                } else {
                    "Subscription to community chat failed."
                }
                Log.d("FCMTopic", msg)
            }
    }

    private fun performLogin(onSuccess: () -> Unit) {
        // Firebase Authentication (Login)
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    println("Firebase auth successful for: $email")
                    viewModelScope.launch {
                        isLoading = true
                        try {
                            val sessionResponse = apiService.createSession(CreateSessionRequest(email, null))

                            // Once the session is created, save all relevant data to the UserManager
                            userManager.setLoggedIn(true)
                            userManager.setSessionId(sessionResponse.session_id)

                            // Access the state object from the response
                            val userState = sessionResponse.state

                            // Populate the UserManager with data from the state object
                            userManager.setUserId(userState.user_id)
                            userManager.setUserName(userState.name)
                            userManager.setLatitude(userState.latitude.toString())
                            userManager.setLongitude(userState.longitude.toString())
                            userManager.setTimeZone(userState.timeZone)
                            userManager.setFarmingYears(userState.experience.toInt())
                            userManager.setPreferredLanguage(userState.language)

                            // *** SUBSCRIBE TO TOPIC ON SUCCESS ***
                            subscribeToCommunityChatTopic()

                            onSuccess()

                        } catch (e: Exception) {
                            println("Full error creating session: ${e.stackTraceToString()}")
                            errorMessage = "Unable to create session. Please try again."
                        } finally {
                            isLoading = false
                        }
                    }
                } else {
                    // Firebase Authentication failed
                    errorMessage = task.exception?.message ?: "An unknown authentication error occurred."
                    isLoading = false
                }
            }
    }

    private fun performSignUp(onSuccess: () -> Unit) {
        if (password.length < 6) {
            errorMessage = "Password must be at least 6 characters long."
            isLoading = false
            return
        }

        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    viewModelScope.launch {
                        try {
                            userManager.setUserName(name)
                            userManager.setUserId(email)

                            // *** SUBSCRIBE TO TOPIC ON SUCCESS ***
                            subscribeToCommunityChatTopic()
                            onSuccess()
                        } catch (e: Exception) {
                            errorMessage = "Account created, but failed to create session."
                        } finally {
                            isLoading = false
                        }
                    }
                } else {
                    errorMessage = task.exception?.message
                    isLoading = false
                }
            }
    }

    fun onSubmit(onSuccess: () -> Unit) {
        if (isLoading) return

        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Email and password cannot be empty."
            return
        }

        if (!isLoginMode) {
            if (name.isBlank()) {
                errorMessage = "All fields are required for sign up."
                return
            }
        }

        isLoading = true
        errorMessage = null

        if (isLoginMode) {
            performLogin(onSuccess)
        } else {
            performSignUp(onSuccess)
        }
    }
}