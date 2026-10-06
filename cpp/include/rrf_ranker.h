#pragma once

#include "simd_math.h"
#include <vector>
#include <cstdint>

namespace biblerag {

struct RankedResult {
    int32_t chunk_id;
    float rrf_score;
    int dense_rank;       // 0, если не вошел в dense top
    float dense_score;
    int sparse_rank;      // 0, если не вошел в sparse top
    float sparse_score;

    bool operator>(const RankedResult& other) const {
        return rrf_score > other.rrf_score;
    }
};

/**
 * Объединение Dense и Sparse списков через Reciprocal Rank Fusion:
 * Score(d) = 1 / (k_const + rank_dense) + 1 / (k_const + rank_sparse)
 */
std::vector<RankedResult> fuse_ranks(
    const std::vector<ScoredItem>& dense_results,
    const std::vector<ScoredItem>& sparse_results,
    int top_k = 5,
    float k_const = 60.0f
);

} // namespace biblerag
