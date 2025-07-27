package com.example.farmers.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.farmers.R
import kotlinx.coroutines.delay

/**
 * A splash screen that displays a background image for 2 seconds
 * and then automatically navigates away.
 *
 * @param onGetStartedClick Callback to trigger navigation to the next screen.
 */
@Composable
fun WelcomeScreen(
    onGetStartedClick: () -> Unit
) {
    // This effect will run once when the screen is first displayed.
    LaunchedEffect(key1 = true) {
        delay(1000L)
        onGetStartedClick()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.homeimage3),
            contentDescription = "Application Splash Screen",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}