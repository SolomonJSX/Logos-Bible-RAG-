package com.biblerag.model

data class BookItem(
    val bookOrder: Int,
    val nameRu: String,
    val nameEn: String,
    val testament: String,
    val category: String,
    val totalChapters: Int
) {
    val shortName: String
        get() = when (nameRu) {
            "Бытие" -> "Быт"
            "Исход" -> "Исх"
            "Левит" -> "Лев"
            "Числа" -> "Чис"
            "Второзаконие" -> "Втор"
            "От Матфея" -> "Мф"
            "От Марка" -> "Мк"
            "От Луки" -> "Лк"
            "От Иоанна" -> "Ин"
            "Деяния" -> "Деян"
            "Римлянам" -> "Рим"
            "Откровение" -> "Откр"
            "Псалтирь" -> "Пс"
            "Притчи" -> "Притч"
            else -> nameRu.take(4)
        }

    val isOldTestament: Boolean
        get() = testament.equals("old", ignoreCase = true) || testament.contains("ветх", ignoreCase = true)

    val isNewTestament: Boolean
        get() = testament.equals("new", ignoreCase = true) || testament.contains("нов", ignoreCase = true)

    val categoryRu: String
        get() = when (category.lowercase()) {
            "law" -> "Закон (Пятикнижие)"
            "history" -> "Исторические"
            "wisdom" -> "Учительные"
            "prophets" -> "Пророческие"
            "gospels" -> "Евангелия"
            "acts" -> "Деяния"
            "epistles" -> "Послания"
            "apocalypse" -> "Откровение"
            else -> category
        }
}

data class VerseItem(
    val id: Int,
    val bookOrder: Int,
    val chapter: Int,
    val verse: Int,
    val text: String
)

data class ChapterRef(
    val bookOrder: Int,
    val bookNameRu: String,
    val chapter: Int,
    val totalChapters: Int
)

data class PageVerseSegment(
    val verse: Int,
    val text: String,
    val isContinuation: Boolean = false,
    val continuesOnNextPage: Boolean = false
)

data class BookPageRef(
    val pageNumber: Int,
    val bookOrder: Int,
    val bookNameRu: String,
    val chapter: Int,
    val totalChaptersInBook: Int,
    val startVerse: Int,
    val endVerse: Int,
    val isFirstPageOfChapter: Boolean,
    val segments: List<PageVerseSegment> = emptyList()
)