package com.biblerag.engine

import com.biblerag.core.BibleNativeEngine
import com.biblerag.core.NativeSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.io.Closeable

sealed interface RagStreamEvent {
    data class ContextFound(val chunks: List<NativeSearchResult>, val searchTimeMs: Long) : RagStreamEvent
    data class TokenGenerated(val token: String) : RagStreamEvent
    data class Completed(val totalTokens: Int, val durationMs: Long) : RagStreamEvent
    data class Error(val message: String) : RagStreamEvent
}

class BibleRagOrchestrator(
    private val nativeEngine: BibleNativeEngine,
    private val embeddingProvider: (String) -> FloatArray,
    private val llmGenerator: suspend (query: String, prompt: String, chunks: List<NativeSearchResult>, useNeuralLlm: Boolean, onToken: suspend (String) -> Unit) -> Unit
) : Closeable {

    fun ask(query: String, topK: Int = 15, useNeuralLlm: Boolean = false): Flow<RagStreamEvent> = channelFlow {
        val startTime = System.currentTimeMillis()
        try {
            // ШАГ 0: СТРОГАЯ ВАЛИДАЦИЯ ТЕМЫ ДО ЗАПУСКА EMBEDDING И ПОИСКА
            val check = TopicGuard.checkTopic(query)
            if (check is TopicCheckResult.Refusal) {
                // Никаких стихов не отправляем!
                send(RagStreamEvent.ContextFound(emptyList(), 0))
                val words = check.message.split(" ")
                words.forEachIndexed { idx, word ->
                    val suffix = if (idx < words.lastIndex) " " else ""
                    send(RagStreamEvent.TokenGenerated(word + suffix))
                    kotlinx.coroutines.delay(18)
                }
                send(RagStreamEvent.Completed(words.size, System.currentTimeMillis() - startTime))
                return@channelFlow
            }

            // ШАГ 1: Получение вектора запроса (ТОЛЬКО ПОСЛЕ УСПЕШНОЙ ПРОВЕРКИ ТЕМЫ)
            val t0 = System.currentTimeMillis()
            val queryEmbedding = embeddingProvider(query)

            // ШАГ 2: Нативный C++ гибридный поиск (SIMD NEON + FTS5 + RRF)
            val chunks = nativeEngine.search(queryEmbedding, query, topK)
            val searchTime = System.currentTimeMillis() - t0

            if (chunks.isEmpty()) {
                send(RagStreamEvent.ContextFound(emptyList(), searchTime))
                val notFoundMsg = "Мир дому твоему, путник. В священных свитках, открытых ныне предо мною, не найдено прямых стихов по сему вопросу.\n\nПопробуй вопросить иначе или использовать святые слова: «вера», «молитва», «заповеди», «любовь» или «мудрость»."
                val words = notFoundMsg.split(" ")
                words.forEachIndexed { idx, word ->
                    val suffix = if (idx < words.lastIndex) " " else ""
                    send(RagStreamEvent.TokenGenerated(word + suffix))
                    kotlinx.coroutines.delay(40)
                }
                send(RagStreamEvent.Completed(words.size, System.currentTimeMillis() - startTime))
                return@channelFlow
            }

            // ШАГ 3: Отправляем найденные источники в UI
            send(RagStreamEvent.ContextFound(chunks, searchTime))

            // ШАГ 4: Сборка Zero-Shot промпта с топ-5 отрывками
            val synthesisChunks = chunks.take(5)
            val prompt = PromptBuilder.build(query, synthesisChunks)

            // ШАГ 5: Потоковая генерация ответа
            var tokenCount = 0
            llmGenerator(query, prompt, synthesisChunks, useNeuralLlm) { token ->
                tokenCount++
                send(RagStreamEvent.TokenGenerated(token))
            }

            val totalDuration = System.currentTimeMillis() - startTime
            send(RagStreamEvent.Completed(tokenCount, totalDuration))

        } catch (e: Exception) {
            send(RagStreamEvent.Error(e.localizedMessage ?: "Непредвиденная ошибка RAG"))
        }
    }.flowOn(Dispatchers.Default)

    override fun close() {
        nativeEngine.close()
    }
}
