package com.clinic.clinicapp.data.voice

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Обёртка над sherpa-onnx для распознавания речи.
 * Использует модель Whisper Tiny (английская версия).
 *
 * ВАЖНО: инициализация Whisper может занимать 10-15+ секунд,
 * поэтому её нужно делать заранее (при старте приложения),
 * а не в момент нажатия кнопки.
 */
class SherpaSttEngine(private val context: Context) {

    private val TAG = "SherpaStt"

    private var recognizer: OfflineRecognizer? = null
    private var isInitialized = false

    /**
     * Инициализировать движок заранее.
     * Вызывай этот метод при старте приложения.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            Log.d(TAG, "Начало инициализации Whisper Tiny...")
            val startTime = System.currentTimeMillis()

            val modelDir = "models/sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19"
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    nemo = OfflineNemoEncDecCtcModelConfig( // Используем NeMo CTC
                        model = "$modelDir/model.int8.onnx"
                    ),
                    tokens = "$modelDir/tokens.txt",
                    numThreads = 2,
                    provider = "cpu",
                    debug = false
                )
            )

            Log.d(TAG, "Создание OfflineRecognizer...")
            recognizer = OfflineRecognizer(
                assetManager = context.assets,
                config = config
            )

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Инициализация завершена за ${elapsed}мс")
            isInitialized = true
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Ошибка инициализации", t)
            false
        }
    }

    /**
     * Распознать фразу.
     * Предполагает, что initialize() уже был вызван.
     */
    suspend fun transcribe(samples: FloatArray): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "transcribe called, samples=${samples.size}")

        if (recognizer == null) {
            Log.e(TAG, "Recognizer не инициализирован!")
            return@withContext ""
        }

        try {
            val startTime = System.currentTimeMillis()
            val stream = recognizer!!.createStream()
            Log.d(TAG, "Stream создан")

            stream.acceptWaveform(samples, sampleRate = 16000)
            Log.d(TAG, "Аудио принято")

            recognizer!!.decode(stream)
            Log.d(TAG, "Decode завершён")

            val result = recognizer!!.getResult(stream).text.trim()
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Результат: «$result» за ${elapsed}мс")

            stream.release()
            result
        } catch (t: Throwable) {
            Log.e(TAG, "Ошибка распознавания", t)
            ""
        }
    }

    fun release() {
        Log.d(TAG, "release called")
        recognizer?.release()
        recognizer = null
        isInitialized = false
    }
}