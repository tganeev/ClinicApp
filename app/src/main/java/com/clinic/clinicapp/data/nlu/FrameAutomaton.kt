package com.clinic.clinicapp.data.nlu

import android.util.Log

/**
 * Упрощённое жадное декодирование фрейма.
 * TODO: заменить на полный FrameAutomaton из train/constrained.py,
 *       когда будет доступен код автомата.
 */
class FrameAutomaton(
    private val tokenizer: BpeTokenizer,
    private val eosId: Int,
    private val padId: Int
) {

    private val TAG = "FrameAutomaton"

    /**
     * @param forward функция инференса: получает 128 ID, возвращает логиты
     * @param initialIds исходный закодированный текст
     * @param maxSteps максимум новых токенов (фрейм редко длиннее 20)
     */
    fun generate(
        forward: (LongArray) -> FloatArray,
        initialIds: LongArray,
        maxSteps: Int = 32
    ): Pair<String, LongArray> {
        val current = initialIds.copyOf()
        val generated = ArrayList<Long>()

        for (step in 0 until maxSteps) {
            // Ищем последнюю позицию с реальным токеном (не PAD)
            var lastRealPos = -1
            for (i in current.indices.reversed()) {
                if (current[i].toInt() != padId) {
                    lastRealPos = i
                    break
                }
            }

            if (lastRealPos < 0 || lastRealPos >= current.size - 1) break

            // Логиты для последней позиции
            val logits = forward(current)

            // argmax
            var bestId = 0
            var bestScore = logits[0]
            for (i in logits.indices) {
                if (logits[i] > bestScore) {
                    bestScore = logits[i]
                    bestId = i
                }
            }

            // Записываем следующий токен
            current[lastRealPos + 1] = bestId.toLong()
            generated += bestId.toLong()

            // Стоп по EOS
            if (bestId == eosId) break
        }

        // Собираем строку фрейма
        val frame = generated.joinToString(" ") { id ->
            tokenizer.tokenOf(id) ?: "<UNK:$id>"
        }
        Log.d(TAG, "Frame: $frame")
        return frame to generated.toLongArray()
    }
}