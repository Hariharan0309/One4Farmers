package com.example.farmers.weather

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.farmers.R
import com.example.farmers.ui.theme.PrimaryGreen
import com.example.farmers.ui.theme.SecondaryGreen
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel = hiltViewModel()
) {
    val state = viewModel.state

    // Use a light gradient background for a softer, more modern look
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            PrimaryGreen.copy(alpha = 0.3f),
            Color.White
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        if (state.weatherInfo != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp)
            ) {
                // The main weather card remains green to be the focal point
                CurrentWeatherCard(state = state)
                Spacer(modifier = Modifier.height(24.dp))

                // Subsequent cards are white for contrast
                HourlyForecast(state = state)
                Spacer(modifier = Modifier.height(24.dp))
                WeeklyForecast(state = state)
                Spacer(modifier = Modifier.height(16.dp))

                if ((state.weatherInfo.currentWeatherData.temperature) > 33) {
                    ExcessiveHeatWarning()
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Analysis section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = viewModel::generateAnalysisReport,
                        enabled = !state.isAnalyzing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryGreen)
                    ) {
                        if (state.isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Text("Generate Weather Analysis Report", color = Color.White, fontSize = 16.sp)
                        }
                    }

                    state.analysisReport?.let { report ->
                        Spacer(modifier = Modifier.height(16.dp))
                        AnalysisReportCard(reportText = report)
                    }
                }
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        state.error?.let { error ->
            Text(
                text = error,
                color = Color.Red,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
fun AnalysisReportCard(reportText: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Article,
                    contentDescription = "Report Icon",
                    tint = SecondaryGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Weather Analysis Report",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black.copy(alpha = 0.8f)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color.LightGray.copy(alpha = 0.5f)
            )
            MarkdownText(
                markdown = reportText,
                fontSize = 14.sp,
                color = Color.Black.copy(alpha = 0.87f),
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun CurrentWeatherCard(state: WeatherState) {
    val data = state.weatherInfo?.currentWeatherData ?: return

    val cardBrush = Brush.linearGradient(
        colors = listOf(
            PrimaryGreen,
            SecondaryGreen
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 28.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.background(cardBrush)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Location and Time
                Text(text = state.locationName ?: "Chennai, Tamil Nadu", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Saturday, 26 July | 12:30 PM", fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f))

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = data.weatherType.iconRes),
                        contentDescription = data.weatherType.weatherDesc,
                        modifier = Modifier.size(100.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "${data.temperature.roundToInt()}°C",
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = data.weatherType.weatherDesc,
                    fontSize = 22.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.3f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WeatherInfoItem(value = "${data.humidity}%", label = "Humidity", icon = R.drawable.ic_humidity)
                    WeatherInfoItem(value = "${data.windSpeed?.roundToInt()} km/h", label = "Wind", icon = R.drawable.ic_wind)
                }
            }
        }
    }
}

@Composable
fun WeatherInfoItem(value: String, label: String, icon: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painter = painterResource(id = icon), contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = Color.White, fontWeight = FontWeight.SemiBold)
        Text(text = label, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

@Composable
fun HourlyForecast(state: WeatherState) {
    val data = state.weatherInfo?.hourlyWeatherData ?: return
    var isExpanded by remember { mutableStateOf(true) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Section header with toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Today", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black.copy(alpha = 0.8f))
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Toggle Today's Forecast",
                tint = Color.Black.copy(alpha = 0.8f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        AnimatedVisibility(visible = isExpanded) {
            // Card is now white, with a subtle border
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
            ) {
                LazyRow(
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val relevantData = data.filter { it.time.isAfter(LocalDateTime.now().minusHours(1)) }.take(24)
                    items(relevantData) { weatherData ->
                        HourlyForecastItem(data = weatherData)
                    }
                }
            }
        }
    }
}

@Composable
fun HourlyForecastItem(data: WeatherData) {
    val isCurrentHour = data.time.hour == LocalDateTime.now().hour
    val itemBackgroundColor = if (isCurrentHour) SecondaryGreen.copy(alpha = 0.2f) else Color(0xFFF0F2F5)
    val textColor = if (isCurrentHour) SecondaryGreen else Color.Black.copy(alpha = 0.8f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color = itemBackgroundColor)
            .padding(horizontal = 12.dp, vertical = 16.dp)
            .width(65.dp)
    ) {
        Text(
            text = if (isCurrentHour) "Now" else data.time.format(DateTimeFormatter.ofPattern("ha")).lowercase(),
            color = textColor,
            fontWeight = if (isCurrentHour) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
        Image(
            painter = painterResource(id = data.weatherType.iconRes),
            contentDescription = data.weatherType.weatherDesc,
            modifier = Modifier.size(35.dp)
        )
        Text(
            text = "${data.temperature.roundToInt()}°",
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
fun WeeklyForecast(state: WeatherState) {
    val data = state.weatherInfo?.dailyWeatherData ?: return
    var isExpanded by remember { mutableStateOf(true) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "7-Day Forecast", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black.copy(alpha = 0.8f))
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Toggle 7-Day Forecast",
                tint = Color.Black.copy(alpha = 0.8f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        AnimatedVisibility(visible = isExpanded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) {
                    data.forEachIndexed { index, weatherData ->
                        DailyForecastItem(data = weatherData)
                        if (index < data.lastIndex) {
                            HorizontalDivider(
                                color = Color.LightGray.copy(alpha = 0.4f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailyForecastItem(data: WeatherData) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = data.time.format(DateTimeFormatter.ofPattern("EEEE")),
            color = Color.Black.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Image(
            painter = painterResource(id = data.weatherType.iconRes),
            contentDescription = data.weatherType.weatherDesc,
            modifier = Modifier
                .size(40.dp)
                .padding(horizontal = 8.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(100.dp)
        ) {
            Text(
                text = "${data.temperatureMax?.roundToInt()}°",
                color = Color.Black.copy(alpha = 0.9f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${data.temperatureMin?.roundToInt()}°",
                color = Color.Gray,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ExcessiveHeatWarning() {
//    Card(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(horizontal = 16.dp),
//        shape = RoundedCornerShape(16.dp),
//        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E6)),
//        border = BorderStroke(1.dp, Color(0xFFFFD5A0))
//    ) {
//        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
//            Icon(
//                painter = painterResource(id = R.drawable.ic_warning),
//                contentDescription = "Warning",
//                tint = Color(0xFFD97706),
//                modifier = Modifier.size(32.dp)
//            )
//            Spacer(modifier = Modifier.width(12.dp))
//            Column {
//                Text(text = "Excessive Heat Warning", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9A3412))
//                Text(text = "High temperatures expected. Stay hydrated and avoid prolonged sun exposure.", color = Color(0xFFB45309), lineHeight = 20.sp)
//            }
//        }
//    }
}
