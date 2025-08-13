package com.example.farmers.community

import android.Manifest
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.farmers.home.chatbot.LoadingDotsIndicator
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityChatScreen(
    viewModel: CommunityChatViewModel = hiltViewModel(),
    currentUserName: String,
    currentUserId: String
) {
    // Collect the entire UI state object
    val uiState by viewModel.uiState.collectAsState()
    val messageText by viewModel.messageText.collectAsState()
    val preferredLanguage = viewModel.preferredLanguage

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Community Chat") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            if (uiState.isLoading && uiState.messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            isMyMessage = message.senderId == currentUserId,
                            preferredLanguage = preferredLanguage,
                            isPlaying = message.id == uiState.currentlyPlayingMessageId,
                            onPlayToggle = { viewModel.onPlayToggle(message) }
                        )
                    }
                }
            }

            CommunityChatInputBar(
                messageText = messageText,
                onMessageChange = viewModel::onMessageTextChanged,
                onSendClick = {
                    viewModel.sendMessage(
                        senderId = currentUserId,
                        senderName = currentUserName
                    )
                },
                onSendAudioClick = { audioUri ->
                    viewModel.sendMessage(
                        senderId = currentUserId,
                        senderName = currentUserName,
                        audioUri = audioUri
                    )
                }
            )
        }
    }
}

@Composable
fun MessageBubble(
    // The bubble now accepts the UI-specific model
    message: UiCommunityMessage,
    isMyMessage: Boolean,
    preferredLanguage: String,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit
) {
    val horizontalArrangement = if (isMyMessage) Arrangement.End else Arrangement.Start
    val bubbleColor = if (isMyMessage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isMyMessage) 16.dp else 0.dp,
        bottomEnd = if (isMyMessage) 0.dp else 16.dp
    )
    val displayText = when (preferredLanguage) {
        "Tamil" -> message.text_ta ?: message.text
        "Hindi" -> message.text_hi ?: message.text
        else -> message.text
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (message.isLoading) {
                LoadingDotsIndicator()
            } else {
                Column {
                    if (!isMyMessage) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (isMyMessage) {
                        message.audioUrl?.let { url ->
                            AudioPlayerBubble(audioUrl = url, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (displayText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    if (displayText.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isMyMessage) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.StopCircle else Icons.Default.VolumeUp,
                                    contentDescription = "Play message audio",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { onPlayToggle() }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- The rest of the file (CommunityChatInputBar, AudioPlayerBubble, LoadingDotsIndicator) remains the same ---
// (Copy them from the previous response if needed)

// No changes needed below this line for the UI file
@Composable
fun CommunityChatInputBar(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onSendAudioClick: (Uri) -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isRecording = true
            val file = File(context.cacheDir, "community_audio_${System.currentTimeMillis()}.m4a")
            audioFile = file
            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
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

    fun stopRecording(send: Boolean) {
        mediaRecorder?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) { Log.e("MediaRecorder", "Stop/release failed", e) }
        }
        mediaRecorder = null
        isRecording = false
        if (send) {
            audioFile?.let { onSendAudioClick(Uri.fromFile(it)) }
        }
    }

    Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = if (isRecording) "Recording..." else messageText,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                shape = RoundedCornerShape(24.dp),
                readOnly = isRecording,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        onSendClick()
                    } else {
                        if (isRecording) {
                            stopRecording(send = true)
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = when {
                        messageText.isNotBlank() -> Icons.AutoMirrored.Filled.Send
                        isRecording -> Icons.Default.Stop
                        else -> Icons.Default.Mic
                    },
                    contentDescription = "Send or Record",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
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
            Log.e("AudioPlayer", "Failed to set data source for $audioUrl", e)
        }
        onDispose {
            mediaPlayer.release()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
        if (isPlaying) mediaPlayer.pause() else mediaPlayer.start()
        isPlaying = !isPlaying
    }) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = "Play/Pause",
            tint = tint
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Voice Message", style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}