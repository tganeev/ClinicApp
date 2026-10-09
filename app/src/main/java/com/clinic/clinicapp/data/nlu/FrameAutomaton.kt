package com.clinic.clinicapp.data.nlu

import android.util.Log

/**
 * Constrained decoding: генерация фрейма токенов с маской допустимых токенов.
 * Гарантирует синтаксически валидный фрейм.
 *
 * Полная версия автомата — см. train/constrained.py в репозитории модели.
 * Здесь портирован базовый вариант для BOOK/CANCEL/RESCHEDULE.
 */
class FrameAutomaton(
    private val tokenizer: BpeTokenizer,
    private val eosId: Int,
    private val padId: Int,
    private val assistantId: Int,
    private val userId: Int,
    private val bosId: Int
) {

    private val TAG = "FrameAutomaton"

    companion object {
        private const val MAX_STEPS = 24
        private const val CONTEXT_SIZE = 128

        // Специальные токены (id берутся из tokenizer.json)
        private const val TOKEN_INTENT_BOOK = "<BOOK>"
        private const val TOKEN_INTENT_CANCEL = "<CANCEL>"
        private const val TOKEN_INTENT_RESCHEDULE = "<RESCHEDULE>"
        private const val TOKEN_INTENT_UNSUPPORTED = "<UNSUPPORTED>"
        private const val TOKEN_TIME_OPEN = "<TIME>"
        private const val TOKEN_TIME_CLOSE = "</TIME>"
        private const val TOKEN_END_FRAME = "<END_FRAME>"

        // Список специальностей
        private val SPECIALTY_TOKENS = listOf(
            "<GP>", "<DENTIST>", "<DERMATOLOGIST>", "<GYNECOLOGIST>",
            "<OPHTHALMOLOGIST>", "<NEUROLOGIST>", "<PSYCHOLOGIST>",
            "<SURGEON>", "<ENT>", "<UNKNOWN_SPECIALTY>"
        )

        // Список токенов времени (факторные категории)
        private val TIME_TOKENS = listOf(
            "<T_UNKNOWN>", "<T_AMBIG>",
            "T_TODAY", "T_TOMORROW", "T_DAY_AFTER_TOMORROW",
            "T_IN_2_DAYS", "T_IN_3_DAYS", "T_IN_WEEK", "T_IN_2_WEEKS",
            "T_NEXT_WEEK", "T_NEXT_MONTH", "T_THIS_WEEK", "T_THIS_MONTH",
            "T_MON", "T_TUE", "T_WED", "T_THU", "T_FRI", "T_SAT", "T_SUN",
            "T_MORNING", "T_BEFORE_NOON", "T_MIDDAY", "T_AFTERNOON",
            "T_EVENING", "T_AFTER_WORK", "T_AFTER_18",
            "T_H08_00", "T_H08_30", "T_H09_00", "T_H09_30", "T_H10_00",
            "T_H11_00", "T_H13_00", "T_H14_00", "T_H15_00", "T_H16_00",
            "T_H17_00", "T_H17_30", "T_H18_00", "T_H18_30", "T_H19_00",
            "T_H20_00", "T_H21_00", "T_H22_00"
        )
    }

    /**
     * Генерирует фрейм для указанного текста.
     */
    fun generate(
        forward: (LongArray) -> FloatArray,
        text: String,
        tokenizer: BpeTokenizer
    ): Pair<String, LongArray> {
        // 1. Токенизируем текст
        val textIds = tokenizer.encodeText(text)  // без <BOS>/<EOS>/<PAD>

        // 2. Собираем промпт: [BOS, USER] + textIds + [ASSISTANT]
        val seq = ArrayList<Long>(CONTEXT_SIZE)
        seq += bosId.toLong()
        seq += userId.toLong()
        seq.addAll(textIds.map { it.toLong() })
        seq += assistantId.toLong()

        Log.d(TAG, "Промпт: ${seq.size} токенов")

        // 3. Жадная генерация с маской автомата
        val generated = ArrayList<Long>()
        var state = State.INTENT

        for (step in 0 until MAX_STEPS) {
            if (seq.size >= CONTEXT_SIZE) break

            // Собираем входной массив 128 токенов (хвост — padId)
            val input = LongArray(CONTEXT_SIZE) { padId.toLong() }
            for (i in seq.indices) input[i] = seq[i]

            // Получаем логиты последней позиции
            val logits = forward(input)

            // Маскируем запрещённые токены
            val allowed = allowedTokensFor(state)
            val next = argmaxWithMask(logits, allowed)

            if (next == eosId) {
                Log.d(TAG, "EOS на шаге $step")
                break
            }

            seq += next.toLong()
            generated += next.toLong()

            // Переходим в следующее состояние
            state = nextState(state, next)
        }

        // 4. Формируем строку фрейма
        val frame = generated.joinToString(" ") { id ->
            tokenizer.tokenOf(id) ?: "<UNK:$id>"
        }
        Log.d(TAG, "Frame: $frame")

        return frame to generated.toLongArray()
    }

    /**
     * Состояния автомата.
     */
    private enum class State {
        INTENT,
        SPECIALTY,
        TIME,
        SYMPTOMS,
        CLARIFICATION,
        END_FRAME,
        DONE
    }

    /**
     * Возвращает список ID токенов, разрешённых в текущем состоянии.
     */
    private fun allowedTokensFor(state: State): Set<Int> {
        val ids = mutableSetOf<Int>()

        fun add(token: String) {
            tokenizer.idOf(token)?.let { ids += it }
        }

        when (state) {
            State.INTENT -> {
                add(TOKEN_INTENT_BOOK)
                add(TOKEN_INTENT_CANCEL)
                add(TOKEN_INTENT_RESCHEDULE)
                add(TOKEN_INTENT_UNSUPPORTED)
            }

            State.SPECIALTY -> {
                SPECIALTY_TOKENS.forEach { add(it) }
            }

            State.TIME -> {
                // Открытие/закрытие слота времени
                add(TOKEN_TIME_OPEN)
                add(TOKEN_TIME_CLOSE)
                // Факторы времени
                TIME_TOKENS.forEach { add(it) }
            }

            State.SYMPTOMS -> {
                // Симптомы + служебные
                add("<SYMPTOM_TOOTHACHE>")
                add("<SYMPTOM_SORE_THROAT>")
                add("<SYMPTOM_FEVER>")
                add("<SYMPTOM_RASH>")
                add("<SYMPTOM_BACK_PAIN>")
                add("<SYMPTOM_HEADACHE>")
                add("<SYMPTOM_EYE_PAIN>")
                add("<SYMPTOM_ANXIETY>")
                add("<SYMPTOM_COUGH>")
                add("<SYMPTOM_STOMACH_PAIN>")
                add("<SYMPTOM_DIZZINESS>")
                add("<SYMPTOM_INSOMNIA>")
                add("<SYMPTOM_ALLERGY>")
                add("<NEEDS_CLARIFICATION>")
                add(TOKEN_END_FRAME)
            }

            State.CLARIFICATION -> {
                add(TOKEN_END_FRAME)
            }

            State.END_FRAME -> {
                add("<EOS>")
            }

            State.DONE -> {
                // Ничего не разрешаем — генерация должна остановиться
                add("<EOS>")
            }
        }

        return ids
    }

    /**
     * Argmax логитов с маской.
     */
    private fun argmaxWithMask(logits: FloatArray, allowed: Set<Int>): Int {
        var bestId = -1
        var bestScore = Float.NEGATIVE_INFINITY
        for (id in allowed) {
            if (id < logits.size && logits[id] > bestScore) {
                bestScore = logits[id]
                bestId = id
            }
        }
        // Если ничего не разрешено — возвращаем EOS, чтобы не зависнуть
        return if (bestId >= 0) bestId else eosId
    }

    /**
     * Переход автомата.
     */
    private fun nextState(state: State, tokenId: Int): State {
        val tokenStr = tokenizer.tokenOf(tokenId.toLong()) ?: return state

        return when (state) {
            State.INTENT -> when {
                tokenStr == TOKEN_INTENT_UNSUPPORTED -> State.END_FRAME
                tokenStr == TOKEN_INTENT_CANCEL -> State.SYMPTOMS
                tokenStr == TOKEN_INTENT_BOOK ||
                        tokenStr == TOKEN_INTENT_RESCHEDULE -> State.SPECIALTY
                else -> State.END_FRAME
            }

            State.SPECIALTY -> State.TIME

            State.TIME -> when (tokenStr) {
                TOKEN_TIME_OPEN -> State.TIME  // внутри слота
                TOKEN_TIME_CLOSE -> State.SYMPTOMS  // закрыли слот
                else -> State.TIME  // остаёмся в TIME для следующего фактора
            }

            State.SYMPTOMS -> when (tokenStr) {
                TOKEN_END_FRAME -> State.END_FRAME
                "<NEEDS_CLARIFICATION>" -> State.CLARIFICATION
                else -> State.SYMPTOMS
            }

            State.CLARIFICATION -> State.END_FRAME

            State.END_FRAME -> State.DONE
            State.DONE -> State.DONE
        }
    }
}