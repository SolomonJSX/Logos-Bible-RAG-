package com.biblerag.core

import java.io.Closeable

fun interface LlamaTokenCallback {
    fun onToken(piece: String): Boolean
}

/**
 * Высокопроизводительный нативный движок RAG-поиска по Библии (C++ / ARM NEON / SQLite FTS5)
 * и инференса GGUF нейросетей (llama.cpp).
 */
class BibleNativeEngine(private val dbPath: String) : Closeable {
    private var nativeHandle: Long = 0L

    companion object {
        init {
            // Загрузка libbiblerag.so, скомпилированного через Android NDK
            System.loadLibrary("biblerag")
        }
    }

    init {
        nativeHandle = nativeInit(dbPath)
        if (nativeHandle == 0L) {
            throw IllegalStateException("Не удалось инициализировать BibleNativeEngine с базой: $dbPath")
        }
    }

    /**
     * Выполняет гибридный поиск:
     * @param queryEmbedding 312-мерный нормализованный вектор от rubert-tiny2
     * @param queryText Строка поискового запроса для FTS5 BM25
     * @param topK Количество возвращаемых контекстных чанков
     */
    fun search(queryEmbedding: FloatArray, queryText: String, topK: Int = 3): List<NativeSearchResult> {
        check(nativeHandle != 0L) { "BibleNativeEngine уже закрыт!" }
        val results = nativeSearch(nativeHandle, queryEmbedding, queryText, topK)
        return results?.toList() ?: emptyList()
    }

    // --- llama.cpp GGUF методы ---

    fun loadLlama(modelPath: String, nCtx: Int = 2048, nThreads: Int = 4): Boolean {
        return nativeLoadLlama(modelPath, nCtx, nThreads)
    }

    fun freeLlama() {
        nativeFreeLlama()
    }

    val isLlamaLoaded: Boolean
        get() = nativeIsLlamaLoaded()

    fun generateLlama(prompt: String, maxTokens: Int = 512, onToken: (String) -> Boolean): Boolean {
        return nativeGenerateLlama(prompt, maxTokens, LlamaTokenCallback { piece -> onToken(piece) })
    }

    override fun close() {
        if (nativeHandle != 0L) {
            nativeClose(nativeHandle)
            nativeHandle = 0L
        }
        freeLlama()
    }

    // --- Нативные JNI методы ---
    private external fun nativeInit(dbPath: String): Long
    private external fun nativeSearch(
        enginePtr: Long,
        queryEmbedding: FloatArray,
        queryText: String,
        topK: Int
    ): Array<NativeSearchResult>?
    private external fun nativeClose(enginePtr: Long)

    private external fun nativeLoadLlama(modelPath: String, nCtx: Int, nThreads: Int): Boolean
    private external fun nativeFreeLlama()
    private external fun nativeIsLlamaLoaded(): Boolean
    private external fun nativeGenerateLlama(prompt: String, maxTokens: Int, callback: LlamaTokenCallback): Boolean
}
