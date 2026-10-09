package com.clinic.clinicapp.data.nlu

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NluEngine(private val context: Context) {

    private val TAG = "NluEngine"

    private var tokenizer: BpeTokenizer? = null
    private var model: NluModel? = null
    private var automaton: FrameAutomaton? = null
    private val parser = FrameParser()

    private var isInitialized = false

    suspend fun initialize(
        onDownloadProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            Log.d(TAG, "Загрузка токенизатора…")
            val tok = BpeTokenizer.fromAssets(context, "nlu/tokenizer.json")
            tokenizer = tok

            Log.d(TAG, "Загрузка TFLite-модели…")
            val mdl = NluModel(context)
            model = mdl

            Log.d(TAG, "Инициализация автомата…")
            automaton = FrameAutomaton(
                tokenizer = tok,
                eosId = tok.idOf("<EOS>") ?: 3,
                padId = tok.idOf("<PAD>") ?: 0,
                assistantId = tok.idOf("<ASSISTANT>") ?: 5,
                userId = tok.idOf("<USER>") ?: 4,
                bosId = tok.idOf("<BOS>") ?: 2
            )

            isInitialized = true
            Log.d(TAG, "NLU готов")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Ошибка инициализации NLU", t)
            false
        }
    }

    suspend fun parse(text: String): FrameParser.Result = withContext(Dispatchers.IO) {
        val tok = tokenizer ?: throw IllegalStateException("NLU не инициализирован")
        val mdl = model ?: throw IllegalStateException("NLU не инициализирован")
        val atm = automaton ?: throw IllegalStateException("NLU не инициализирован")

        val (frame, _) = atm.generate(
            forward = { ids -> mdl.forward(ids) },
            text = text,
            tokenizer = tok
        )
        parser.parse(frame)
    }

    fun release() {
        model?.close()
        model = null
        tokenizer = null
        automaton = null
        isInitialized = false
    }
}