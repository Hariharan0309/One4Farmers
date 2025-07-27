package com.example.farmers.welcome

import androidx.lifecycle.ViewModel
import com.example.farmers.service.FirebaseApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val apiService: FirebaseApiService
) : ViewModel() {

}
