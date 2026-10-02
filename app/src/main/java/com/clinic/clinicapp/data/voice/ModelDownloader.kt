package com.clinic.clinicapp.data.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileOutputStream as FileOut
import java.net.HttpURLConnection
import java.net.URL
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

class ModelDownloader(private val context: Context) {

    private val TAG = "ModelDownloader"

    companion object {
        const val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-ctc-giga-am-v2-russian-2025-04-19.tar.bz2"

        const val MARKER_FILE = "model.int8.onnx"
        private const val ARCHIVE_NAME = "model.tar.bz2"
    }

    suspend fun ensureModel(
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val modelsRoot = File(context.filesDir, "models")
        if (!modelsRoot.exists()) modelsRoot.mkdirs()

        // Если модель уже распакована где-то внутри modelsRoot — используем её
        findModelDir(modelsRoot)?.let {
            Log.d(TAG, "Модель уже загружена: ${it.absolutePath}")
            return@withContext it
        }

        Log.d(TAG, "Модель отсутствует — начинаем загрузку")

        // 1. Скачиваем архив
        val archive = File(context.cacheDir, ARCHIVE_NAME)
        downloadFile(MODEL_URL, archive, onProgress)

        // 2. Распаковываем в modelsRoot (архив создаст свою подпапку)
        Log.d(TAG, "Распаковка архива…")
        extractTarBz2(archive, modelsRoot)

        // 3. Удаляем архив
        archive.delete()

        // 4. Ищем папку с моделью рекурсивно
        val modelDir = findModelDir(modelsRoot)
            ?: throw IllegalStateException(
                "После распаковки не найден $MARKER_FILE в $modelsRoot"
            )

        Log.d(TAG, "Модель готова: ${modelDir.absolutePath}")
        modelDir
    }

    /**
     * Рекурсивно ищет папку с model.int8.onnx.
     */
    private fun findModelDir(root: File): File? {
        if (!root.exists()) return null
        return root.walkTopDown()
            .firstOrNull { it.isFile && it.name == MARKER_FILE }
            ?.parentFile
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
        }

        try {
            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode при загрузке $urlString")
            }

            val total = connection.contentLengthLong
            Log.d(TAG, "Начало загрузки: $total байт")

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
                        output.flush()
                    }
                }
            }

            Log.d(TAG, "Загрузка завершена: ${destination.length()} байт")
        } finally {
            connection.disconnect()
        }
    }

    private fun extractTarBz2(archive: File, targetDir: File) {
        val tarInputStream = TarArchiveInputStream(
            BZip2CompressorInputStream(
                BufferedInputStream(FileInputStream(archive))
            )
        )

        tarInputStream.use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)

                // Защита от path traversal
                if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    throw SecurityException("Небезопасный путь в архиве: ${entry.name}")
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOut(outFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (tar.read(buffer).also { read = it } > 0) {
                            output.write(buffer, 0, read)
                        }
                    }
                }
                entry = tar.nextEntry
            }
        }
    }
}