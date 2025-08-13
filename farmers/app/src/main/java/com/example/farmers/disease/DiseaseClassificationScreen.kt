package com.example.farmers.disease

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.example.farmers.BuildConfig
import com.example.farmers.data.UserManager
import com.example.farmers.ui.theme.PrimaryGreen
import com.example.farmers.ui.theme.SecondaryGreen
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiseaseClassificationScreen(
    userManager: UserManager,
    viewModel: DiseaseClassificationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var description by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? -> viewModel.onImageSelected(uri) }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success -> if (success) viewModel.onImageSelected(cameraImageUri) }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                cameraImageUri = createImageUri(context)
                cameraLauncher.launch(cameraImageUri!!)
            } else {
                Log.d("Permission", "Camera permission was denied.")
            }
        }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Plant/ Soil Analysis") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black,
                    scrolledContainerColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F9F9)) // Use a light, clean background
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ImagePreviewer(
                uri = uiState.selectedImageUri,
                onClick = { galleryLauncher.launch("image/*") }
            )
            Spacer(modifier = Modifier.height(24.dp))
            ImageSourceButtons(
                onGalleryClick = { galleryLauncher.launch("image/*") },
                onCameraClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Optional: Describe the issue or your queries...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 100.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = Color.LightGray,
                    focusedLabelColor = PrimaryGreen,
                    cursorColor = PrimaryGreen,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black.copy(alpha = 0.9f)
                )
            )

            Spacer(modifier = Modifier.weight(1f))
            val sessionId = userManager.getSessionId()
            val userId = userManager.getUserId()
            if (!sessionId.isNullOrEmpty() && !userId.isNullOrEmpty()) {
                UploadArea(
                    uiState = uiState,
                    onUploadClick = {
                        focusManager.clearFocus()
                        viewModel.uploadImageAndAnalyze(userId = userId, sessionId = sessionId, description = description)
                    }
                )
            }
        }
    }
}

private fun createImageUri(context: Context): Uri {
    val file = File.createTempFile("camera_photo_", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file)
}

@Composable
fun ImagePreviewer(uri: Uri?, onClick: () -> Unit) {
    val stroke = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f))
    val borderColor = if (uri == null) PrimaryGreen.copy(alpha = 0.6f) else Color.LightGray

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(color = borderColor, style = stroke, cornerRadius = CornerRadius(16.dp.toPx()))
        }
        if (uri != null) {
            Image(
                painter = rememberAsyncImagePainter(uri),
                contentDescription = "Selected Plant Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.UploadFile,
                    contentDescription = "Upload Icon",
                    modifier = Modifier.size(50.dp),
                    tint = PrimaryGreen
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Tap to upload a photo",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun ImageSourceButtons(onGalleryClick: () -> Unit, onCameraClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Primary Action: Camera
        Button(
            onClick = onCameraClick,
            modifier = Modifier
                .weight(1f)
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.White)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Camera")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Use Camera")
        }
        // Secondary Action: Gallery
        OutlinedButton(
            onClick = onGalleryClick,
            modifier = Modifier
                .weight(1f)
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, PrimaryGreen),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen)
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery")
            Spacer(modifier = Modifier.width(8.dp))
            Text("From Gallery")
        }
    }
}

@Composable
fun UploadArea(uiState: DiseaseScreenUiState, onUploadClick: () -> Unit) {
    if (uiState.isSuccess || uiState.error != null) {
        UploadResult(uiState)
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (uiState.isLoading || uiState.isAnalyzing) {
                Spacer(modifier = Modifier.height(16.dp))
                UploadResult(uiState)
                Spacer(modifier = Modifier.height(16.dp))
            }
            Button(
                onClick = onUploadClick,
                enabled = uiState.selectedImageUri != null && !uiState.isLoading && !uiState.isAnalyzing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.White)
            ) {
                Text("Analyze Plant/Soil Image", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun UploadResult(uiState: DiseaseScreenUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .defaultMinSize(minHeight = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            uiState.isLoading -> {
                Text("Uploading...", color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { uiState.uploadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = PrimaryGreen,
                    trackColor = Color.LightGray.copy(alpha = 0.4f)
                )
            }
            uiState.isAnalyzing -> {
                CircularProgressIndicator(color = PrimaryGreen)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Analyzing image...", color = Color.Gray)
            }
            uiState.isSuccess -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Article,
                                contentDescription = "Report Icon",
                                tint = SecondaryGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Analysis Report",
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
                            markdown = uiState.analysisResult ?: "No specific result found.",
                            fontSize = 14.sp,
                            color = Color.Black.copy(alpha = 0.87f),
                            lineHeight = 22.sp
                        )
                    }
                }
            }
            uiState.error != null -> {
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
