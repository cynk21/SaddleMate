package com.example.saddlemate.sensors

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records one voice pin as a WAV file (16 kHz, 16 bit, mono), the format Vosk reads.
 *
 * Works without network. The file is written while recording, and the WAV header is fixed at
 * the end with the real length.
 */
class VoiceRecorder(private val outputDir: File) {

    private var record: AudioRecord? = null
    private var file: File? = null
    private var thread: Thread? = null

    /** Starts recording into a new file. Returns the file path. Needs RECORD_AUDIO. */
    @SuppressLint("MissingPermission")
    fun start(): String {
        val f = File(outputDir, "pin-${System.currentTimeMillis()}.wav")
        file = f
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2,
        )
        record = r
        writeHeader(f, dataLength = 0)
        r.startRecording()
        thread = Thread {
            val buf = ByteArray(minBuf)
            RandomAccessFile(f, "rw").use { out ->
                out.seek(HEADER_SIZE.toLong())
                while (!Thread.currentThread().isInterrupted && r.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val n = r.read(buf, 0, buf.size)
                    if (n > 0) out.write(buf, 0, n)
                }
            }
        }.also { it.start() }
        return f.absolutePath
    }

    /** Stops recording and fixes the WAV header with the real length. Returns the file path. */
    fun stop(): String? {
        val r = record ?: return null
        val f = file ?: return null
        r.stop()
        thread?.interrupt()
        thread?.join(1_000)
        r.release()
        record = null
        thread = null
        val dataLength = (f.length() - HEADER_SIZE).coerceAtLeast(0L)
        writeHeader(f, dataLength)
        return f.absolutePath
    }

    private fun writeHeader(f: File, dataLength: Long) {
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt((36 + dataLength).toInt())
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1) // PCM
            putShort(1) // mono
            putInt(SAMPLE_RATE)
            putInt(SAMPLE_RATE * 2) // byte rate
            putShort(2) // block align
            putShort(16) // bits per sample
            put("data".toByteArray())
            putInt(dataLength.toInt())
        }.array()
        RandomAccessFile(f, "rw").use { out ->
            out.seek(0)
            out.write(header)
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val HEADER_SIZE = 44
    }
}
