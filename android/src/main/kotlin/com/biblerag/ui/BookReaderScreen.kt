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
import com.biblerag.model.BookPageRef
import com.biblerag.model.ChapterRef
import com.biblerag.model.VerseItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.biblerag.ui.theme.LocalLogosColors

// Цветовая палитра антикварной книги Logos с адаптацией к ночному режиму
private val ParchmentBg: Color @Composable get() = LocalLogosColors.current.pageBg
private val ParchmentDarker: Color @Composable get() = LocalLogosColors.current.pageBgDarker
private val InkDark: Color @Composable get() = LocalLogosColors.current.inkDark
private val InkGold: Color @Composable get() = LocalLogosColors.current.inkGold
private val AccentGold: Color @Composable get() = LocalLogosColors.current.accentGold
private val DeepLeather: Color @Composable get() = LocalLogosColors.current.deepLeather
private val DropCapColor: Color @Composable get() = LocalLogosColors.current.dropCapColor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookReaderScreen(
    repository: BibleReaderRepository,
    books: List<BookItem>,
    targetLocation: Triple<Int, Int, Int>? = null, // bookOrder, chapter, verse
    onTargetLocationConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allPages = remember(books) { repository.getAllPages(books) }

    // Запоминание страницы, на которой остановились
    val prefs = remember { context.getSharedPreferences("logos_reader_prefs", Context.MODE_PRIVATE) }
    val savedPage = remember { prefs.getInt("last_read_page", 0) }

    // Страница 0 = Обложка
    // Страница 1 = Оглавление
    // Страницы 2..N = Реальные страницы книги без скролла (страница книги 1 = index 2)
    val totalPages = 2 + allPages.size
    val pagerState = rememberPagerState(
        initialPage = savedPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0)),
        pageCount = { totalPages }
    )

    // Автосохранение страницы при перелистывании
    LaunchedEffect(pagerState.currentPage) {
        prefs.edit().putInt("last_read_page", pagerState.currentPage).apply()
    }

    // Мгновенный переход к точной странице со стихом по ссылке из цитат диалога Писаря
    LaunchedEffect(targetLocation, allPages) {
        targetLocation?.let { (bookOrder, chapter, verse) ->
            val pageIdx = allPages.indexOfFirst {
                it.bookOrder == bookOrder && it.chapter == chapter && verse in it.startVerse..it.endVerse
            }.let { idx ->
                if (idx >= 0) idx else allPages.indexOfFirst { it.bookOrder == bookOrder && it.chapter == chapter }
            }

            if (pageIdx >= 0) {
                val pageToOpen = 2 + pageIdx
                pagerState.scrollToPage(pageToOpen)
                onTargetLocationConsumed()
            }
        }
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
                            allPages = allPages,
                            onChapterSelected = { bookOrder, chapter ->
                                val targetPage = allPages.firstOrNull {
                                    it.bookOrder == bookOrder && it.chapter == chapter
                                }
                                if (targetPage != null) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(2 + (targetPage.pageNumber - 1))
                                    }
                                }
                            }
                        )
                    }
                    else -> {
                        // Страницы 2+: Настоящие печатные страницы Писания без вертикального скролла
                        val pageIdxInAllPages = pageIndex - 2
                        if (pageIdxInAllPages in allPages.indices) {
                            val pageRef = allPages[pageIdxInAllPages]
                            BookChapterPage(
                                repository = repository,
                                pageRef = pageRef,
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
    allPages: List<BookPageRef>,
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
                        color = LocalLogosColors.current.placeholderColor,
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
                    focusedContainerColor = LocalLogosColors.current.pageBgDarker,
                    unfocusedContainerColor = LocalLogosColors.current.pageBgDarker,
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
                            isExpanded = expandedOldTestament || searchQuery.isNotBlank(),
                            onToggle = { expandedOldTestament = !expandedOldTestament }
                        )
                    }

                    if (expandedOldTestament || searchQuery.isNotBlank()) {
                        items(filteredOldBooks, key = { it.bookOrder }) { book ->
                            BookTocRow(
                                book = book,
                                allPages = allPages,
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
                            isExpanded = expandedNewTestament || searchQuery.isNotBlank(),
                            onToggle = { expandedNewTestament = !expandedNewTestament }
                        )
                    }

                    if (expandedNewTestament || searchQuery.isNotBlank()) {
                        items(filteredNewBooks, key = { it.bookOrder }) { book ->
                            BookTocRow(
                                book = book,
                                allPages = allPages,
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
 * Строка верхнего уровня: Ветхий Завет / Новый Завет в стиле разделов книги (без номеров страниц)
 */
@Composable
private fun TestamentHeaderRow(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isExpanded) DeepLeather else Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isExpanded) AccentGold else InkGold.copy(alpha = 0.6f)
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
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = InkDark,
                letterSpacing = 1.5.sp,
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(
            color = InkGold.copy(alpha = 0.35f),
            thickness = 1.dp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

/**
 * Строка книги с номером страницы и раскрывающимся списком глав (прям как родитель)
 */
@Composable
private fun BookTocRow(
    book: BookItem,
    allPages: List<BookPageRef>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onChapterSelected: (bookOrder: Int, chapter: Int) -> Unit
) {
    val bookStartPage = remember(book, allPages) {
        allPages.firstOrNull { it.bookOrder == book.bookOrder }?.pageNumber ?: 1
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
                    val chPage = remember(book.bookOrder, ch, allPages) {
                        allPages.firstOrNull { it.bookOrder == book.bookOrder && it.chapter == ch }?.pageNumber
                            ?: (bookStartPage + ch - 1)
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
 * Страницы 2+: Страница чтения стихов Писания без вертикального скролла
 */
@Composable
private fun BookChapterPage(
    repository: BibleReaderRepository,
    pageRef: BookPageRef,
    onBackToContents: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    hasPrevious: Boolean,
    hasNext: Boolean
) {
    val segments = pageRef.segments.ifEmpty {
        remember(pageRef) {
            repository.getPageVerses(pageRef.bookOrder, pageRef.chapter, pageRef.startVerse, pageRef.endVerse).map {
                com.biblerag.model.PageVerseSegment(verse = it.verse, text = it.text)
            }
        }
    }

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
                        text = pageRef.bookNameRu.uppercase(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = InkDark,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = if (pageRef.isFirstPageOfChapter) "ГЛАВА ${pageRef.chapter}" else "ГЛАВА ${pageRef.chapter} (ст. ${pageRef.startVerse}–${pageRef.endVerse})",
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

            // Содержимое стихов страницы (БЕЗ вертикального скролла, настоящий книжный разворот!)
            if (segments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Текст страницы не найден",
                        fontFamily = FontFamily.Serif,
                        color = Color.Gray
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 8.dp)
                ) {
                    if (pageRef.isFirstPageOfChapter) {
                        // Декоративный заголовок первой страницы главы
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "❖",
                                    color = AccentGold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "ГЛАВА ${pageRef.chapter}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
                                    color = InkDark,
                                    letterSpacing = 2.sp
                                )
                            }
                        }
                    } else {
                        // Элегантный разделитель продолжения главы
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "❖  ст. ${pageRef.startVerse}–${pageRef.endVerse}  ❖",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Serif,
                                color = InkGold.copy(alpha = 0.7f),
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Отображение стихов страницы с плавной стыковкой продолжений
                    segments.forEach { segment ->
                        val annotatedVerse = buildAnnotatedString {
                            if (!segment.isContinuation) {
                                withStyle(
                                    SpanStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DropCapColor,
                                        baselineShift = BaselineShift.Superscript
                                    )
                                ) {
                                    append("${segment.verse} ")
                                }
                            }
                            append(segment.text)
                        }

                        Text(
                            text = annotatedVerse,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            fontFamily = FontFamily.Serif,
                            color = InkDark,
                            modifier = Modifier.padding(bottom = 5.dp)
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
                    text = "~ ${pageRef.pageNumber} ~",
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
