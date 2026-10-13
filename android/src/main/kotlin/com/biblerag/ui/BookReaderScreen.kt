package com.biblerag.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biblerag.R
import com.biblerag.data.BibleReaderRepository
import com.biblerag.model.BookItem
import com.biblerag.model.ChapterRef
import com.biblerag.model.VerseItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Цветовая палитра антикварной книги Logos
private val ParchmentBg = Color(0xFFF9F5EC)
private val ParchmentDarker = Color(0xFFF1E9DA)
private val InkDark = Color(0xFF2C2219)
private val InkGold = Color(0xFF9E782F)
private val AccentGold = Color(0xFFC5A059)
private val DeepLeather = Color(0xFF1B1109)
private val DropCapColor = Color(0xFF8B1E0F) // Винтажный киноварь для буквицы

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookReaderScreen(
    repository: BibleReaderRepository,
    books: List<BookItem>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allChapters = remember(books) { repository.getAllChapters(books) }

    // Запоминание страницы, на которой остановились
    val prefs = remember { context.getSharedPreferences("logos_reader_prefs", Context.MODE_PRIVATE) }
    val savedPage = remember { prefs.getInt("last_read_page", 0) }

    // Страница 0 = Обложка
    // Страница 1 = Оглавление
    // Страница 2..N = Главы Библии (страница книги 1 = index 2)
    val totalPages = 2 + allChapters.size
    val pagerState = rememberPagerState(
        initialPage = savedPage.coerceIn(0, totalPages - 1),
        pageCount = { totalPages }
    )

    // Автосохранение страницы при перелистывании
    LaunchedEffect(pagerState.currentPage) {
        prefs.edit().putInt("last_read_page", pagerState.currentPage).apply()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepLeather)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .bookPageFlip(pageIndex, pagerState)
            ) {
                when (pageIndex) {
                    0 -> {
                        // Страница 0: Обложка книги
                        BookCoverPage(
                            savedPage = savedPage,
                            onOpenBook = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            },
                            onResumeReading = { targetPage ->
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(targetPage)
                                }
                            }
                        )
                    }
                    1 -> {
                        // Страница 1: Оглавление книги (Содержание в книжном стиле)
                        BookTableOfContentsPage(
                            books = books,
                            allChapters = allChapters,
                            onChapterSelected = { bookOrder, chapter ->
                                val targetIdx = allChapters.indexOfFirst {
                                    it.bookOrder == bookOrder && it.chapter == chapter
                                }
                                if (targetIdx >= 0) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(2 + targetIdx)
                                    }
                                }
                            }
                        )
                    }
                    else -> {
                        // Страницы 2+: Главы Писания (страница 1 книги = index 2)
                        val chapterIdx = pageIndex - 2
                        if (chapterIdx in allChapters.indices) {
                            val chapterRef = allChapters[chapterIdx]
                            BookChapterPage(
                                repository = repository,
                                chapterRef = chapterRef,
                                bookPageNumber = chapterIdx + 1,
                                onBackToContents = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(1)
                                    }
                                },
                                onPrevious = {
                                    if (pageIndex > 2) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pageIndex - 1)
                                        }
                                    }
                                },
                                onNext = {
                                    if (pageIndex < totalPages - 1) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pageIndex + 1)
                                        }
                                    }
                                },
                                hasPrevious = pageIndex > 2,
                                hasNext = pageIndex < totalPages - 1
                            )
                        }
                    }
                }

                // Реалистичная тень сгиба корешка книги слева
                if (pageIndex > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(20.dp)
                            .align(Alignment.CenterStart)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0x3B000000),
                                        Color(0x18000000),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}

