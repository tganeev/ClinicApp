package com.clinic.clinicapp.data.nlu

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Высокоуровневый API NLU:
 *   text → токенизация → ONNX → constrained decode → слоты.
 *
 * На этапе отладки модель берётся из assets (копируется в filesDir при первом запуске).
 * Позже заменим на скачивание через NluModelDownloader.
 */
class NluEngine(private val context: Context) {

    private val TAG = "NluEngine"

    private var tokenizer: BpeTokenizer? = null
    private var model: NluModel? = null
    private var automaton: FrameAutomaton? = null
    private val parser = FrameParser()

    private var isInitialized = false

    companion object {
        private const val ASSET_MODEL = "nlu/model.onnx"
        private const val LOCAL_MODEL = "nlu/model.onnx"
    }

    suspend fun initialize(
        onDownloadProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            Log.d(TAG, "Загрузка токенизатора…")
            val tok = BpeTokenizer.fromAssets(context, "nlu/tokenizer.json")
            tokenizer = tok

            Log.d(TAG, "Подготовка ONNX-модели из assets…")
            val modelFile = copyModelFromAssetsIfNeeded()
            val mdl = NluModel(modelFile)
            model = mdl

            Log.d(TAG, "Инициализация автомата…")
            automaton = FrameAutomaton(
                tokenizer = tok,
                eosId = tok.idOf("<EOS>") ?: 3,
                padId = tok.idOf("<PAD>") ?: 0
            )

            isInitialized = true
            Log.d(TAG, "NLU готов")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Ошибка инициализации NLU", t)
            false
        }
    }

    /**
     * Копирует model.onnx из assets в filesDir/nlu/model.onnx, если его там ещё нет.
     * Нужно, потому что ONNX Runtime не умеет читать модель напрямую из assets —
     * ему нужен файл на диске.
     */
    private fun copyModelFromAssetsIfNeeded(): File {
        val target = File(context.filesDir, LOCAL_MODEL)
        if (target.exists() && target.length() > 0) {
            Log.d(TAG, "Модель уже в filesDir: ${target.length()} байт")
            return target
        }

        target.parentFile?.mkdirs()

        Log.d(TAG, "Копирование модели из assets в filesDir…")
        val startTime = System.currentTimeMillis()

        context.assets.open(ASSET_MODEL).use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                var read: Int
                while (input.read(buffer).also { read = it } > 0) {
                    output.write(buffer, 0, read)
                    total += read
                }
                Log.d(TAG, "Скопировано $total байт")
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Копирование завершено за ${elapsed}мс, размер=${target.length()} байт")
        return target
    }

    /**
     * Парсит текст. Предполагает, что initialize() уже был вызван.
     */
    suspend fun parse(text: String): FrameParser.Result = withContext(Dispatchers.IO) {
        val tok = tokenizer ?: throw IllegalStateException("NLU не инициализирован")
        val mdl = model ?: throw IllegalStateException("NLU не инициализирован")
        val atm = automaton ?: throw IllegalStateException("NLU не инициализирован")

        val inputIds = tok.encode(text, maxLength = 128)
        val (frame, _) = atm.generate(
            forward = { ids -> mdl.forward(ids) },
            initialIds = inputIds
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