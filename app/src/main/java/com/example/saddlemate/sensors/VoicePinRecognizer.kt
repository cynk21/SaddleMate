package com.example.saddlemate.sensors

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records one spoken phrase and returns the recognised German text.
 *
 * Uses the Android speech recogniser. Whether it works offline depends on the phone; without
 * network it may fail with an error, which is reported to the caller.
 */
@Singleton
class VoicePinRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recognizer: SpeechRecognizer? = null

    /**
     * Starts one recognition. [onResult] gets the best text, [onError] a readable message.
     * Must be called on the main thread.
     */
    fun listenOnce(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Spracherkennung ist auf diesem Gerät nicht verfügbar.")
            return
        }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isBlank()) onError("Nichts verstanden. Bitte noch einmal sprechen.") else onResult(text)
            }

            override fun onError(error: Int) {
                onError(messageFor(error))
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        r.startListening(intent)
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Keine Verbindung für die Spracherkennung. Bitte später erneut versuchen."
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "Nichts verstanden. Bitte noch einmal sprechen."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Mikrofon-Erlaubnis fehlt."
        else -> "Spracherkennung fehlgeschlagen (Code $error)."
    }
}
