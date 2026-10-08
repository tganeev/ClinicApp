package com.clinic.clinicapp.data.nlu

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Скачивает ONNX-модель NLU (clinic_lm) при первом запуске.
 * Файл весит ~27 МБ, хранится в filesDir/nlu/model.onnx
 */
class NluModelDownloader(private val context: Context) {

    private val TAG = "NluDownloader"

    companion object {
        // TODO: заменить на реальный URL, где лежит model.onnx
        const val MODEL_URL = "https://disk.yandex.ru/d/udCCcPOdUcwr-g"
        const val MODEL_FILE = "model.onnx"
    }

    suspend fun ensureModel(
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val nluDir = File(context.filesDir, "nlu")
        if (!nluDir.exists()) nluDir.mkdirs()

        val modelFile = File(nluDir, MODEL_FILE)
        if (modelFile.exists() && modelFile.length() > 0) {
            Log.d(TAG, "Модель уже скачана: ${modelFile.length()} байт")
            return@withContext modelFile
        }

        Log.d(TAG, "Скачивание NLU-модели...")
        val tmpFile = File(nluDir, "$MODEL_FILE.tmp")
        downloadFile(MODEL_URL, tmpFile, onProgress)
        tmpFile.renameTo(modelFile)
        Log.d(TAG, "Модель готова: ${modelFile.length()} байт")
        modelFile
    }

    private fun downloadFile(
        urlString: String,
        destination: File,
        onProgress: (Long, Long) -> Unit
    ) {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            connection.connect()
            val code = connection.responseCode
            Log.d(TAG, "HTTP $code для $urlString")

            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code для $urlString")
            }

            val contentType = connection.contentType ?: ""
            val total = connection.contentLengthLong
            Log.d(TAG, "Content-Type: $contentType, Content-Length: $total")

            // Проверяем, что это не HTML-страница
            if (contentType.contains("text/html", ignoreCase = true)) {
                throw IllegalStateException(
                    "Сервер вернул HTML вместо файла модели. " +
                            "Content-Type: $contentType. Проверь ссылку."
                )
            }

            connection.inputStream.use { input ->
                BufferedInputStream(input).use { buffered ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var read: Int
                        while (buffered.read(buffer).also { read = it } > 0) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }
            }

            // Финальная проверка размера
            val actualSize = destination.length()
            Log.d(TAG, "Скачано: $actualSize байт")
            if (actualSize < 1_000_000) {
                throw IllegalStateException(
                    "Файл модели подозрительно маленький: $actualSize байт. " +
                            "Ожидалось ~27 МБ. Возможно, скачалась HTML-страница."
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}