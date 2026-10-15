package com.biblerag.data

import android.database.sqlite.SQLiteDatabase
import com.biblerag.model.BookItem
import com.biblerag.model.BookPageRef
import com.biblerag.model.ChapterRef
import com.biblerag.model.VerseItem

class BibleReaderRepository(private val dbPath: String) {

    private var cachedPages: List<BookPageRef>? = null

    fun getBooks(): List<BookItem> {
        val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
        val list = mutableListOf<BookItem>()

        val query = """
            SELECT book_order, book_name_ru, book_name_en, testament, category, MAX(chapter) as max_chapter
            FROM verses
            GROUP BY book_order
            ORDER BY book_order ASC
        """.trimIndent()

        db.rawQuery(query, null).use { cursor ->
            val orderIdx = cursor.getColumnIndexOrThrow("book_order")
            val ruIdx = cursor.getColumnIndexOrThrow("book_name_ru")
            val enIdx = cursor.getColumnIndexOrThrow("book_name_en")
            val testIdx = cursor.getColumnIndexOrThrow("testament")
            val catIdx = cursor.getColumnIndexOrThrow("category")
            val maxChapIdx = cursor.getColumnIndexOrThrow("max_chapter")

            while (cursor.moveToNext()) {
                list.add(
                    BookItem(
                        bookOrder = cursor.getInt(orderIdx),
                        nameRu = cursor.getString(ruIdx),
                        nameEn = cursor.getString(enIdx),
                        testament = cursor.getString(testIdx),
                        category = cursor.getString(catIdx),
                        totalChapters = cursor.getInt(maxChapIdx)
                    )
                )
            }
        }
        db.close()
        return list
    }

    fun getAllChapters(books: List<BookItem>): List<ChapterRef> {
        val chapters = ArrayList<ChapterRef>(1200)
        for (b in books) {
            for (c in 1..b.totalChapters) {
                chapters.add(ChapterRef(b.bookOrder, b.nameRu, c, b.totalChapters))
            }
        }
        return chapters
    }

    /**
     * Формирует каноническую разбивку всей Библии на физические страницы книги.
     * Каждая страница содержит законченный блок стихов или предложений, который целиком помещается
     * на экране смартфона без необходимости вертикальной прокрутки.
     * Если стих не помещается целиком на странице, он разделяется по знакам препинания,
     * и оставшаяся часть предложения плавно продолжается на следующей странице без обрыва текста.
     */
    fun getAllPages(books: List<BookItem>): List<BookPageRef> {
        cachedPages?.let { return it }

        val booksMap = books.associateBy { it.bookOrder }
        val pages = ArrayList<BookPageRef>(6500)

        val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
        val query = """
            SELECT book_order, chapter, verse, text
            FROM verses
            ORDER BY book_order ASC, chapter ASC, verse ASC
        """.trimIndent()

        data class VerseRow(val bookOrder: Int, val chapter: Int, val verse: Int, val text: String)
        val allVerses = ArrayList<VerseRow>(31200)

        db.rawQuery(query, null).use { cursor ->
            val bIdx = cursor.getColumnIndexOrThrow("book_order")
            val cIdx = cursor.getColumnIndexOrThrow("chapter")
            val vIdx = cursor.getColumnIndexOrThrow("verse")
            val tIdx = cursor.getColumnIndexOrThrow("text")

            while (cursor.moveToNext()) {
                allVerses.add(
                    VerseRow(
                        bookOrder = cursor.getInt(bIdx),
                        chapter = cursor.getInt(cIdx),
                        verse = cursor.getInt(vIdx),
                        text = cursor.getString(tIdx)
                    )
                )
            }
        }
        db.close()

        val PAGE_1_MAX_CHARS = 480
        val PAGE_N_MAX_CHARS = 600

        fun splitText(text: String, budget: Int): Pair<String, String> {
            if (text.length <= budget) return Pair(text, "")
            val punctuation = listOf(". ", "? ", "! ", "; ", ": ", ", ", " — ")
            var bestPos = -1
            for (p in punctuation) {
                val searchStart = (budget - p.length).coerceAtMost(text.length - 1)
                if (searchStart >= 25) {
                    val pos = text.lastIndexOf(p, searchStart)
                    if (pos >= 25) {
                        bestPos = pos + p.length
                        break
                    }
                }
            }
            if (bestPos == -1) {
                val searchStart = (budget - 1).coerceAtMost(text.length - 1)
                if (searchStart >= 25) {
                    val pos = text.lastIndexOf(' ', searchStart)
                    if (pos >= 25) {
                        bestPos = pos + 1
                    }
                }
            }
            if (bestPos == -1 || (text.length - bestPos) < 15) {
                return Pair(text, "")
            }
            return Pair(text.substring(0, bestPos).trim(), text.substring(bestPos).trim())
        }

        var chapterStart = 0
        while (chapterStart < allVerses.size) {
            val first = allVerses[chapterStart]
            var chapterEnd = chapterStart + 1
            while (chapterEnd < allVerses.size &&
                allVerses[chapterEnd].bookOrder == first.bookOrder &&
                allVerses[chapterEnd].chapter == first.chapter
            ) {
                chapterEnd++
            }

            val chapterVerses = allVerses.subList(chapterStart, chapterEnd)
            val book = booksMap[first.bookOrder]

            var curSegs = mutableListOf<com.biblerag.model.PageVerseSegment>()
            var curChars = 0
            var isFirstPageOfChapter = true

            data class PendingWork(val verse: Int, var text: String, var isContinuation: Boolean)
            val workQueue = ArrayDeque<PendingWork>()
            for (v in chapterVerses) {
                workQueue.add(PendingWork(v.verse, v.text, false))
            }

            fun emitCurrentPage() {
                if (curSegs.isNotEmpty()) {
                    val pageNumber = pages.size + 1
                    pages.add(
                        BookPageRef(
                            pageNumber = pageNumber,
                            bookOrder = first.bookOrder,
                            bookNameRu = book?.nameRu ?: "",
                            chapter = first.chapter,
                            totalChaptersInBook = book?.totalChapters ?: 1,
                            startVerse = curSegs.first().verse,
                            endVerse = curSegs.last().verse,
                            isFirstPageOfChapter = isFirstPageOfChapter,
                            segments = curSegs
                        )
                    )
                    curSegs = mutableListOf()
                    curChars = 0
                    isFirstPageOfChapter = false
                }
            }

            while (workQueue.isNotEmpty()) {
                val item = workQueue.removeFirst()
                val limit = if (isFirstPageOfChapter) PAGE_1_MAX_CHARS else PAGE_N_MAX_CHARS
                val remainingSpace = limit - curChars

                if (curChars == 0) {
                    if (item.text.length <= limit) {
                        curSegs.add(
                            com.biblerag.model.PageVerseSegment(
                                verse = item.verse,
                                text = item.text,
                                isContinuation = item.isContinuation,
                                continuesOnNextPage = false
                            )
                        )
                        curChars += item.text.length
                    } else {
                        val (p1, p2) = splitText(item.text, limit)
                        if (p2.isNotEmpty()) {
                            curSegs.add(
                                com.biblerag.model.PageVerseSegment(
                                    verse = item.verse,
                                    text = p1,
                                    isContinuation = item.isContinuation,
                                    continuesOnNextPage = true
                                )
                            )
                            emitCurrentPage()
                            workQueue.addFirst(PendingWork(item.verse, p2, true))
                        } else {
                            curSegs.add(
                                com.biblerag.model.PageVerseSegment(
                                    verse = item.verse,
                                    text = item.text,
                                    isContinuation = item.isContinuation,
                                    continuesOnNextPage = false
                                )
                            )
                            emitCurrentPage()
                        }
                    }
                } else {
                    if (curChars + item.text.length <= limit) {
                        curSegs.add(
                            com.biblerag.model.PageVerseSegment(
                                verse = item.verse,
                                text = item.text,
                                isContinuation = item.isContinuation,
                                continuesOnNextPage = false
                            )
                        )
                        curChars += item.text.length
                    } else {
                        if (remainingSpace >= 70) {
                            val (p1, p2) = splitText(item.text, remainingSpace)
                            if (p2.isNotEmpty()) {
                                curSegs.add(
                                    com.biblerag.model.PageVerseSegment(
                                        verse = item.verse,
                                        text = p1,
                                        isContinuation = item.isContinuation,
                                        continuesOnNextPage = true
                                    )
                                )
                                emitCurrentPage()
                                workQueue.addFirst(PendingWork(item.verse, p2, true))
                                continue
                            }
                        }
                        emitCurrentPage()
                        workQueue.addFirst(item)
                    }
                }
            }
            emitCurrentPage()

            chapterStart = chapterEnd
        }

        cachedPages = pages
        return pages
    }

