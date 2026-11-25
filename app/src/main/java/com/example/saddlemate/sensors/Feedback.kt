package com.example.saddlemate.sensors

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import com.example.saddlemate.domain.AlertOutput
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speaks alerts and fires vibration, per the selected audio mode.
 *
 * The TTS engine is warmed up once at app start, so the first alert is not late.
 * Speech is queued (never flushed), so a new alert does not cut off the previous one.
 * Messages that arrive before the engine is ready are held and spoken once it is.
 */
@Singleton
class AlertFeedback @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<String>()

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /** Idempotent. Call early (app start) so the engine is ready before the first ride. */
    fun init() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.GERMAN
                tts?.setSpeechRate(1.0f)
                flushPending()
            }
        }
    }

    fun deliver(output: AlertOutput, spokenText: String) {
        if (output.speak) speak(spokenText)
        if (output.haptic) vibrate(longPattern = output.tone)
    }

    private fun speak(text: String) {
        if (!ready) {
            pending.addLast(text)
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "alert-${System.nanoTime()}")
    }

    private fun flushPending() {
        while (pending.isNotEmpty()) {
            tts?.speak(pending.removeFirst(), TextToSpeech.QUEUE_ADD, null, "alert-${System.nanoTime()}")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        pending.clear()
    }

    private fun vibrate(longPattern: Boolean) {
        val pattern = if (longPattern) longArrayOf(0, 300, 150, 300) else longArrayOf(0, 120)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
