package com.example.farmers.home.chatbot

import android.Manifest
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
// Import for the ImageRequest Builder
import coil.request.ImageRequest
import com.example.chatbot.ChatViewModel
import com.example.chatbot.Message
import com.example.chatbot.Participant
import com.example.farmers.ui.theme.PrimaryGreen
import com.example.farmers.ui.theme.SecondaryGreen
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun ChatScreen(
    prefilledPrompt: String? = null,
    chatViewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by chatViewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(prefilledPrompt) {
        if (!prefilledPrompt.isNullOrBlank()) {
            chatViewModel.sendMessage(text = prefilledPrompt)
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = { ChatHeader() }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(scaffoldPadding)
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = uiState.messages,
                    key = { it.id }
                ) { message ->
                    MessageBubble(
                        message = message,
                        isPlaying = uiState.currentlyPlayingMessageId == message.id,
                        onPlayToggle = { chatViewModel.onPlayToggle(message) }
                    )
                }
            }
            ChatInputBar(
                onSendMessage = { text, imageUri ->
                    chatViewModel.sendMessage(text = text, imageUri = imageUri)
                },
                onSendVoiceMessage = { audioUri, imageUri ->
                    chatViewModel.sendMessage(audioUri = audioUri, imageUri = imageUri)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHeader() {
    TopAppBar(
        title = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AI Assistant",
                    color = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = PrimaryGreen,
            titleContentColor = Color.White
        )
    )
}

@Composable
fun MessageBubble(
    message: Message,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isUserMessage = message.participant == Participant.USER
    val bubbleColor = if (isUserMessage) PrimaryGreen else SecondaryGreen
    val textColor = Color.White
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUserMessage) 16.dp else 0.dp,
        bottomEnd = if (isUserMessage) 0.dp else 16.dp
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUserMessage) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = screenWidth * 0.8f)
                .background(bubbleColor, shape = bubbleShape)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (message.isLoading) {
                if (message.localImageUri != null) {
                    Column {
                        AsyncImage(
                            model = message.localImageUri,
                            contentDescription = "Uploading image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(8.dp))
                        LoadingDotsIndicator()
                    }
                } else {
                    LoadingDotsIndicator()
                }
            } else {
                Column {
                    message.imageUrl?.let { remoteImageUrl ->
                        // **FIX**: Use ImageRequest to set the local image as a placeholder, preventing flicker.
                        val imageRequest = ImageRequest.Builder(LocalContext.current)
                            .data(remoteImageUrl) // Final URL to load
                            .placeholderMemoryCacheKey(message.localImageUri.toString()) // Use local URI as placeholder
                            .crossfade(true) // Smooth transition
                            .build()

                        AsyncImage(
                            model = imageRequest,
                            contentDescription = "User uploaded image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        if (message.text != null || message.audioUrl != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    message.audioUrl?.let {
                        AudioPlayerBubble(
                            audioUrl = it,
                            tint = textColor
                        )
                        if (message.text != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    message.text?.let { text ->
                        Row(verticalAlignment = Alignment.Top) {
                            if (message.participant == Participant.BOT) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.StopCircle else Icons.Default.VolumeUp,
                                    contentDescription = "Play message audio",
                                    tint = textColor.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { onPlayToggle() }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            MarkdownText(
                                markdown = text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    onSendMessage: (String, Uri?) -> Unit,
    onSendVoiceMessage: (Uri, Uri?) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                // When an image is picked, also add it to Coil's memory cache
                val imageLoader = coil.Coil.imageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(it)
                    .memoryCacheKey(it.toString())
                    .build()
                imageLoader.enqueue(request)
                imageUri = it
            }
        }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                isRecording = true
                val file = File(context.cacheDir, "voice_message_${System.currentTimeMillis()}.m4a")
                audioFile = file
                mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(file.absolutePath)
                    try {
                        prepare()
                        start()
                    } catch (e: Exception) {
                        Log.e("MediaRecorder", "Prepare failed", e)
                    }
                }
            }
        }
    )

    fun stopRecording(send: Boolean) {
        mediaRecorder?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                Log.e("MediaRecorder", "Stop/release failed", e)
            }
        }
        mediaRecorder = null
        isRecording = false
        if (send) {
            audioFile?.let {
                onSendVoiceMessage(Uri.fromFile(it), imageUri)
                imageUri = null
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column {
            imageUri?.let {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                    AsyncImage(
                        model = it,
                        contentDescription = "Selected image preview",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-8).dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { imageUri = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove image",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) {
                    Icon(Icons.Default.Image, contentDescription = "Add Image", tint = PrimaryGreen)
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Type a message...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedIndicatorColor = PrimaryGreen,
                        unfocusedIndicatorColor = SecondaryGreen,
                        cursorColor = PrimaryGreen,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (text.isNotBlank() || imageUri != null) {
                            onSendMessage(text, imageUri)
                            text = ""
                            imageUri = null
                        }
                    })
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            onSendMessage(text, imageUri)
                            text = ""
                            imageUri = null
                        } else {
                            if (isRecording) {
                                stopRecording(send = true)
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(PrimaryGreen)
                ) {
                    Icon(
                        imageVector = when {
                            text.isNotBlank() -> Icons.AutoMirrored.Filled.Send
                            isRecording -> Icons.Default.Stop
                            else -> Icons.Default.Mic
                        },
                        contentDescription = "Send or Record",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun AudioPlayerBubble(audioUrl: String, tint: Color) {
    var isPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }
    DisposableEffect(audioUrl) {
        try {
            mediaPlayer.setDataSource(audioUrl)
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnCompletionListener { isPlaying = false }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to set data source", e)
        }
        onDispose {
            mediaPlayer.release()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = "Play/Pause",
            tint = tint,
            modifier = Modifier.clickable {
                if (isPlaying) {
                    mediaPlayer.pause()
                } else {
                    mediaPlayer.start()
                }
                isPlaying = !isPlaying
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Voice Message", style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
fun LoadingDotsIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        (1..3).forEach { index ->
            val animatable = remember { Animatable(0f) }
            LaunchedEffect(animatable) {
                delay(index * 150L)
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(y = -(animatable.value * 5).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.8f))
            )
        }
    }
}