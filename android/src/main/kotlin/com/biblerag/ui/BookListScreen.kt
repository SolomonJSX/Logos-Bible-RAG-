package com.biblerag.ui
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biblerag.R
import com.biblerag.model.BookItem

val DarkLeather = Color(0xFF1E140C)
val WarmParchment = Color(0xFFF4ECE1)
val AgedGold = Color(0xFFC5A059)
val DeepBrown = Color(0xFF3E2723)
val CardParchment = Color(0xFFFAF6F0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    books: List<BookItem>,
    onChapterSelected: (book: BookItem, chapter: Int) -> Unit
) {
    var selectedTestament by remember { mutableStateOf("new") } // "new" по умолчанию
    var selectedBookForChapters by remember { mutableStateOf<BookItem?>(null) }

    val filteredBooks = remember(books, selectedTestament) {
        books.filter { it.testament.lowercase().contains(selectedTestament) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.logos_splash),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.25f
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkLeather.copy(alpha = 0.95f))
                        .padding(top = 16.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Священное Писание",
                        color = AgedGold,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))

                    // Переключатель Заветов
                    TabRow(
                        selectedTabIndex = if (selectedTestament == "new") 0 else 1,
                        containerColor = Color.Transparent,
                        contentColor = AgedGold,
                        divider = {}
                    ) {
                        Tab(
                            selected = selectedTestament == "new",
                            onClick = { selectedTestament = "new" },
                            text = { Text("Новый Завет", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                        )
                        Tab(
                            selected = selectedTestament == "old",
                            onClick = { selectedTestament = "old" },
                            text = { Text("Ветхий Завет", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                        )
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredBooks, key = { it.bookOrder }) { book ->
                    BookCard(book = book, onClick = { selectedBookForChapters = book })
                }
            }
        }

        // Шторка выбора главы
        selectedBookForChapters?.let { book ->
            ModalBottomSheet(
                onDismissRequest = { selectedBookForChapters = null },
                containerColor = CardParchment,
                scrimColor = Color.Black.copy(alpha = 0.6f)
            ) {
                ChapterSelectionSheet(
                    book = book,
                    onChapterClick = { chapter ->
                        selectedBookForChapters = null
                        onChapterSelected(book, chapter)
                    }
                )
            }
        }
    }
}

@Composable
fun BookCard(book: BookItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.dp, AgedGold.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardParchment.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = book.nameRu,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepBrown
                )
                Text(
                    text = book.category,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AgedGold.copy(alpha = 0.15f))
                    .border(0.5.dp, AgedGold, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${book.totalChapters} гл.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = DeepBrown
                )
            }
        }
    }
}

@Composable
fun ChapterSelectionSheet(book: BookItem, onChapterClick: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = book.nameRu,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = DeepBrown
        )
        Text(
            text = "Выберите главу",
            fontSize = 13.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 52.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)
        ) {
            items((1..book.totalChapters).toList()) { chapter ->
                Surface(
                    modifier = Modifier
                        .size(52.dp)
                        .clickable { onChapterClick(chapter) },
                    shape = RoundedCornerShape(10.dp),
                    color = DarkLeather,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AgedGold)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = chapter.toString(),
                            color = AgedGold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}