package com.example.farmers.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject

@ViewModelScoped
class TextToSpeechService @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    val callback = utteranceCompletedCallbacks.remove(utteranceId)
                    callback?.invoke()
                }
                override fun onError(utteranceId: String?) {
                    utteranceCompletedCallbacks.remove(utteranceId)
                }
            })
            Log.d("TTS", "TTS Engine Initialized.")
        } else {
            Log.e("TTS", "TTS Initialization Failed!")
        }
    }

    // A map to hold callbacks for when speech finishes
    private val utteranceCompletedCallbacks = mutableMapOf<String, () -> Unit>()

    fun speak(text: String, onDone: () -> Unit) {
        if (!isInitialized) return

        val utteranceId = text.hashCode().toString()
        utteranceCompletedCallbacks[utteranceId] = onDone
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        utteranceCompletedCallbacks.clear()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        isInitialized = false
    }
}