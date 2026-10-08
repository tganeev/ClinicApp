package com.clinic.clinicapp.data.nlu

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import java.io.File
import java.nio.LongBuffer

/**
 * Обёртка над ONNX Runtime для TinyGPT (clinic_lm).
 * Вход:  int64[1, 128]
 * Выход: float32[1, 128, V]
 */
class NluModel(modelFile: File) {

    private val TAG = "NluModel"
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    // Имя входа в ONNX-модели. Если в твоей модели другое — замени.
    private val inputName = "input_ids"

    init {
        val opts = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        session = env.createSession(modelFile.absolutePath, opts)
        Log.d(TAG, "Модель загружена. Входы: ${session.inputNames}, выходы: ${session.outputNames}")
    }

    /**
     * Прямой проход. Возвращает логиты последней позиции —
     * распределение вероятностей для следующего токена.
     */
    fun forward(inputIds: LongArray): FloatArray {
        require(inputIds.size == 128) { "Ожидается 128 токенов, получено ${inputIds.size}" }

        val shape = longArrayOf(1, 128)
        val buffer = LongBuffer.wrap(inputIds)
        val inputTensor = OnnxTensor.createTensor(env, buffer, shape)

        try {
            val inputs = mapOf(inputName to inputTensor)
            val result = session.run(inputs)
            try {
                val outputTensor = result[0] as OnnxTensor
                val shapeArr = outputTensor.info.shape
                val vocabSize = shapeArr[2].toInt()
                val outputBuffer = outputTensor.floatBuffer

                // Берём логиты последней позиции (индекс 127)
                val lastPos = 127
                val logits = FloatArray(vocabSize)
                val baseIdx = lastPos * vocabSize
                outputBuffer.position(baseIdx)
                outputBuffer.get(logits, 0, vocabSize)
                return logits
            } finally {
                result.close()
            }
        } finally {
            inputTensor.close()
        }
    }

    fun close() {
        session.close()
    }
}