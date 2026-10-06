package com.biblerag.core

/**
 * Результат гибридного поиска, возвращаемый из нативного C++ ядра.
 */
data class NativeSearchResult(
    val chunkId: Int,
    val canonicalRef: String,
    val testament: String,
    val category: String,
    val text: String,
    val rrfScore: Float,
    val denseRank: Int,
    val denseScore: Float,
    val sparseRank: Int,
    val sparseScore: Float
)
