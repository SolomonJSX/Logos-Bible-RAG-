package com.biblerag.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biblerag.core.NativeSearchResult
import com.biblerag.engine.BibleRagOrchestrator
import com.biblerag.engine.RagStreamEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: Sender,
    val text: String,
    val citations: List<NativeSearchResult> = emptyList(),
    val searchTimeMs: Long? = null,
    val isStreaming: Boolean = false,
    val visibleCitationCount: Int = 3
)

enum class Sender { USER, ASSISTANT }

data class ChatUiState(
    val messages: List<Message> = listOf(
        Message(
            sender = Sender.ASSISTANT,
            text = "Мир дому твоему, путник! Я — Елихореф, царский писец царя Соломона, хранитель священных свитков. О чем вопрошает сердце твое? Задай вопрос о вере, притчах или стихах Писания — и я отыщу для тебя слова мудрости."
        )
    ),
    val isGenerating: Boolean = false,
    val currentInput: String = "",
    val useNeuralLlm: Boolean = false, // По умолчанию мгновенный богословский синтезатор для быстрого отклика
    val topK: Int = 15,
    val suggestedQueries: List<String> = listOf(
        "Что Соломон говорил о мудрости?",
        "Как обрести мир и покой в сердце?",
        "О верной дружбе и любви в Притчах",
        "Притча о добром самарянине",
        "Что Писание говорит о терпении?"
    )
)

class ChatViewModel(
    private val orchestrator: BibleRagOrchestrator
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(currentInput = text) }
    }

    fun setNeuralLlmEnabled(enabled: Boolean) {
        _uiState.update { it.copy(useNeuralLlm = enabled) }
    }

    fun setTopK(k: Int) {
        _uiState.update { it.copy(topK = k) }
    }

    fun loadMoreCitations(messageId: String) {
        _uiState.update { state ->
            val updated = state.messages.map { msg ->
                if (msg.id == messageId) {
                    msg.copy(visibleCitationCount = msg.visibleCitationCount + 3)
                } else msg
            }
            state.copy(messages = updated)
        }
    }

    fun collapseCitations(messageId: String) {
        _uiState.update { state ->
            val updated = state.messages.map { msg ->
                if (msg.id == messageId) {
                    msg.copy(visibleCitationCount = 3)
                } else msg
            }
            state.copy(messages = updated)
        }
    }

    fun sendQuery(queryText: String = _uiState.value.currentInput) {
        val query = queryText.trim()
        if (query.isEmpty() || _uiState.value.isGenerating) return

        val userMessage = Message(sender = Sender.USER, text = query)
        val assistantMessage = Message(sender = Sender.ASSISTANT, text = "", isStreaming = true)
        val useNeural = _uiState.value.useNeuralLlm
        val topK = _uiState.value.topK

        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage + assistantMessage,
                isGenerating = true,
                currentInput = ""
            )
        }

        viewModelScope.launch {
            val responseBuilder = StringBuilder()
            var attachedCitations = emptyList<NativeSearchResult>()
            var searchMs: Long? = null

            orchestrator.ask(query, topK = topK, useNeuralLlm = useNeural).collect { event ->
                when (event) {
                    is RagStreamEvent.ContextFound -> {
                        attachedCitations = event.chunks
                        searchMs = event.searchTimeMs
                        updateLastMessage(responseBuilder.toString(), attachedCitations, searchMs, isStreaming = true)
                    }
                    is RagStreamEvent.TokenGenerated -> {
                        responseBuilder.append(event.token)
                        updateLastMessage(responseBuilder.toString(), attachedCitations, searchMs, isStreaming = true)
                    }
                    is RagStreamEvent.Completed -> {
                        updateLastMessage(responseBuilder.toString(), attachedCitations, searchMs, isStreaming = false)
                        _uiState.update { it.copy(isGenerating = false) }
                    }
                    is RagStreamEvent.Error -> {
                        responseBuilder.append("\n[Ошибка: ${event.message}]")
                        updateLastMessage(responseBuilder.toString(), attachedCitations, searchMs, isStreaming = false)
                        _uiState.update { it.copy(isGenerating = false) }
                    }
                }
            }
        }
    }

    private fun updateLastMessage(
        text: String,
        citations: List<NativeSearchResult>,
        searchMs: Long?,
        isStreaming: Boolean
    ) {
        _uiState.update { state ->
            if (state.messages.isEmpty()) return@update state
            val updatedMessages = state.messages.toMutableList()
            val lastIdx = updatedMessages.lastIndex
            updatedMessages[lastIdx] = updatedMessages[lastIdx].copy(
                text = text,
                citations = citations,
                searchTimeMs = searchMs,
                isStreaming = isStreaming
            )
            state.copy(messages = updatedMessages)
        }
    }

    override fun onCleared() {
        super.onCleared()
        orchestrator.close()
    }
}
