package com.biblerag.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.biblerag.R
import com.biblerag.core.NativeSearchResult

import com.biblerag.ui.theme.LocalLogosColors

// Благородная антикварная палитра для диалога с Писарем с поддержкой ночного режима
private val ChatBg: Color @Composable get() = LocalLogosColors.current.chatBg
private val DeepLeather: Color @Composable get() = LocalLogosColors.current.deepLeather
private val AccentGold: Color @Composable get() = LocalLogosColors.current.accentGold
private val InkDark: Color @Composable get() = LocalLogosColors.current.inkDark
private val InkGold: Color @Composable get() = LocalLogosColors.current.inkGold
private val ParchmentCard: Color @Composable get() = LocalLogosColors.current.parchmentCard
private val AssistantBubble: Color @Composable get() = LocalLogosColors.current.assistantBubble
private val UserBubble: Color @Composable get() = LocalLogosColors.current.userBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleChatScreen(
    viewModel: ChatViewModel,
    onOpenScripture: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var showScribeBio by remember { mutableStateOf(false) }

    // Автоскролл к самому низу при генерации контента, новых сообщениях или раскрытии цитат
    val lastMessage = state.messages.lastOrNull()
    LaunchedEffect(
        state.messages.size,
        lastMessage?.text,
        lastMessage?.visibleCitationCount,
        lastMessage?.citations?.size,
        state.isGenerating
    ) {
        if (state.messages.isNotEmpty()) {
            if (state.isGenerating) {
                // Во время активной генерации мгновенно удерживаем скролл на самом низу
                listState.scrollToItem(state.messages.lastIndex, scrollOffset = 100000)
            } else {
                // После генерации или раскрытия карточек плавно докручиваем вниз
                listState.animateScrollToItem(state.messages.lastIndex, scrollOffset = 100000)
            }
        }
    }

    if (showScribeBio) {
        ScribeBiographySheet(onDismiss = { showScribeBio = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showScribeBio = true }
                            .padding(vertical = 2.dp, horizontal = 2.dp)
                    ) {
                        // Портрет Писаря Соломона в золотом медальоне
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .border(2.dp, AccentGold, CircleShape)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.solomon_scribe),
                                contentDescription = "Писарь Соломона",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "Писарь Соломона",
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = AccentGold
                            )
                            Text(
                                text = "Хранитель свитков • Офлайн ИИ",
                                fontFamily = FontFamily.Serif,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color(0xFFD4C3A3)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepLeather
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeepLeather)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Быстрые карточки вопросов от мудреца
                if (!state.isGenerating) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        items(state.suggestedQueries) { suggestion ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF2B1D12),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentGold.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable { viewModel.sendQuery(suggestion) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = suggestion,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Serif,
                                        color = Color(0xFFDFD0B8)
                                    )
                                }
                            }
                        }
                    }
                }

                // Поле ввода вопроса в пергаментном оформлении
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.currentInput,
                        onValueChange = viewModel::onInputChanged,
                        placeholder = {
                            Text(
                                "Вопрошай у писца о Писании...",
                                fontFamily = FontFamily.Serif,
                                color = LocalLogosColors.current.placeholderColor,
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3,
                        enabled = !state.isGenerating,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = LocalLogosColors.current.inputContainerBg,
                            unfocusedContainerColor = LocalLogosColors.current.inputContainerBg,
                            disabledContainerColor = LocalLogosColors.current.inputContainerBg.copy(alpha = 0.6f),
                            focusedTextColor = LocalLogosColors.current.inputTextColor,
                            unfocusedTextColor = LocalLogosColors.current.inputTextColor,
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = AccentGold.copy(alpha = 0.5f),
                            cursorColor = InkGold
                        )
                    )

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = { viewModel.sendQuery() },
                        enabled = state.currentInput.isNotBlank() && !state.isGenerating,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (state.currentInput.isNotBlank() && !state.isGenerating)
                                    AccentGold
                                else AccentGold.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                    ) {
                        if (state.isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = DeepLeather,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Отправить",
                                tint = DeepLeather,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ChatBg)
                .padding(innerPadding)
        ) {

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    ScribeMessageItem(
                        message = msg,
                        onLoadMore = viewModel::loadMoreCitations,
                        onCollapse = viewModel::collapseCitations,
                        onOpenScripture = onOpenScripture
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ScribeMessageItem(
    message: Message,
    onLoadMore: (String) -> Unit = {},
    onCollapse: (String) -> Unit = {},
    onOpenScripture: (String) -> Unit = {}
) {
    val isUser = message.sender == Sender.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            // Подпись и статус Писаря
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            ) {
                Text(
                    text = "Елихореф, Писарь",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = InkGold
                )
            }
        }

        // Облачко сообщения
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) UserBubble else AssistantBubble,
            border = if (!isUser) androidx.compose.foundation.BorderStroke(1.dp, AccentGold.copy(alpha = 0.45f)) else null,
            shadowElevation = 3.dp,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                if (message.text.isEmpty() && message.isStreaming) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = AccentGold
                        )
                        Text(
                            text = "Писарь раскрывает древние свитки...",
                            fontFamily = FontFamily.Serif,
                            color = InkDark,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Text(
                        text = message.text,
                        color = if (isUser) Color(0xFFF6EFE6) else InkDark,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontFamily = FontFamily.Serif
                    )
                }
            }
        }

        // Блок найденных контекстных стихов: выходит ТОЛЬКО после того, как полностью завершится печать текста (!message.isStreaming)
        AnimatedVisibility(
            visible = !message.isStreaming && message.citations.isNotEmpty() && message.text.isNotEmpty(),
            enter = fadeIn(tween(500)) + expandVertically(tween(500))
        ) {
            Column {
                val totalCount = message.citations.size
                val visibleCount = message.visibleCitationCount.coerceAtMost(totalCount)
                val visibleCitations = message.citations.take(visibleCount)

                Spacer(Modifier.height(8.dp))

                // Заголовок блока свитков
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = InkGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Свитки Писания: показано $visibleCount из $totalCount",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = InkGold
                    )
                    message.searchTimeMs?.let {
                        Text(
                            text = " • ${it}мс",
                            fontSize = 11.sp,
                            color = Color(0xFF8F7A65)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Карточки стихов с ссылками на главы Библии
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    visibleCitations.forEach { citation ->
                        ScribeScrollCard(
                            citation = citation,
                            onOpenScripture = onOpenScripture
                        )
                    }
                }

                // Кнопки пагинации: «Показать ещё 3» и «Свернуть до 3»
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (totalCount > visibleCount) {
                        val remaining = totalCount - visibleCount
                        val nextStep = remaining.coerceAtMost(3)
                        OutlinedButton(
                            onClick = { onLoadMore(message.id) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = InkDark),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentGold)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = InkGold
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Ещё $nextStep (из $remaining)",
                                fontFamily = FontFamily.Serif,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (visibleCount > 3) {
                        OutlinedButton(
                            onClick = { onCollapse(message.id) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = InkDark),
                            border = androidx.compose.foundation.BorderStroke(1.dp, InkGold.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExpandLess,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = InkGold
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Свернуть",
                                fontFamily = FontFamily.Serif,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Карточка-свиток найденного отрывка Писания со ссылкой на книгу
 */
@Composable
fun ScribeScrollCard(
    citation: NativeSearchResult,
    onOpenScripture: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = ParchmentCard),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentGold.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = citation.canonicalRef,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = InkDark
                    )
                }

                // Ссылка-бейдж для быстрого перехода к чтению главы в ридере
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = AccentGold.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentGold.copy(alpha = 0.6f)),
                    modifier = Modifier.clickable { onOpenScripture(citation.canonicalRef) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Читать ↗",
                            fontFamily = FontFamily.Serif,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = InkDark
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = DeepLeather.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = if (citation.testament.contains("new", ignoreCase = true) || citation.testament.contains("нов", ignoreCase = true))
                            "Новый Завет" else "Ветхий Завет",
                        fontFamily = FontFamily.Serif,
                        fontSize = 10.sp,
                        color = InkGold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.width(4.dp))

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = InkGold,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            // Текст отрывка (свернутый до 2 строк или полный)
            Text(
                text = citation.text,
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = InkDark,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            // Развернутая кнопка перехода при открытой карточке
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onOpenScripture(citation.canonicalRef) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepLeather,
                        contentColor = AccentGold
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Открыть главу в книге Logos",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Роскошная нижняя шторка (ModalBottomSheet) с биографией и описанием Писаря Соломона (Елихорефа)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScribeBiographySheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepLeather,
        contentColor = Color(0xFFE8DEC8),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = AccentGold.copy(alpha = 0.5f))
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Большой портрет в золотом кольце
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .border(3.dp, AccentGold, CircleShape)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.solomon_scribe),
                    contentDescription = "Писарь Соломона",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "ЕЛИХОРЕФ",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = AccentGold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Писарь благословенного царя Соломона",
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
                color = Color(0xFFDFD1B8)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = Color(0xFF2C1E14),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentGold.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "📜 3-я Царств 4:3",
                    fontFamily = FontFamily.Serif,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentGold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(
                color = AccentGold.copy(alpha = 0.3f),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = Color(0x22C5A059),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentGold.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "«И были у царя Соломона князья: ...Елихореф и Ахия, сыновья Сивы, писцы; Иосафат, сын Ахилуда, дееписатель...»\n— 3-я Царств 4:2–3",
                    fontFamily = FontFamily.Serif,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Color(0xFFF1E6D2),
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Елихореф (др.-евр. אֱלִיחֹרֶף — «Бог — его награда») — хранитель царской библиотеки и священных свитков при дворе премудрого царя Соломона в Иерусалиме.\n\n" +
                        "В древнем Израиле писцы почитались наравне с мудрецами: они берегли Закон Моисеев, переписывали притчи Соломона, псалмы Давида и наставляли народ в истине Слова Божьего.\n\n" +
                        "В этом приложении Писарь служит вашим смиренным проводником: он обращается к 31 102 стихам канонической Библии, находя точные цитаты и делясь вечной мудростью Писания.",
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp,
                color = Color(0xFFE8DEC8),
                lineHeight = 22.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGold,
                    contentColor = DeepLeather
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Во славу Божию",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

