package com.example.meritrankerstudent.util.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enterprise AudioStreamRecorder for low-latency Google Cloud Chirp 3 Streaming.
 * 
 * Captures 16-bit Linear PCM at 16 kHz Mono in bounded memory buffers.
 * Enforces zero raw audio persistence: chunks are emitted to memory stream and discarded.
 */
class AudioStreamRecorder(
    private val sampleRate: Int = SAMPLE_RATE_HZ,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    companion object {
        private const val TAG = "AudioStreamRecorder"
        const val SAMPLE_RATE_HZ = 16000
        const val CHUNK_DURATION_MS = 100 // 100ms audio chunks (~3200 bytes per chunk)
        const val BUFFER_CAPACITY = 64 // Bounded backpressure buffer
    }

    private val isRecording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val _audioChunks = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val audioChunks: Flow<ByteArray> = _audioChunks.asSharedFlow()

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope): Boolean {
        if (isRecording.getAndSet(true)) {
            Log.w(TAG, "Audio recording already active")
            return true
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid AudioRecord buffer configuration")
                isRecording.set(false)
                return false
            }

            val chunkSize = (sampleRate * (16 / 8) * (CHUNK_DURATION_MS / 1000.0)).toInt() // 3200 bytes
            val recordBufferSize = maxOf(minBufferSize * 2, chunkSize * 4)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                recordBufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                record.release()
                isRecording.set(false)
                return false
            }

            audioRecord = record
            record.startRecording()
            Log.d(TAG, "AudioRecord started: 16kHz PCM 16-bit Mono, chunk=$chunkSize bytes")

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(chunkSize)
                while (isActive && isRecording.get()) {
                    val readBytes = record.read(buffer, 0, buffer.size)
                    if (readBytes > 0) {
                        val chunkCopy = buffer.copyOf(readBytes)
                        _audioChunks.emit(chunkCopy)
                    } else if (readBytes < 0) {
                        Log.e(TAG, "AudioRecord read error: $readBytes")
                        break
                    }
                }
            }
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "Exception starting AudioRecord", e)
            stop()
            return false
        }
    }

    fun stop() {
        if (!isRecording.getAndSet(false)) return
        Log.d(TAG, "Stopping AudioRecord")
        try {
            recordingJob?.cancel()
            recordingJob = null
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
            audioRecord = null
        } catch (e: Throwable) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
    }

    fun isCapturing(): Boolean = isRecording.get()
}
