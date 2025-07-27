package com.example.farmers.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.farmers.community.CommunityChatScreen
import com.example.farmers.home.chatbot.ChatScreen
import com.example.farmers.data.UserManager
import com.example.farmers.discovery.ProductDiscoveryScreen
import com.example.farmers.disease.DiseaseClassificationScreen
import com.example.farmers.home.HomeScreen
import com.example.farmers.home.delivery.DeliveryHomeScreen
import com.example.farmers.home.profile.AboutFarmScreen
import com.example.farmers.login.DeliveryLoginScreen
import com.example.farmers.login.LoginScreen
import com.example.farmers.sell.SellProductScreen
import com.example.farmers.weather.WeatherScreen
import com.example.farmers.welcome.WelcomeScreen

@Composable
fun AppNavigation(userManager: UserManager) {
    val navController = rememberNavController()

    val startDestination = if (userManager.isLoggedIn()) "home" else if(userManager.isDeliveryLoggedIn()) "delivery_home" else "welcome"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("welcome") {
            WelcomeScreen(
                onGetStartedClick = { navController.navigate("login") }
            )
        }

        composable(route = "login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("welcome") { inclusive = true }
                    }
                },
                onSignUpSuccess = {
                    navController.navigate("aboutFarm") {
                        popUpTo("aboutFarm") { inclusive = true }
                    }
                },
                onNavigateToDeliveryLogin = { navController.navigate("delivery_login") }
            )
        }

        composable("home") {
            HomeScreen(
                onLogout = {
                    userManager.logout()
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                },
//                onNavigateToChatBot = {
//                    navController.navigate("chatBot")
//                },
                onNavigateToChatBot = { prompt ->
                    // The route will be "chatBot?prompt=your_message" or just "chatBot" if prompt is null
                    val route = if (!prompt.isNullOrBlank()) "chatBot?prompt=$prompt" else "chatBot"
                    navController.navigate(route)
                },
                onNavigateToCropDiagnosis = {
                    navController.navigate("diagnosis")
                },
                onAboutFarm = {
                    navController.navigate("aboutFarm")
                },
                onNavigateToWeather = {
                    navController.navigate("weather")
                },
                onNavigateToProductDiscovery = {
                    navController.navigate("product_discovery")
                },
                onNavigateToSellProduct = {
                    navController.navigate("sellProduct")
                },
                onNavigateToCommunity = {
                    val userName = userManager.getUserName()
                    val userId = userManager.getUserId()

                    if (!userName.isNullOrBlank() && !userId.isNullOrBlank()) {
                        navController.navigate("community")
                    }

                },
                userManager = userManager
            )
        }

//        composable("chatBot") {
//            ChatScreen()
//        }

        composable(
            route = "chatBot?prompt={prompt}", // Define route with an optional argument
            arguments = listOf(
                navArgument("prompt") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            // Extract the argument
            val prompt = backStackEntry.arguments?.getString("prompt")
            // Pass it to your screen
            ChatScreen(
                prefilledPrompt = prompt
            )
        }

        composable("diagnosis") {
            DiseaseClassificationScreen(
                userManager = userManager
            )
        }

        composable("aboutFarm") {
            AboutFarmScreen(
                navigateToHome = {
                    navController.navigate("home")
                }
            )
        }

        composable("weather") {
            WeatherScreen()
        }

        composable(route = "product_discovery") {
            ProductDiscoveryScreen()
        }

        composable(route = "sellProduct") {
            SellProductScreen(
                onBackClick = { navController.navigateUp() }
            )
        }

        composable(route = "delivery_login") {
            DeliveryLoginScreen(
                onBackClick = navController::navigateUp,
                onLoginSuccess = { navController.navigate("delivery_home") {
                    popUpTo("delivery_login") { inclusive = true }
                } }
            )
        }

        composable(route = "delivery_home") {
            DeliveryHomeScreen(
                onLogout = {
                    userManager.logout()
                    navController.navigate("delivery_login") {
                        popUpTo("delivery_home") { inclusive = true }
                    }
                }
            )
        }

        composable(route = "community") {
            CommunityChatScreen(
                currentUserName = userManager.getUserName() ?: return@composable,
                currentUserId = userManager.getUserId() ?: return@composable
            )
        }
    }
}