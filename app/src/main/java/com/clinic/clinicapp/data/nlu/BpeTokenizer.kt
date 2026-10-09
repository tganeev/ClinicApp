package com.clinic.clinicapp.data.nlu

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * BPE-токенизатор, совместимый с HuggingFace ByteLevel BPE.
 * Читает tokenizer.json, хранит vocab и merges.
 *
 * ВАЖНО: ByteLevel BPE работает с байтами UTF-8, а не с символами.
 * Порядок: текст → UTF-8 байты → ByteLevel-символы → BPE-слияния → ID.
 */
class BpeTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val merges: Map<Pair<String, String>, Int>,
    private val addedTokens: Map<String, Int>
) {

    companion object {
        private const val TAG = "BpeTokenizer"

        /** Загружает tokenizer.json из assets. */
        fun fromAssets(context: Context, path: String = "nlu/tokenizer.json"): BpeTokenizer {
            val json = context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
            return fromJson(json)
        }

        /** Загружает tokenizer.json с диска. */
        fun fromFile(file: java.io.File): BpeTokenizer {
            return fromJson(file.readText(Charsets.UTF_8))
        }

        private fun fromJson(json: String): BpeTokenizer {
            // Отключаем строгий режим: некоторые ключи могут не соответствовать
            // нашей модели данных, но нам нужны только vocab и merges.
            val parser = Json { ignoreUnknownKeys = true; isLenient = true }
            val root = parser.parseToJsonElement(json).jsonObject
            val model = root["model"]!!.jsonObject

            // ---- vocab: {"<PAD>": 0, "при": 163, ...} ----
            val vocabObj = model["vocab"]!!.jsonObject
            val vocab = HashMap<String, Int>(vocabObj.size)
            for ((key, value) in vocabObj) {
                vocab[key] = value.jsonPrimitive.int
            }

            // ---- merges: [["а","б"], ["аб","в"], ...] ----
            // Внимание: в одних версиях HF merges — массив пар-массивов,
            // в других — массив строк "а б". Поддерживаем оба формата.
            val mergesArr = model["merges"]!!.jsonArray
            val merges = HashMap<Pair<String, String>, Int>(mergesArr.size)
            mergesArr.forEachIndexed { index, element ->
                when (element) {
                    is JsonArray -> {
                        // Формат: ["а", "б"]
                        val a = element.getOrNull(0)?.jsonPrimitive?.contentOrNull ?: return@forEachIndexed
                        val b = element.getOrNull(1)?.jsonPrimitive?.contentOrNull ?: return@forEachIndexed
                        merges[a to b] = index
                    }
                    is kotlinx.serialization.json.JsonPrimitive -> {
                        // Формат: "а б"
                        val str = element.contentOrNull ?: return@forEachIndexed
                        val parts = str.split(" ", limit = 2)
                        if (parts.size == 2) {
                            merges[parts[0] to parts[1]] = index
                        }
                    }
                    else -> { /* игнорируем */ }
                }
            }

            // ---- added_tokens (специальные) ----
            val added = HashMap<String, Int>()
            (root["added_tokens"] as? JsonArray)?.forEach { el ->
                val obj = el as? JsonObject ?: return@forEach
                val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val id = obj["id"]?.jsonPrimitive?.int ?: return@forEach
                added[content] = id
            }

            Log.d(TAG, "Токенизатор загружен: vocab=${vocab.size}, merges=${merges.size}, added=${added.size}")
            return BpeTokenizer(vocab, merges, added)
        }
    }

    /**
     * Кодирует текст БЕЗ специальных токенов.
     * Возвращает только ID токенов текста (для промпта TFLite-модели).
     */
    fun encodeText(text: String): List<Int> {
        val result = ArrayList<Int>()
        val words = text.lowercase().trim().split(Regex("\\s+"))
        for (word in words) {
            if (word.isEmpty()) continue
            result.addAll(encodeWord(word))
        }
        return result
    }

    /**
     * Кодирует текст в массив ID длиной maxLength.
     * Схема: [<BOS>] + токены текста + [<EOS>] + [<PAD>...].
     */
    fun encode(text: String, maxLength: Int = 128): LongArray {
        val result = ArrayList<Long>(maxLength)

        // <BOS>
        val bosId = idOf("<BOS>") ?: 2
        result += bosId.toLong()

        // Токенизируем текст по словам
        val words = text.lowercase().trim().split(Regex("\\s+"))
        for (word in words) {
            if (word.isEmpty()) continue
            val ids = encodeWord(word)
            for (id in ids) {
                if (result.size >= maxLength - 1) break
                result += id.toLong()
            }
            if (result.size >= maxLength - 1) break
        }

        // <EOS>
        if (result.size < maxLength) {
            val eosId = idOf("<EOS>") ?: 3
            result += eosId.toLong()
        }

        // <PAD> до maxLength
        val padId = idOf("<PAD>") ?: 0
        while (result.size < maxLength) {
            result += padId.toLong()
        }

        return result.toLongArray()
    }

    /**
     * Кодирует одно слово через ByteLevel BPE.
     * 1. UTF-8 байты
     * 2. ByteLevel-маппинг (байт → печатный символ)
     * 3. BPE-слияния
     * 4. Маппинг юнитов в ID
     */
    private fun encodeWord(word: String): List<Int> {
        // 1. UTF-8 байты
        val bytes = word.toByteArray(Charsets.UTF_8)

        // 2. ByteLevel: каждый байт → один печатный Unicode-символ
        val chars = bytes.map { byteToPrintableChar(it) }

        // 3. BPE-слияния
        val merged = applyBpe(chars)

        // 4. В ID
        return merged.mapNotNull { unit -> vocab[unit] }
    }

    /**
     * ByteLevel-маппинг: байт 0..255 → «печатный» Unicode-символ.
     * Соответствует логике `bytes_to_unicode` из GPT-2 / HuggingFace.
     */
    private fun byteToPrintableChar(byte: Byte): String {
        val b = byte.toInt() and 0xFF

        // bs = список «непечатных» байтов, которые надо сдвинуть
        val bs = listOf(
            0..32, 127..160, 173..173
        ).flatten().toSet()

        return if (b in bs) {
            // Сдвигаем в диапазон печатных символов
            String(Character.toChars(b + 256))
        } else {
            // Оставляем как есть
            b.toChar().toString()
        }
    }

    /**
     * Жадный BPE: пока есть слияния, применяем пару с минимальным rank.
     */
    private fun applyBpe(chars: List<String>): List<String> {
        if (chars.size <= 1) return chars
        var symbols = chars.toMutableList()

        while (true) {
            var bestPair: Pair<String, String>? = null
            var bestRank = Int.MAX_VALUE

            for (i in 0 until symbols.size - 1) {
                val pair = symbols[i] to symbols[i + 1]
                val rank = merges[pair] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    bestPair = pair
                }
            }

            val pair = bestPair ?: break

            // Сливаем все вхождения выбранной пары
            val merged = ArrayList<String>(symbols.size)
            var i = 0
            while (i < symbols.size) {
                if (i < symbols.size - 1 &&
                    symbols[i] == pair.first &&
                    symbols[i + 1] == pair.second
                ) {
                    merged += pair.first + pair.second
                    i += 2
                } else {
                    merged += symbols[i]
                    i += 1
                }
            }
            symbols = merged
            if (symbols.size == 1) break
        }

        return symbols
    }

    /** ID токена по его строке (для специальных токенов). */
    fun idOf(token: String): Int? = addedTokens[token] ?: vocab[token]

    /** Строка токена по ID. */
    fun tokenOf(id: Long): String? {
        val i = id.toInt()
        // Сначала специальные, потом обычные
        addedTokens.entries.find { it.value == i }?.let { return it.key }
        return vocab.entries.find { it.value == i }?.key
    }
}