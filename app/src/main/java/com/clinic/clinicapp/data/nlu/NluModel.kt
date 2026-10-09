package com.clinic.clinicapp.data.nlu

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.io.File

/**
 * Обёртка над TFLite-моделью clinic_lm.
 *
 * Контракт:
 *   вход  : int64[1, 128]
 *   выход : float32[1, 128, V]
 *
 * Модель использует TensorFlow Lite (LiteRT) — независимый рантайм,
 * не конфликтующий с ONNX Runtime, который тянет sherpa-onnx.
 */
class NluModel(private val context: Context) {

    private val TAG = "NluModel"

    private var interpreter: Interpreter? = null

    companion object {
        private const val MODEL_ASSET = "nlu/model_int8.tflite"
        private const val CONTEXT_SIZE = 128
    }

    init {
        try {
            val model = loadModelFile(MODEL_ASSET)
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(model, options)
            Log.d(TAG, "TFLite-модель загружена")

            // Логируем формы входа/выхода для отладки
            val inputShape = interpreter!!.getInputTensor(0).shape()
            val outputShape = interpreter!!.getOutputTensor(0).shape()
            Log.d(TAG, "Вход: ${inputShape.toList()}, Выход: ${outputShape.toList()}")
        } catch (t: Throwable) {
            Log.e(TAG, "Ошибка загрузки TFLite-модели", t)
            throw t
        }
    }

    /**
     * Загружает модель из assets во временный файл.
     * TFLite Interpreter принимает ByteBuffer или File, но не поток из assets напрямую.
     */
    private fun loadModelFile(assetPath: String): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(assetPath)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Прямой проход: возвращает логиты последней позиции.
     *
     * @param inputIds LongArray размера 128 (хвост — pad_id)
     * @return FloatArray размера V — распределение вероятностей следующего токена
     */
    fun forward(inputIds: LongArray): FloatArray {
        require(inputIds.size == CONTEXT_SIZE) {
            "Ожидается $CONTEXT_SIZE токенов, получено ${inputIds.size}"
        }

        val interp = interpreter ?: throw IllegalStateException("Модель не инициализирована")

        // Вход: [1, 128] int64
        val input = Array(1) { inputIds }

        // Выход: [1, 128, V] float32
        val outputShape = interp.getOutputTensor(0).shape()  // [1, 128, V]
        val vocabSize = outputShape[2]
        val output = Array(1) { Array(CONTEXT_SIZE) { FloatArray(vocabSize) } }

        interp.run(input, output)

        // Берём логиты последней позиции
        return output[0][CONTEXT_SIZE - 1]
    }

    /**
     * Прямой проход по всей последовательности: возвращает логиты для каждой позиции.
     * Нужно, если мы хотим оценивать не только последний токен.
     */
    fun forwardFull(inputIds: LongArray): Array<FloatArray> {
        require(inputIds.size == CONTEXT_SIZE)

        val interp = interpreter ?: throw IllegalStateException("Модель не инициализирована")

        val input = Array(1) { inputIds }
        val outputShape = interp.getOutputTensor(0).shape()
        val vocabSize = outputShape[2]
        val output = Array(1) { Array(CONTEXT_SIZE) { FloatArray(vocabSize) } }

        interp.run(input, output)

        return output[0]
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}