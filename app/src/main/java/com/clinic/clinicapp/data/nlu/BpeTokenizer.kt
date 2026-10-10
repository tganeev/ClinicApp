package com.clinic.clinicapp.data.nlu

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * BPE-токенизатор, совместимый с HuggingFace ByteLevel BPE.
 *
 * Точный порт логики из train/tokenizer_train.py (HF tokenizers).
 * Отличия от «наивной» реализации:
 *  - regex-претокенайзер HF (splitRegex), а не split(" ")
 *  - byteToUni использует инкремент n ТОЛЬКО для непечатных байтов
 *  - спецтокены обрабатываются ДО BPE и не токенизируются текстом
 */
class BpeTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val ranks: Map<Pair<String, String>, Int>,
    private val added: List<Pair<String, Int>>,
    private val byteToUni: Map<Int, String>
) {

    companion object {
        private const val TAG = "BpeTokenizer"

        fun fromAssets(context: Context, path: String = "nlu/tokenizer.json"): BpeTokenizer {
            val json = context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
            return fromJson(json)
        }

        fun fromFile(file: java.io.File): BpeTokenizer {
            return fromJson(file.readText(Charsets.UTF_8))
        }

        private fun fromJson(json: String): BpeTokenizer {
            val root = JSONObject(json)
            val model = root.getJSONObject("model")

            // ---- vocab ----
            val vocabObj = model.getJSONObject("vocab")
            val vocab = HashMap<String, Int>(vocabObj.length())
            vocabObj.keys().forEach { key ->
                vocab[key] = vocabObj.getInt(key)
            }

            // ---- merges: массив строк "a b" или пар-массивов ----
            val mergesArr = model.getJSONArray("merges")
            val ranks = HashMap<Pair<String, String>, Int>(mergesArr.length())
            for (i in 0 until mergesArr.length()) {
                val el = mergesArr.get(i)
                when (el) {
                    is JSONArray -> {
                        val a = el.optString(0, null) ?: continue
                        val b = el.optString(1, null) ?: continue
                        ranks[a to b] = i
                    }
                    is String -> {
                        val parts = el.split(" ", limit = 2)
                        if (parts.size == 2) ranks[parts[0] to parts[1]] = i
                    }
                }
            }

            // ---- added_tokens ----
            val added = mutableListOf<Pair<String, Int>>()
            val addedArr = root.optJSONArray("added_tokens")
            if (addedArr != null) {
                for (i in 0 until addedArr.length()) {
                    val obj = addedArr.getJSONObject(i)
                    val content = obj.optString("content", null) ?: continue
                    val id = obj.optInt("id", -1).takeIf { it >= 0 } ?: continue
                    added += content to id
                }
            }
            // Сортируем по убыванию длины — длинные спецтокены матчатся первыми
            val sortedAdded = added.sortedByDescending { it.first.length }

            // ---- byte → unicode (точная логика bytes_to_unicode из GPT-2) ----
            val bs = buildList {
                addAll(33..126)     // '!'..'~'
                addAll(161..172)    // '¡'..'¬'
                addAll(174..255)    // '®'..'ÿ'
            }.toSet()

            var n = 0
            val byteToUni = HashMap<Int, String>(256)
            for (b in 0..255) {
                byteToUni[b] = if (b in bs) {
                    b.toChar().toString()
                } else {
                    (256 + n++).toChar().toString()
                }
            }

            Log.d(TAG, "Токенизатор загружен: vocab=${vocab.size}, merges=${ranks.size}, added=${sortedAdded.size}")
            return BpeTokenizer(vocab, ranks, sortedAdded, byteToUni)
        }
    }

    private val splitRegex = Regex(
        "'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+"
    )

    /**
     * Кодирует текст в последовательность BPE-ids.
     * Спецтокены вырезаются ДО BPE.
     */
    fun encode(text: String): List<Int> {
        val out = ArrayList<Int>()
        var rest = text

        // 1) Спецтокены (ищем вхождения в начале строки рекурсивно)
        while (rest.isNotEmpty()) {
            val hit = added.firstOrNull { it.first.length > 1 && rest.startsWith(it.first) }
            if (hit == null) break
            out.add(hit.second)
            rest = rest.substring(hit.first.length)
        }

        // 2) Regex-сплит + byte-level + BPE
        for (piece in splitRegex.findAll(rest).map { it.value }) {
            if (piece.isEmpty()) continue

            // Byte-level: каждый байт UTF-8 → печатный символ
            var syms = piece.toByteArray(Charsets.UTF_8)
                .map { b -> byteToUni[b.toInt() and 0xFF]!! }
                .toMutableList()

            if (syms.isEmpty()) continue

            // BPE-слияния: жадный выбор пары с минимальным рангом
            while (syms.size > 1) {
                var best: Pair<String, String>? = null
                var bestRank = Int.MAX_VALUE
                for (i in 0 until syms.size - 1) {
                    val pair = syms[i] to syms[i + 1]
                    val r = ranks[pair] ?: continue
                    if (r < bestRank) {
                        bestRank = r
                        best = pair
                    }
                }
                val chosen = best ?: break

                val merged = ArrayList<String>(syms.size)
                var i = 0
                while (i < syms.size) {
                    if (i < syms.size - 1 &&
                        syms[i] == chosen.first &&
                        syms[i + 1] == chosen.second
                    ) {
                        merged += chosen.first + chosen.second
                        i += 2
                    } else {
                        merged += syms[i]
                        i += 1
                    }
                }
                syms = merged
            }

            // Финальные юниты → ID
            for (s in syms) {
                val id = vocab[s] ?: vocab["<UNK>"]
                if (id != null) out.add(id)
            }
        }
        return out
    }

    /** ID по строке токена (спецтокены и обычные). */
    fun idOf(token: String): Int? {
        added.firstOrNull { it.first == token }?.let { return it.second }
        return vocab[token]
    }

    /** Строка по ID. */
    fun tokenOf(id: Int): String? {
        added.firstOrNull { it.second == id }?.let { return it.first }
        return vocab.entries.firstOrNull { it.value == id }?.key
    }
}