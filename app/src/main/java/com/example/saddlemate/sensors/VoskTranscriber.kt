package com.example.saddlemate.sensors

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Turns a recorded WAV file into German text with Vosk, fully offline.
 *
 * The German model ships in assets/vosk-model-de (Apache 2.0, see LICENSES.md). It is unpacked
 * once on first use and kept in memory afterwards.
 */
@Singleton
class VoskTranscriber @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var model: Model? = null

    /** Returns the recognised text, or null if nothing was understood. Blocking: call off the main thread. */
    @Synchronized
    fun transcribe(wavPath: String): String? {
        val m = model ?: loadModel().also { model = it }
        val recognizer = Recognizer(m, SAMPLE_RATE)
        try {
            FileInputStream(wavPath).use { input ->
                input.skip(WAV_HEADER_BYTES.toLong())
                val buf = ByteArray(BUFFER_BYTES)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    recognizer.acceptWaveForm(buf, n)
                }
            }
            val json = JSONObject(recognizer.finalResult)
            return json.optString("text").trim().ifEmpty { null }
        } finally {
            recognizer.close()
        }
    }

    private fun loadModel(): Model {
        val target = File(context.filesDir, MODEL_DIR)
        if (!target.exists()) copyAssets("vosk-model-de", target)
        return Model(target.absolutePath)
    }

    private fun copyAssets(assetPath: String, target: File) {
        val entries = context.assets.list(assetPath).orEmpty()
        if (entries.isEmpty()) {
            context.assets.open(assetPath).use { input ->
                target.parentFile?.mkdirs()
                target.outputStream().use { input.copyTo(it) }
            }
            return
        }
        target.mkdirs()
        for (name in entries) copyAssets("$assetPath/$name", File(target, name))
    }

    private companion object {
        const val SAMPLE_RATE = 16_000f
        const val WAV_HEADER_BYTES = 44
        const val BUFFER_BYTES = 4_096
        const val MODEL_DIR = "vosk-model-de-unpacked"
    }
}
