package com.clinic.clinicapp.data.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Рекордер PCM-аудио.
 * Требования Whisper Tiny: 16 кГц, моно, PCM 16-bit.
 *
 * Логика:
 *  - start() запускает фоновый поток, который непрерывно читает AudioRecord
 *    и складывает сэмплы в общий буфер samples;
 *  - stop() выставляет isRecording=false, вызывает record.stop()/release(),
 *    после чего фоновый поток сам завершается;
 *  - результат возвращается как FloatArray в диапазоне [-1, 1].
 */
class AudioRecorder {

    private val TAG = "AudioRecorder"

    private var audioRecord: AudioRecord? = null

    @Volatile
    private var isRecording = false

    private val samples = ArrayList<Short>(16000 * 10)  // запас на 10 секунд

    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    @SuppressLint("MissingPermission")
    suspend fun start() = withContext(Dispatchers.IO) {
        Log.d(TAG, "start() вызван")

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val bufferSize = minBuffer.coerceAtLeast(SAMPLE_RATE * 2)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord не инициализирован (state=${record.state})")
            record.release()
            return@withContext
        }

        audioRecord = record
        record.startRecording()
        isRecording = true
        synchronized(samples) { samples.clear() }

        Log.d(TAG, "Запись стартовала")

        Thread {
            val chunk = ShortArray(4096)
            Log.d(TAG, "Фоновый поток чтения запущен")
            while (isRecording) {
                val read = record.read(chunk, 0, chunk.size)
                if (read > 0) {
                    synchronized(samples) {
                        for (i in 0 until read) samples.add(chunk[i])
                    }
                } else if (read < 0) {
                    Log.e(TAG, "Ошибка чтения AudioRecord: $read")
                    break
                }
            }
            Log.d(TAG, "Фоновый поток чтения завершён")
        }.start()
    }

    suspend fun stop(): FloatArray = withContext(Dispatchers.IO) {
        Log.d(TAG, "stop() вызван")

        val record = audioRecord
        if (record == null) {
            Log.e(TAG, "stop(): audioRecord == null")
            return@withContext FloatArray(0)
        }

        isRecording = false

        try {
            record.stop()
            Log.d(TAG, "record.stop() выполнен")
        } catch (t: Throwable) {
            Log.e(TAG, "Ошибка при record.stop()", t)
        }

        try {
            record.release()
            Log.d(TAG, "record.release() выполнен")
        } catch (t: Throwable) {
            Log.e(TAG, "Ошибка при record.release()", t)
        }

        audioRecord = null

        // Даём фоновому потоку дочитать последние сэмплы
        Thread.sleep(50)

        val snapshot: List<Short>
        synchronized(samples) {
            snapshot = samples.toList()
            samples.clear()
        }

        Log.d(TAG, "Записано сэмплов: ${snapshot.size}")

        FloatArray(snapshot.size) { snapshot[it] / 32768f }
    }
}