/**
 * 3D-модификатор листания страниц книги
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.bookPageFlip(
    page: Int,
    pagerState: PagerState
): Modifier = graphicsLayer {
    val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

    if (pageOffset < -1f || pageOffset > 1f) {
        alpha = 0f
        return@graphicsLayer
    }

    cameraDistance = 16f * density

    if (pageOffset <= 0f) {
        transformOrigin = TransformOrigin(0f, 0.5f)
        rotationY = -pageOffset.coerceIn(-1f, 0f) * 85f
        alpha = 1f - (-pageOffset * 0.35f)
    } else {
        transformOrigin = TransformOrigin(1f, 0.5f)
        rotationY = -pageOffset.coerceIn(0f, 1f) * 85f
        alpha = 1f - (pageOffset * 0.35f)
    }
}

/**
 * Страница 0: Обложка древней книги
 */
@Composable
private fun BookCoverPage(
    savedPage: Int,
    onOpenBook: () -> Unit,
    onResumeReading: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF26180E),
                        Color(0xFF180F08),
                        Color(0xFF0F0905)
                    )
                )
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, AccentGold.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(8.dp)
                .border(1.dp, AccentGold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "❖ ✝ ❖",
                    color = AccentGold,
                    fontSize = 20.sp,
                    letterSpacing = 4.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(CircleShape)
                        .border(3.dp, AccentGold, CircleShape),
                    elevation = CardDefaults.cardElevation(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logos_splash),
                        contentDescription = "Логос",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "L O G O S",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Serif,
                    color = AccentGold,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "СВЯЩЕННОЕ ПИСАНИЕ",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Serif,
                    color = Color(0xFFDFD1B8),
                    letterSpacing = 3.sp
                )

                Text(
                    text = "Ветхий и Новый Завет",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif,
                    color = Color(0xFFA8967D)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Кнопки открытия / продолжения чтения
                if (savedPage >= 2) {
                    val bookPageNum = savedPage - 1
                    Button(
                        onClick = { onResumeReading(savedPage) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGold,
                            contentColor = DeepLeather
                        ),
                        shape = RoundedCornerShape(24.dp),
                        elevation = ButtonDefaults.buttonElevation(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ПРОДОЛЖИТЬ (стр. $bookPageNum)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(onClick = onOpenBook) {
                        Text(
                            text = "Открыть Оглавление",
                            color = AccentGold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Serif
                        )
                    }
                } else {
                    Button(
                        onClick = onOpenBook,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGold,
                            contentColor = DeepLeather
                        ),
                        shape = RoundedCornerShape(24.dp),
                        elevation = ButtonDefaults.buttonElevation(8.dp),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ОТКРЫТЬ КНИГУ",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Страница 1: Интерактивное Оглавление книги (как в настоящей печатной книге)
 */
@Composable
private fun BookTableOfContentsPage(
    books: List<BookItem>,
    allChapters: List<ChapterRef>,
    onChapterSelected: (bookOrder: Int, chapter: Int) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var expandedOldTestament by remember { mutableStateOf(true) }
    var expandedNewTestament by remember { mutableStateOf(true) }
    var expandedBookOrder by remember { mutableStateOf<Int?>(null) }

    val oldTestamentBooks = remember(books) { books.filter { it.isOldTestament } }
    val newTestamentBooks = remember(books) { books.filter { it.isNewTestament } }

    val filteredOldBooks = remember(oldTestamentBooks, searchQuery) {
        if (searchQuery.isBlank()) oldTestamentBooks
        else oldTestamentBooks.filter { it.nameRu.contains(searchQuery, ignoreCase = true) }
    }

    val filteredNewBooks = remember(newTestamentBooks, searchQuery) {
        if (searchQuery.isBlank()) newTestamentBooks
        else newTestamentBooks.filter { it.nameRu.contains(searchQuery, ignoreCase = true) }
    }

    val newTestamentStartPage = remember(allChapters) {
        val matthewOrder = 40
        val idx = allChapters.indexOfFirst { it.bookOrder >= matthewOrder }
        if (idx >= 0) idx + 1 else 930
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ParchmentBg)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Заголовок Оглавления
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "❖  О Г Л А В Л Е Н И Е  ❖",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = InkDark,
                    letterSpacing = 2.sp
                )
            }

            HorizontalDivider(
                color = InkGold.copy(alpha = 0.5f),
                thickness = 1.dp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Поле быстрого поиска по книгам
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                placeholder = {
                    Text(
                        "Поиск книги в оглавлении...",
                        color = Color(0xFF7A6B5D),
                        fontFamily = FontFamily.Serif
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Поиск",
                        tint = InkGold
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить", tint = InkGold)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = InkGold,
                    unfocusedBorderColor = InkGold.copy(alpha = 0.4f),
                    focusedTextColor = InkDark,
                    unfocusedTextColor = InkDark,
                    cursorColor = InkGold
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            // Древовидное оглавление настоящей печатной книги
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Раздел 1: ВЕТХИЙ ЗАВЕТ (самый большой уровень)
                if (filteredOldBooks.isNotEmpty()) {
                    item {
                        TestamentHeaderRow(
                            title = "ВЕТХИЙ ЗАВЕТ",
                            pageText = "1",
                            isExpanded = expandedOldTestament || searchQuery.isNotBlank(),
                            onToggle = { expandedOldTestament = !expandedOldTestament }
                        )
                    }

                    if (expandedOldTestament || searchQuery.isNotBlank()) {
                        items(filteredOldBooks, key = { it.bookOrder }) { book ->
                            BookTocRow(
                                book = book,
                                allChapters = allChapters,
                                isExpanded = expandedBookOrder == book.bookOrder,
                                onToggle = {
                                    expandedBookOrder = if (expandedBookOrder == book.bookOrder) null else book.bookOrder
                                },
                                onChapterSelected = onChapterSelected
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // Раздел 2: НОВЫЙ ЗАВЕТ (самый большой уровень)
                if (filteredNewBooks.isNotEmpty()) {
                    item {
                        TestamentHeaderRow(
                            title = "НОВЫЙ ЗАВЕТ",
                            pageText = "$newTestamentStartPage",
                            isExpanded = expandedNewTestament || searchQuery.isNotBlank(),
                            onToggle = { expandedNewTestament = !expandedNewTestament }
                        )
                    }

                    if (expandedNewTestament || searchQuery.isNotBlank()) {
                        items(filteredNewBooks, key = { it.bookOrder }) { book ->
                            BookTocRow(
                                book = book,
                                allChapters = allChapters,
                                isExpanded = expandedBookOrder == book.bookOrder,
                                onToggle = {
                                    expandedBookOrder = if (expandedBookOrder == book.bookOrder) null else book.bookOrder
                                },
                                onChapterSelected = onChapterSelected
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Строка верхнего уровня: Ветхий Завет / Новый Завет
 */
@Composable
private fun TestamentHeaderRow(
    title: String,
    pageText: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        color = DeepLeather,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = AccentGold,
                modifier = Modifier.size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.Remove else Icons.Default.Add,
                        contentDescription = null,
                        tint = DeepLeather,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = AccentGold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = ". . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .",
                fontSize = 11.sp,
                fontFamily = FontFamily.Serif,
                color = AccentGold.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            )

            Text(
                text = pageText,
                fontSize = 13.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = AccentGold
            )
        }
    }
}

/**
 * Строка книги с номером страницы и раскрывающимся списком глав (прям как родитель)
 */
@Composable
private fun BookTocRow(
    book: BookItem,
    allChapters: List<ChapterRef>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onChapterSelected: (bookOrder: Int, chapter: Int) -> Unit
) {
    val bookStartPage = remember(book, allChapters) {
        val idx = allChapters.indexOfFirst { it.bookOrder == book.bookOrder }
        if (idx >= 0) idx + 1 else 1
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
    ) {
        // Родительская строка книги (подглава завета с отступом)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isExpanded) DeepLeather else Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isExpanded) AccentGold else InkGold.copy(alpha = 0.5f)
                ),
                modifier = Modifier.size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.Remove else Icons.Default.Add,
                        contentDescription = null,
                        tint = if (isExpanded) AccentGold else InkDark,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "${book.bookOrder}. ${book.nameRu}",
                fontSize = 15.sp,
                fontWeight = if (isExpanded) FontWeight.Bold else FontWeight.SemiBold,
                fontFamily = FontFamily.Serif,
                color = InkDark
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = ". . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .",
                fontSize = 11.sp,
                fontFamily = FontFamily.Serif,
                color = InkGold.copy(alpha = 0.35f),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            )

            Text(
                text = "$bookStartPage",
                fontSize = 13.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                color = InkGold
            )
        }

        // Дочерний список глав (еще глубже с дополнительным отступом)
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 4.dp, top = 2.dp, bottom = 8.dp)
            ) {
                for (ch in 1..book.totalChapters) {
                    val chPage = remember(book.bookOrder, ch, allChapters) {
                        val idx = allChapters.indexOfFirst { it.bookOrder == book.bookOrder && it.chapter == ch }
                        if (idx >= 0) idx + 1 else bookStartPage + ch - 1
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChapterSelected(book.bookOrder, ch) }
                            .padding(vertical = 5.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Глава $ch",
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Serif,
                            color = InkDark
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = ". . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Serif,
                            color = InkGold.copy(alpha = 0.25f),
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        )

                        Text(
                            text = "$chPage",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif,
                            color = Color(0xFF7A6B5D)
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = InkGold.copy(alpha = 0.18f),
            thickness = 0.5.dp
        )
    }
}

/**
 * Страницы 2+: Страница чтения главы из Библии
 */
@Composable
private fun BookChapterPage(
    repository: BibleReaderRepository,
    chapterRef: ChapterRef,
    bookPageNumber: Int,
    onBackToContents: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    hasPrevious: Boolean,
    hasNext: Boolean
) {
    val versesState = produceState<List<VerseItem>?>(initialValue = null, chapterRef) {
        value = withContext(Dispatchers.IO) {
            repository.getChapterVerses(chapterRef.bookOrder, chapterRef.chapter)
        }
    }
    val verses = versesState.value
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ParchmentBg)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Верхний колонтитул книги
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackToContents) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "К содержанию",
                        tint = InkGold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = chapterRef.bookNameRu.uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = InkDark,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "ГЛАВА ${chapterRef.chapter}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Serif,
                        color = InkGold
                    )
                }

                Box(modifier = Modifier.size(48.dp))
            }

            HorizontalDivider(
                color = InkGold.copy(alpha = 0.4f),
                thickness = 1.dp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Содержимое стихов главы
            if (verses == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentGold, modifier = Modifier.size(32.dp))
                }
            } else if (verses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Текст главы не найден",
                        fontFamily = FontFamily.Serif,
                        color = Color.Gray
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(bottom = 16.dp)
                ) {
                    // Декоративный заголовок главы
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "❖",
                                color = AccentGold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "ГЛАВА ${chapterRef.chapter}",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif,
                                color = InkDark,
                                letterSpacing = 2.sp
                            )
                        }
                    }

                    // Отображение стихов
                    verses.forEach { verseItem ->
                        val annotatedVerse = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DropCapColor,
                                    baselineShift = BaselineShift.Superscript
                                )
                            ) {
                                append("${verseItem.verse} ")
                            }
                            append(verseItem.text)
                        }

                        Text(
                            text = annotatedVerse,
                            fontSize = 17.sp,
                            lineHeight = 26.sp,
                            fontFamily = FontFamily.Serif,
                            color = InkDark,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = InkGold.copy(alpha = 0.4f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Нижний колонтитул: стрелки листания и канонический номер страницы
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (hasPrevious) {
                    TextButton(onClick = onPrevious) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = InkGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Пред. стр.",
                            color = InkDark,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(60.dp))
                }

                // Канонический номер страницы книги (например: ~ 1 ~)
                Text(
                    text = "~ $bookPageNumber ~",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif,
                    color = InkGold,
                    fontWeight = FontWeight.Medium
                )

                if (hasNext) {
                    TextButton(onClick = onNext) {
                        Text(
                            text = "След. стр.",
                            color = InkDark,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = InkGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(60.dp))
                }
            }
        }
    }
}