    /**
     * Загружает стихи для конкретной страницы книги Logos
     */
    fun getPageVerses(bookOrder: Int, chapter: Int, startVerse: Int, endVerse: Int): List<VerseItem> {
        val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
        val list = mutableListOf<VerseItem>()
        val query = """
            SELECT id, book_order, chapter, verse, text
            FROM verses
            WHERE book_order = ? AND chapter = ? AND verse >= ? AND verse <= ?
            ORDER BY verse ASC
        """.trimIndent()

        db.rawQuery(
            query,
            arrayOf(bookOrder.toString(), chapter.toString(), startVerse.toString(), endVerse.toString())
        ).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("id")
            val orderIdx = cursor.getColumnIndexOrThrow("book_order")
            val chIdx = cursor.getColumnIndexOrThrow("chapter")
            val vIdx = cursor.getColumnIndexOrThrow("verse")
            val textIdx = cursor.getColumnIndexOrThrow("text")

            while (cursor.moveToNext()) {
                list.add(
                    VerseItem(
                        id = cursor.getInt(idIdx),
                        bookOrder = cursor.getInt(orderIdx),
                        chapter = cursor.getInt(chIdx),
                        verse = cursor.getInt(vIdx),
                        text = cursor.getString(textIdx)
                    )
                )
            }
        }
        db.close()
        return list
    }

    fun getChapterVerses(bookOrder: Int, chapter: Int): List<VerseItem> {
        val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
        val list = mutableListOf<VerseItem>()
        val query = """
            SELECT id, book_order, chapter, verse, text
            FROM verses
            WHERE book_order = ? AND chapter = ?
            ORDER BY verse ASC
        """.trimIndent()

        db.rawQuery(query, arrayOf(bookOrder.toString(), chapter.toString())).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("id")
            val orderIdx = cursor.getColumnIndexOrThrow("book_order")
            val chIdx = cursor.getColumnIndexOrThrow("chapter")
            val vIdx = cursor.getColumnIndexOrThrow("verse")
            val textIdx = cursor.getColumnIndexOrThrow("text")

            while (cursor.moveToNext()) {
                list.add(
                    VerseItem(
                        id = cursor.getInt(idIdx),
                        bookOrder = cursor.getInt(orderIdx),
                        chapter = cursor.getInt(chIdx),
                        verse = cursor.getInt(vIdx),
                        text = cursor.getString(textIdx)
                    )
                )
            }
        }
        db.close()
        return list
    }
}