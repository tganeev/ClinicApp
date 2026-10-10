package com.clinic.clinicapp.data.nlu

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Arrays

/**
 * Единая точка входа NLU.
 *
 * Пайплайн: text → BPE → prompt [BOS, USER, ...text, ASSISTANT] →
 *           TFLite → argmax по маске автомата → фрейм токенов.
 *
 * Референс: train/constrained.py + train/export_tflite.py.
 */
class ClinicNLU(private val context: Context) {

    private val TAG = "ClinicNLU"

    private val vocabSize = 3000
    private val ctxLen = 128

    // Спец-ids (совпадают с tokenizer.json)
    private val padId = 0
    private val bosId = 2
    private val eosId = 3
    private val userId = 4
    private val assistantId = 5

    private var tflite: Interpreter? = null
    private var tok: BpeTokenizer? = null
    private var idToToken: Map<Int, String> = emptyMap()
    private var autom: FrameAutomaton? = null
    private var initialized = false

    /** Загрузка модели, токенизатора и создание автомата. */
    fun initialize() {
        if (initialized) return

        // 1) TFLite-модель из assets (mmap)
        val mmBuf: MappedByteBuffer = context.assets.openFd("nlu/model_int8.tflite").use { fd ->
            FileInputStream(fd.fileDescriptor).channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength
            )
        }
        tflite = Interpreter(mmBuf, Interpreter.Options().apply { numThreads = 2 })
        Log.d(TAG, "TFLite-модель загружена")

        // 2) Токенизатор
        val tokenizer = BpeTokenizer.fromAssets(context, "nlu/tokenizer.json")
        tok = tokenizer
        Log.d(TAG, "Токенизатор загружен")

        // 3) Обратный маппинг id → token (для вывода фрейма и для автомата)
        val tokenizerJson = context.assets.open("nlu/tokenizer.json")
            .bufferedReader(Charsets.UTF_8).readText()
        val vocabObj = JSONObject(tokenizerJson)
            .getJSONObject("model").getJSONObject("vocab")
        val map = HashMap<Int, String>(vocabObj.length())
        vocabObj.keys().forEach { k -> map[vocabObj.getInt(k)] = k }
        // Добавим спецтокены из added_tokens
        val addedArr = JSONObject(tokenizerJson).optJSONArray("added_tokens")
        if (addedArr != null) {
            for (i in 0 until addedArr.length()) {
                val o = addedArr.getJSONObject(i)
                val c = o.optString("content", null) ?: continue
                val id = o.optInt("id", -1).takeIf { it >= 0 } ?: continue
                map[id] = c
            }
        }
        idToToken = map

        // 4) Автомат
        autom = FrameAutomaton { t -> tokenizer.idOf(t) }
        initialized = true
        Log.d(TAG, "NLU готов")
    }

    /**
     * Распознать текст и вернуть строку фрейма, например:
     *   "<BOOK> <DENTIST> <TIME> T_NEXT_WEEK T_FRI T_EVENING </TIME> <SYMPTOM_TOOTHACHE> <END_FRAME>"
     */
    fun parse(text: String, maxSteps: Int = 24): String {
        val interp = tflite ?: throw IllegalStateException("NLU не инициализирован")
        val tokenizer = tok ?: throw IllegalStateException("NLU не инициализирован")
        val automaton = autom ?: throw IllegalStateException("NLU не иициализирован")

        // Промпт: [BOS, USER, ...text_ids, ASSISTANT]
        val textIds = tokenizer.encode(text)
        Log.d(TAG, "Текст: «$text»")
        Log.d(TAG, "Токены (${textIds.size}): ${textIds.map { idToToken[it] ?: "?" }}")

        val seq = ArrayList<Int>(listOf(bosId, userId) + textIds)
        seq.add(assistantId)

        val inBuf = arrayOf(LongArray(ctxLen) { padId.toLong() })
        val outBuf = arrayOf(Array(ctxLen) { FloatArray(vocabSize) })
        val frame = StringBuilder()

        for (step in 0 until maxSteps) {
            // Обрезаем до последних ctxLen токенов при переполнении
            val len = seq.size.coerceAtMost(ctxLen)
            val start = seq.size - len
            Arrays.fill(inBuf[0], padId.toLong())
            for (i in 0 until len) inBuf[0][i] = seq[start + i].toLong()

            interp.run(inBuf, outBuf)
            val logits = outBuf[0][len - 1]

            val allowed = automaton.allowed()
            if (allowed.isEmpty()) {
                Log.d(TAG, "Шаг $step: автомат не разрешает ни одного токена")
                break
            }

            // argmax по маске
            var bestId = -1
            var bestVal = Float.NEGATIVE_INFINITY
            for (id in allowed) {
                if (id in 0 until logits.size && logits[id] > bestVal) {
                    bestVal = logits[id]
                    bestId = id
                }
            }
            if (bestId < 0) break

            val tokenName = idToToken[bestId] ?: "<UNK:$bestId>"
            Log.d(TAG, "Шаг $step: выбран $tokenName (id=$bestId)")

            if (bestId == eosId) break

            frame.append(tokenName).append(' ')
            seq.add(bestId)
            automaton.step(bestId)
            if (automaton.isDone) break
        }

        val result = frame.toString().trim()
        Log.d(TAG, "Frame: $result")
        return result
    }

    fun release() {
        tflite?.close()
        tflite = null
        tok = null
        autom = null
        initialized = false
    }
}