package com.biblerag.core

import java.io.InputStream

/**
 * WordPiece токенизатор для модели rubert-tiny2.
 * Работает с vocab.txt (83 828 токенов) прямо из assets Android.
 */
class BertWordPieceTokenizer(
    private val vocab: Map<String, Int>
) {
    companion object {
        const val PAD_TOKEN = "[PAD]"
        const val UNK_TOKEN = "[UNK]"
        const val CLS_TOKEN = "[CLS]"
        const val SEP_TOKEN = "[SEP]"

        const val PAD_ID = 0L
        const val UNK_ID = 1L
        const val CLS_ID = 2L
        const val SEP_ID = 3L

        fun loadFromStream(inputStream: InputStream): BertWordPieceTokenizer {
            val vocabMap = HashMap<String, Int>(85000)
            var index = 0
            inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val token = line.trim()
                    if (token.isNotEmpty()) {
                        vocabMap[token] = index
                    }
                    index++
                }
            }
            return BertWordPieceTokenizer(vocabMap)
        }
    }

    /**
     * Токенизирует текст в массив идентификаторов токенов BERT ([CLS] ... [SEP]).
     */
    fun encode(text: String, maxLen: Int = 64): LongArray {
        val result = ArrayList<Long>()
        result.add(CLS_ID)

        val words = cleanAndSplit(text)
        for (word in words) {
            val subTokens = wordPieceTokenize(word)
            for (tid in subTokens) {
                if (result.size >= maxLen - 1) break
                result.add(tid)
            }
            if (result.size >= maxLen - 1) break
        }

        result.add(SEP_ID)
        return result.toLongArray()
    }

    private fun cleanAndSplit(text: String): List<String> {
        val lower = text.lowercase()
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()

        for (ch in lower) {
            if (ch.isLetterOrDigit()) {
                sb.append(ch)
            } else {
                if (sb.isNotEmpty()) {
                    tokens.add(sb.toString())
                    sb.clear()
                }
            }
        }
        if (sb.isNotEmpty()) {
            tokens.add(sb.toString())
        }
        return tokens
    }

    private fun wordPieceTokenize(word: String): List<Long> {
        val subTokens = mutableListOf<Long>()
        var start = 0

        while (start < word.length) {
            var end = word.length
            var matchedId: Long? = null
            var matchedEnd = -1

            while (end > start) {
                val sub = if (start == 0) {
                    word.substring(start, end)
                } else {
                    "##" + word.substring(start, end)
                }

                val id = vocab[sub]
                if (id != null) {
                    matchedId = id.toLong()
                    matchedEnd = end
                    break
                }
                end--
            }

            if (matchedId == null) {
                // Если не найден подтокен, возвращаем [UNK]
                subTokens.add(UNK_ID)
                break
            } else {
                subTokens.add(matchedId)
                start = matchedEnd
            }
        }

        return subTokens
    }
}
