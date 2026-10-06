#pragma once

#include <vector>
#include <cstdint>
#include <cstddef>

namespace biblerag {

struct ScoredItem {
    int32_t id;
    float score;

    bool operator>(const ScoredItem& other) const {
        return score > other.score;
    }
    bool operator<(const ScoredItem& other) const {
        return score < other.score;
    }
};

/**
 * Быстрое скалярное произведение двух нормализованных векторов.
 * На Android ARM64 автоматически использует ARM NEON SIMD.
 */
float dot_product(const float* a, const float* b, int size);

/**
 * Поиск Top-K наиболее близких векторов по всей матрице за O(N log K).
 * matrix: непрерывный буфер float32 размера [num_vectors * dim]
 * query: вектор запроса длины [dim]
 * vector_ids: массив идентификаторов чанков длины [num_vectors]
 */
std::vector<ScoredItem> find_top_k(
    const float* matrix,
    const int32_t* vector_ids,
    int num_vectors,
    int dim,
    const float* query,
    int top_k
);

} // namespace biblerag
