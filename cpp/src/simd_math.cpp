#include "simd_math.h"
#include <queue>
#include <algorithm>

#if defined(__ARM_NEON) || defined(__aarch64__)
#include <arm_neon.h>
#elif defined(__AVX2__)
#include <immintrin.h>
#endif

namespace biblerag {

float dot_product(const float* a, const float* b, int size) {
#if defined(__ARM_NEON) || defined(__aarch64__)
    // Оптимизация под ARM NEON (Android arm64-v8a)
    // Развертка 4x: обрабатываем 16 float32 за итерацию (4 такта SIMD)
    float32x4_t sum0 = vdupq_n_f32(0.0f);
    float32x4_t sum1 = vdupq_n_f32(0.0f);
    float32x4_t sum2 = vdupq_n_f32(0.0f);
    float32x4_t sum3 = vdupq_n_f32(0.0f);

    int i = 0;
    int limit16 = size - 15;
    for (; i < limit16; i += 16) {
        sum0 = vmlaq_f32(sum0, vld1q_f32(a + i),      vld1q_f32(b + i));
        sum1 = vmlaq_f32(sum1, vld1q_f32(a + i + 4),  vld1q_f32(b + i + 4));
        sum2 = vmlaq_f32(sum2, vld1q_f32(a + i + 8),  vld1q_f32(b + i + 8));
        sum3 = vmlaq_f32(sum3, vld1q_f32(a + i + 12), vld1q_f32(b + i + 12));
    }

    sum0 = vaddq_f32(sum0, sum1);
    sum2 = vaddq_f32(sum2, sum3);
    sum0 = vaddq_f32(sum0, sum2);

    // Добираем по 4 элемента
    int limit4 = size - 3;
    for (; i < limit4; i += 4) {
        sum0 = vmlaq_f32(sum0, vld1q_f32(a + i), vld1q_f32(b + i));
    }

    float total = vaddvq_f32(sum0);

    // Оставшиеся элементы (для размерности 312 остатка нет, 312 % 4 == 0)
    for (; i < size; ++i) {
        total += a[i] * b[i];
    }
    return total;

#elif defined(__AVX2__)
    // Оптимизация под x86_64 с AVX2/FMA
    __m256 sum0 = _mm256_setzero_ps();
    __m256 sum1 = _mm256_setzero_ps();

    int i = 0;
    int limit16 = size - 15;
    for (; i < limit16; i += 16) {
        sum0 = _mm256_fmadd_ps(_mm256_loadu_ps(a + i),     _mm256_loadu_ps(b + i),     sum0);
        sum1 = _mm256_fmadd_ps(_mm256_loadu_ps(a + i + 8), _mm256_loadu_ps(b + i + 8), sum1);
    }
    sum0 = _mm256_add_ps(sum0, sum1);

    alignas(32) float buffer[8];
    _mm256_store_ps(buffer, sum0);
    float total = buffer[0] + buffer[1] + buffer[2] + buffer[3] +
                  buffer[4] + buffer[5] + buffer[6] + buffer[7];

    for (; i < size; ++i) {
        total += a[i] * b[i];
    }
    return total;

#else
    // Переносимый fallback с разверткой цикла 4x
    float sum0 = 0.0f;
    float sum1 = 0.0f;
    float sum2 = 0.0f;
    float sum3 = 0.0f;

    int i = 0;
    int limit4 = size - 3;
    for (; i < limit4; i += 4) {
        sum0 += a[i]     * b[i];
        sum1 += a[i + 1] * b[i + 1];
        sum2 += a[i + 2] * b[i + 2];
        sum3 += a[i + 3] * b[i + 3];
    }
    float total = (sum0 + sum1) + (sum2 + sum3);
    for (; i < size; ++i) {
        total += a[i] * b[i];
    }
    return total;
#endif
}

std::vector<ScoredItem> find_top_k(
    const float* matrix,
    const int32_t* vector_ids,
    int num_vectors,
    int dim,
    const float* query,
    int top_k
) {
    if (top_k <= 0 || num_vectors <= 0) {
        return {};
    }

    // Min-heap для поддержания K наибольших элементов
    std::priority_queue<ScoredItem, std::vector<ScoredItem>, std::greater<ScoredItem>> min_heap;

    for (int i = 0; i < num_vectors; ++i) {
        const float* vec = matrix + (static_cast<size_t>(i) * dim);
        float score = dot_product(vec, query, dim);
        int32_t id = vector_ids ? vector_ids[i] : i;

        if (static_cast<int>(min_heap.size()) < top_k) {
            min_heap.push({id, score});
        } else if (score > min_heap.top().score) {
            min_heap.pop();
            min_heap.push({id, score});
        }
    }

    // Выгружаем из heap и сортируем по убыванию скора
    std::vector<ScoredItem> results;
    results.reserve(min_heap.size());
    while (!min_heap.empty()) {
        results.push_back(min_heap.top());
        min_heap.pop();
    }
    std::reverse(results.begin(), results.end());
    return results;
}

} // namespace biblerag
