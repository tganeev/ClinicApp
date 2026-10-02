package com.clinic.clinicapp.data.voice

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Обёртка над sherpa-onnx для распознавания русской речи (GigaAM NeMo CTC).
 *
 * Модель НЕ входит в APK — она скачивается при первом запуске в filesDir/models/.
 * Это уменьшает размер APK с ~340 МБ до ~15 МБ.
 */
class SherpaSttEngine(private val context: Context) {

    private val TAG = "SherpaStt"

    private var recognizer: OfflineRecognizer? = null
    private var isInitialized = false
    private var modelDir: File? = null

    /**
     * Инициализация: скачивает модель (если её нет) и загружает sherpa-onnx.
     *
     * @param onDownloadProgress callback для UI: (downloaded, total).
     *        total = -1, если сервер не прислал Content-Length.
     */
    suspend fun initialize(
        onDownloadProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            Log.d(TAG, "Проверка наличия модели…")
            val downloader = ModelDownloader(context)
            val modelDirLocal = downloader.ensureModel(onDownloadProgress)
            modelDir = modelDirLocal

            Log.d(TAG, "Инициализация sherpa-onnx с моделью из ${modelDirLocal.absolutePath}")
            val startTime = System.currentTimeMillis()

            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    nemo = OfflineNemoEncDecCtcModelConfig(
                        model = File(modelDirLocal, "model.int8.onnx").absolutePath
                    ),
                    tokens = File(modelDirLocal, "tokens.txt").absolutePath,
                    numThreads = 2,
                    provider = "cpu",
                    debug = false
                )
            )

            // ВАЖНО: здесь НЕ передаём assetManager — грузим из файловой системы.
            recognizer = OfflineRecognizer(config = config)

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
     * Распознать фразу из массива сэмплов (16 кГц, моно, float в диапазоне [-1, 1]).
     */
    suspend fun transcribe(samples: FloatArray): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "transcribe called, samples=${samples.size}")

        val rec = recognizer
        if (rec == null) {
            Log.e(TAG, "Recognizer не инициализирован!")
            return@withContext ""
        }

        try {
            val startTime = System.currentTimeMillis()
            val stream = rec.createStream()
            stream.acceptWaveform(samples, sampleRate = 16000)
            rec.decode(stream)
            val result = rec.getResult(stream).text.trim()
            stream.release()

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Результат: «$result» за ${elapsed}мс")
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