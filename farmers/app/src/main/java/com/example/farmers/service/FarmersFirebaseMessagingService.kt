package com.example.farmers.service

import com.example.farmers.community.CommunityChatViewModel
import com.example.farmers.data.UserManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FarmersFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var userManager: UserManager

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        println("FCM MESSAGE RECEIVED: ${remoteMessage.data}")

        val currentUserId = userManager.getUserId()

        remoteMessage.data.let { data ->
            val senderId = data["senderId"]

            if (senderId != null && senderId != currentUserId) {
                val senderName = data["senderName"]
                val text = data["text"]
                val textTa = data["text_ta"]
                val textHi = data["text_hi"]
                val timestamp = data["timestamp"]

                if (senderName != null && text != null && timestamp != null) {
                    println("FCM MESSAGE PARSED: senderId=$senderId")
                    CommunityChatViewModel.onNewMessageReceived(
                        CommunityMessage(
                            senderId = senderId,
                            senderName = senderName,
                            text = text,
                            text_ta = textTa,
                            text_hi = textHi,
                            audio_url = null,
                            timestamp = timestamp
                        )
                    )
                } else {
                    println("FCM MESSAGE FAILED TO PARSE: A required field was null.")
                }
            } else {
                println("FCM MESSAGE IGNORED: Message is from the current user or senderId is null.")
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        println("New FCM Token: $token")
    }
}