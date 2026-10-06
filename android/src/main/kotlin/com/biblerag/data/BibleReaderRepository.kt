package com.biblerag.data

import android.database.sqlite.SQLiteDatabase
import com.biblerag.model.BookItem
import com.biblerag.model.ChapterRef
import com.biblerag.model.VerseItem

class BibleReaderRepository(private val dbPath: String) {

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