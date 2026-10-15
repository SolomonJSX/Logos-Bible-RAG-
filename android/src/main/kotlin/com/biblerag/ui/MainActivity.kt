package com.biblerag.ui
import com.biblerag.data.BibleReaderRepository
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.lifecycleScope
import com.biblerag.core.BertWordPieceTokenizer
import com.biblerag.core.BibleNativeEngine
import com.biblerag.core.OnnxEmbeddingProvider
import com.biblerag.engine.BibleRagOrchestrator
import com.biblerag.engine.MemoryManager
import com.biblerag.engine.ScriptureSynthesizer
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var memoryManager: MemoryManager
    private lateinit var orchestrator: BibleRagOrchestrator
    private lateinit var nativeEngine: BibleNativeEngine
    private var onnxEmbeddingProvider: OnnxEmbeddingProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Путь к базе данных в internal storage приложения
        val dbFile = File(filesDir, "bible.db")
        if (!dbFile.exists()) {
            assets.open("bible.db").use { input ->
                dbFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        // 2. Инициализация C++ нативного поискового ядра
        nativeEngine = BibleNativeEngine(dbFile.absolutePath)

        // 3. Фоновая загрузка квантованной нейросети Qwen2.5-1.5B (GGUF) через llama.cpp
        val possibleModelPaths = listOf(
            "/data/local/tmp/qwen.gguf",
            File(filesDir, "qwen.gguf").absolutePath
        )
        val modelPath = possibleModelPaths.firstOrNull { File(it).exists() }
        if (modelPath != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    Log.i("BibleRAG", "Запуск загрузки GGUF модели в память: $modelPath")
                    val loaded = nativeEngine.loadLlama(modelPath, nCtx = 1024, nThreads = 4)
                    if (loaded) {
                        Log.i("BibleRAG", "GGUF модель Qwen2.5-1.5B успешно загружена через llama.cpp!")
                    } else {
                        Log.w("BibleRAG", "Не удалось загрузить GGUF модель, используется резервный синтезатор.")
                    }
                } catch (e: Exception) {
                    Log.e("BibleRAG", "Ошибка при загрузке GGUF модели: ${e.message}", e)
                }
            }
        } else {
            Log.i("BibleRAG", "GGUF файл не обнаружен, активен легковесный синтезатор ScriptureSynthesizer.")
        }

        // 4. Инициализация ONNX Runtime Mobile и WordPiece токенизатора
        try {
            val tokenizer = assets.open("vocab.txt").use { stream ->
                BertWordPieceTokenizer.loadFromStream(stream)
            }
            val modelBytes = assets.open("embedding_model_quantized.onnx").use { it.readBytes() }
            onnxEmbeddingProvider = OnnxEmbeddingProvider(modelBytes, tokenizer)
            Log.i("BibleRAG", "ONNX Embedding Provider успешно инициализирован.")
        } catch (e: Exception) {
            Log.e("BibleRAG", "Ошибка инициализации ONNX: ${e.message}", e)
        }

        // 5. Инициализация Orchestrator с гибридным поиском и генерацией
        orchestrator = BibleRagOrchestrator(
            nativeEngine = nativeEngine,
            embeddingProvider = { query ->
                try {
                    val cleanQuery = cleanQueryForEmbedding(query)
                    onnxEmbeddingProvider?.computeEmbedding(cleanQuery) ?: FloatArray(312) { 0.0f }
                } catch (e: Exception) {
                    Log.w("BibleRAG", "Fallback к нейтральному вектору: ${e.message}")
                    FloatArray(312) { 0.0f }
                }
            },
            llmGenerator = { query, prompt, chunks, useNeuralLlm, onToken ->
                if (useNeuralLlm && nativeEngine.isLlamaLoaded) {
                    Log.i("BibleRAG", "Генерация ответа через локальную нейросеть Qwen2.5-1.5B (GGUF)...")
                    val ok = nativeEngine.generateLlama(prompt, maxTokens = 250) { piece ->
                        kotlinx.coroutines.runBlocking {
                            onToken(piece)
                        }
                        true
                    }
                    if (!ok) {
                        Log.w("BibleRAG", "Ошибка нативной генерации, fallback к ScriptureSynthesizer")
                        ScriptureSynthesizer.generate(query, chunks, onToken)
                    }
                } else {
                    Log.i("BibleRAG", "Генерация ответа через мгновенный синтезатор ScriptureSynthesizer...")
                    ScriptureSynthesizer.generate(query, chunks, onToken)
                }
            }
        )

        val viewModel = ChatViewModel(orchestrator)
        val readerRepo = BibleReaderRepository(dbFile.absolutePath)

        setContent {
            com.biblerag.ui.theme.LogosTheme {
                var showSplash by remember { mutableStateOf(true) }
                // 0 - Книги Писания, 1 - ИИ-Чат
                var currentTab by remember { mutableStateOf(0) }
                var targetLocationToOpen by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
                val books = remember { readerRepo.getBooks() }

                // Фоновый прогрев страниц книги во время показа заставки SplashScreen
                LaunchedEffect(books) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        readerRepo.getAllPages(books)
                    }
                }

                Crossfade(
                    targetState = showSplash,
                    animationSpec = tween(500),
                    label = "SplashTransition"
                ) { isSplash ->
                    if (isSplash) {
                        SplashScreen(
                            onSplashFinished = { showSplash = false }
                        )
                    } else {
                        Scaffold(
                            bottomBar = {
                                NavigationBar(
                                    containerColor = Color(0xFF1E140C), // В тон переплету
                                    contentColor = Color(0xFFC5A059)
                                ) {
                                    NavigationBarItem(
                                        selected = currentTab == 0,
                                        onClick = { currentTab = 0 },
                                        icon = { Icon(Icons.Default.AutoStories, contentDescription = "Писание") },
                                        label = { Text("Писание") },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color(0xFF1E140C),
                                            selectedTextColor = Color(0xFFC5A059),
                                            indicatorColor = Color(0xFFC5A059),
                                            unselectedIconColor = Color(0xFF8D7B68),
                                            unselectedTextColor = Color(0xFF8D7B68)
                                        )
                                    )
                                    NavigationBarItem(
                                        selected = currentTab == 1,
                                        onClick = { currentTab = 1 },
                                        icon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = "ИИ-Диалог") },
                                        label = { Text("ИИ-Диалог") },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color(0xFF1E140C),
                                            selectedTextColor = Color(0xFFC5A059),
                                            indicatorColor = Color(0xFFC5A059),
                                            unselectedIconColor = Color(0xFF8D7B68),
                                            unselectedTextColor = Color(0xFF8D7B68)
                                        )
                                    )
                                }
                            }
                        ) { innerPadding ->
                            androidx.compose.foundation.layout.Box(modifier = Modifier.padding(innerPadding)) {
                                if (currentTab == 0) {
                                    BookReaderScreen(
                                        repository = readerRepo,
                                        books = books,
                                        targetLocation = targetLocationToOpen,
                                        onTargetLocationConsumed = { targetLocationToOpen = null }
                                    )
                                } else {
                                    BibleChatScreen(
                                        viewModel = viewModel,
                                        onOpenScripture = { canonicalRef ->
                                            val parsed = parseCanonicalRef(canonicalRef)
                                            if (parsed != null) {
                                                val (bookName, chapter, verse) = parsed
                                                val matchedBook = books.firstOrNull {
                                                    it.nameRu.equals(bookName, ignoreCase = true) ||
                                                    it.nameRu.startsWith(bookName, ignoreCase = true) ||
                                                    bookName.startsWith(it.nameRu, ignoreCase = true)
                                                }
                                                if (matchedBook != null) {
                                                    targetLocationToOpen = Triple(matchedBook.bookOrder, chapter, verse)
                                                    currentTab = 0
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun parseCanonicalRef(canonicalRef: String): Triple<String, Int, Int>? {
        // Примеры: "Второзаконие 23:25", "Иисус Навин 15:37-40", "Бытие 1:1", "Псалтирь 22"
        val regex = Regex("""^(.*?)\s+(\d+)(?::(\d+)(?:-\d+)?)?""")
        val match = regex.find(canonicalRef.trim()) ?: return null
        val bookName = match.groupValues[1].trim()
        val chapter = match.groupValues[2].toIntOrNull() ?: 1
        val verse = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 1
        return Triple(bookName, chapter, verse)
    }

    private fun cleanQueryForEmbedding(raw: String): String {
        var q = raw.lowercase().trim()
        val removePrefixes = listOf(
            "есть ли про ", "есть ли о ", "есть ли в библии про ", "есть ли в библии о ",
            "что говорится про ", "что говорится о ", "что говорит библия о ", "что библия говорит о ",
            "что сказано про ", "что сказано о ", "где написано про ", "где написано о ",
            "где говорится о ", "где говорится про ", "расскажи про ", "расскажи о ",
            "что такое "
        )
        for (prefix in removePrefixes) {
            if (q.startsWith(prefix)) {
                q = q.removePrefix(prefix).trim()
                break
            }
        }
        val clean = q.replace(Regex("[^a-zA-Zа-яА-Я0-9\\s]"), " ").trim()
        return if (clean.length >= 3) clean else raw
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterComponentCallbacks(memoryManager)
        nativeEngine.freeLlama()
        try {
            onnxEmbeddingProvider?.close()
        } catch (e: Exception) {
            Log.w("BibleRAG", "Ошибка закрытия ONNX: ${e.message}")
        }
    }
}
