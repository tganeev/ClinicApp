package com.clinic.clinicapp.data.nlu

import android.util.Log

/**
 * Constrained decoding: маска допустимых токенов для каждого состояния.
 * Точный порт train/constrained.py на Kotlin.
 *
 * Гарантирует синтаксически валидный фрейм.
 *
 * Ключевые правила:
 *  - INTENT: только 4 интента.
 *  - SPECIALTY: 10 специальностей.
 *  - TIME: либо <T_UNKNOWN> → SYMPTOMS, либо <TIME> → FACTORS.
 *  - FACTORS: только токены с rank > lastRank (ОБЛАСТЬ 0 < ДЕНЬ 1 < ВРЕМЯ 2).
 *             Закрыть слот можно только после выбора хотя бы одного фактора.
 *  - SYMPTOMS: любое число разных симптомов, затем <END_FRAME> или
 *              <NEEDS_CLARIFICATION>.
 */
class FrameAutomaton(private val idOf: (String) -> Int?) {

    private val TAG = "FrameAutomaton"

    private enum class S {
        INTENT, SPECIALTY, TIME, FACTORS, SYMPTOMS, CLARIF, END_FRAME, DONE
    }

    private var state = S.INTENT
    private var lastRank = -1
    private var emitted = 0
    private val symptoms = mutableSetOf<String>()

    private fun rank(t: String): Int = when (t) {
        in FrameVocab.TIME_SCOPE -> 0
        in FrameVocab.TIME_DAY -> 1
        else -> 2
    }

    private fun ids(ts: Collection<String>): Set<Int> =
        ts.mapNotNull { idOf(it) }.toSet()

    /** Возвращает текущее множество разрешённых ID токенов. */
    fun allowed(): Set<Int> = when (state) {
        S.INTENT -> ids(FrameVocab.INTENTS)

        S.SPECIALTY -> ids(FrameVocab.SPECIALTY)

        S.TIME -> ids(listOf("<T_UNKNOWN>", "<TIME>"))

        S.FACTORS -> {
            val ok = (FrameVocab.TIME_SCOPE + FrameVocab.TIME_DAY + FrameVocab.TIME_OF_DAY)
                .filter { rank(it) > lastRank }
            val base = ids(ok)
            if (emitted > 0) {
                val closeId = idOf("</TIME>")
                if (closeId != null) base + closeId else base
            } else base
        }

        S.SYMPTOMS -> {
            val remaining = FrameVocab.SYMPTOMS.filter { it !in symptoms }
            val base = ids(remaining).toMutableSet()
            idOf("<NEEDS_CLARIFICATION>")?.let { base += it }
            idOf("<END_FRAME>")?.let { base += it }
            base
        }

        S.CLARIF -> setOfNotNull(idOf("<END_FRAME>"))
        S.END_FRAME -> setOfNotNull(idOf("<EOS>"))
        S.DONE -> setOfNotNull(idOf("<EOS>"))
    }

    /** Переход автомата по выбранному токену. */
    fun step(id: Int) {
        val tok = tokenOfId(id) ?: run {
            Log.w(TAG, "step: неизвестный id=$id, форсируем DONE")
            state = S.DONE
            return
        }
        Log.d(TAG, "step: state=$state, token=$tok")
        state = when (state) {
            S.INTENT -> when (tok) {
                "<UNSUPPORTED>" -> S.END_FRAME
                "<CANCEL>" -> S.SYMPTOMS
                "<BOOK>", "<RESCHEDULE>" -> S.SPECIALTY
                else -> S.END_FRAME
            }

            S.SPECIALTY -> S.TIME

            S.TIME -> if (tok == "<T_UNKNOWN>") S.SYMPTOMS else S.FACTORS

            S.FACTORS -> when (tok) {
                "</TIME>" -> S.SYMPTOMS
                else -> {
                    lastRank = rank(tok)
                    emitted++
                    S.FACTORS
                }
            }

            S.SYMPTOMS -> when {
                tok in symptoms -> S.SYMPTOMS
                tok == "<NEEDS_CLARIFICATION>" -> S.CLARIF
                tok == "<END_FRAME>" -> S.END_FRAME
                tok.startsWith("<SYMPTOM_") -> {
                    symptoms += tok
                    S.SYMPTOMS
                }
                else -> S.SYMPTOMS
            }

            S.CLARIF -> if (tok == "<END_FRAME>") S.END_FRAME else S.CLARIF
            S.END_FRAME -> if (tok == "<EOS>") S.DONE else S.END_FRAME
            S.DONE -> S.DONE
        }
    }

    val isDone: Boolean get() = state == S.DONE

    /** Обратный маппинг id → token. Заполняется при первом вызове. */
    private val idToToken: Map<Int, String> by lazy {
        val m = HashMap<Int, String>()
        (FrameVocab.INTENTS + FrameVocab.SPECIALTY + FrameVocab.SYMPTOMS +
                FrameVocab.TIME_SCOPE + FrameVocab.TIME_DAY + FrameVocab.TIME_OF_DAY +
                FrameVocab.SERVICE).forEach { t ->
            idOf(t)?.let { m[it] = t }
        }
        m
    }

    private fun tokenOfId(id: Int): String? = idToToken[id]
}