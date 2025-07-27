package com.example.farmers.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.farmers.data.UserManager
import com.example.farmers.service.FirebaseApiService
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class DeliveryLoginViewModel @Inject constructor(
    private val userManager: UserManager,
    private val apiService: FirebaseApiService // Kept for future use
) : ViewModel() {

    var email by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // List of authorized delivery agent emails
    private val authorizedDeliveryEmails = listOf("delivery1@one4farmers.com", "delivery2@one4farmers.com")

    fun onEmailChange(value: String) {
        email = value.trim()
        errorMessage = null
    }

    fun onPasswordChange(value: String) {
        password = value
        errorMessage = null
    }

    fun login(onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = "Email and password cannot be empty."
            return
        }

        // ✅ Check if the email is authorized before contacting Firebase
        if (email !in authorizedDeliveryEmails) {
            errorMessage = "Not a registered delivery agent."
            return
        }

        isLoading = true
        errorMessage = null

        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    viewModelScope.launch {
                        try {
                            // Store user and logged in details
                            userManager.setDeliveryLoggedIn(true)
                            userManager.setUserId(email)

                            onSuccess()
                        } catch (e: Exception) {
                            errorMessage = "Login successful, but failed to save session."
                        } finally {
                            isLoading = false
                        }
                    }
                } else {
                    // Handle Firebase authentication failure
                    errorMessage = task.exception?.message
                    isLoading = false
                }
            }
    }
}