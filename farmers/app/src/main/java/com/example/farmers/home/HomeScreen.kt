package com.example.farmers.home

import android.annotation.SuppressLint
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.farmers.data.UserManager
import com.example.farmers.ui.theme.PrimaryGreen
import com.example.farmers.ui.theme.SecondaryGreen
import androidx.compose.material.icons.filled.Groups

// Data class for Bottom Navigation and Suggestions
data class NavFeatureItem(val title: String, val icon: ImageVector, val route: String)
data class SuggestionItem(val title: String, val subtitle: String, val onClick: () -> Unit)


@SuppressLint("ContextCastToActivity")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userManager: UserManager,
    onLogout: () -> Unit,
    onAboutFarm: () -> Unit,
    onNavigateToChatBot: (String?) -> Unit,
    onNavigateToCropDiagnosis: () -> Unit,
    onNavigateToProductDiscovery: () -> Unit,
    onNavigateToSellProduct: () -> Unit,
    onNavigateToWeather: () -> Unit,
    onNavigateToCommunity: () -> Unit
) {
    val showExitDialog = remember { mutableStateOf(false) }
    val activity = (LocalContext.current as? Activity)

    if (showExitDialog.value) {
        AlertDialog(
            onDismissRequest = { showExitDialog.value = false },
            title = { Text(text = "Exit App") },
            text = { Text("Are you sure you want to exit?") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog.value = false
                    activity?.finish()
                }) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog.value = false }) { Text("No") }
            }
        )
    }

    BackHandler(true) {
        showExitDialog.value = true
    }

    Scaffold(
        containerColor = Color(0xFFF7F9F9),
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryGreen,
                    titleContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(onClick = onAboutFarm) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profile",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Logout",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        bottomBar = {
            val items = listOf(
                NavFeatureItem("Buy", Icons.Default.ShoppingCart, "buy"),
                NavFeatureItem("Sell", Icons.Default.Sell, "sell"),
                NavFeatureItem("Community", Icons.Default.Groups, "community"),
                NavFeatureItem("Weather", Icons.Default.Cloud, "weather"),
                NavFeatureItem("Diagnosis", Icons.Default.BugReport, "diagnosis")
            )
            var selectedRoute by remember { mutableStateOf("") }

            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title, modifier = Modifier.size(24.dp)) },
                        label = { Text(
                            text = item.title,
                            fontSize = 11.sp,
                            maxLines = 1
                        ) },
                        selected = selectedRoute == item.route,
                        onClick = {
                            selectedRoute = item.route
                            when (item.route) {
                                "buy" -> onNavigateToProductDiscovery()
                                "sell" -> onNavigateToSellProduct()
                                "community" -> onNavigateToCommunity()
                                "weather" -> onNavigateToWeather()
                                "diagnosis" -> onNavigateToCropDiagnosis()
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryGreen,
                            selectedTextColor = PrimaryGreen,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = SecondaryGreen.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToChatBot(null) },
                containerColor = SecondaryGreen,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, "Chatbot")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "welcome_text_animation")
            val offset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "text_offset"
            )

            val brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF4CAF50),
                    Color(0xFF8BC34A),
                    Color(0xFFCDDC39),
                    Color(0xFF4CAF50),
                    Color(0xFF8BC34A)
                ),
                start = Offset(offset, 0f),
                end = Offset(offset + 300f, 0f)
            )

            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = "Welcome, ${userManager.getUserName() ?: "Farmer"}!",
                style = MaterialTheme.typography.headlineMedium.copy(
                    brush = brush
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(60.dp))

            // Suggestions Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))

                val suggestions = listOf(
                    SuggestionItem("Analyze Plant", "Use your camera to analyse plants/soil") { onNavigateToCropDiagnosis() },
                    SuggestionItem("Finance advisor", "Know financial advices and support") { onNavigateToChatBot("Can you provide financial advice for me") },
                    SuggestionItem("Govt. Schemes", "Find subsidies and support") { onNavigateToChatBot("list the government schemes") },
                    SuggestionItem("Beginner's Guide", "Gets tips and more about farming") { onNavigateToChatBot("Provide step by step guide for a beginner farmer") }
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(suggestions) { suggestion ->
                        SuggestionChip(
                            title = suggestion.title,
                            subtitle = suggestion.subtitle,
                            onClick = suggestion.onClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionChip(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(160.dp)
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                lineHeight = 16.sp
            )
        }
    }
}
