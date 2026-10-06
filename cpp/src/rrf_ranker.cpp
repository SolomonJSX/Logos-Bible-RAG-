#include "rrf_ranker.h"
#include <unordered_map>
#include <algorithm>

namespace biblerag {

std::vector<RankedResult> fuse_ranks(
    const std::vector<ScoredItem>& dense_results,
    const std::vector<ScoredItem>& sparse_results,
    int top_k,
    float k_const
) {
    std::unordered_map<int32_t, RankedResult> merged_map;
    merged_map.reserve(dense_results.size() + sparse_results.size());

    // 1. Добавляем Dense результаты
    for (size_t i = 0; i < dense_results.size(); ++i) {
        int rank = static_cast<int>(i) + 1;
        int32_t id = dense_results[i].id;
        float score = dense_results[i].score;
        float rrf = 1.0f / (k_const + static_cast<float>(rank));

        RankedResult item;
        item.chunk_id = id;
        item.rrf_score = rrf;
        item.dense_rank = rank;
        item.dense_score = score;
        item.sparse_rank = 0;
        item.sparse_score = 0.0f;
        merged_map[id] = item;
    }

    // 2. Добавляем Sparse (FTS5) результаты
    for (size_t i = 0; i < sparse_results.size(); ++i) {
        int rank = static_cast<int>(i) + 1;
        int32_t id = sparse_results[i].id;
        float score = sparse_results[i].score;
        float rrf = 1.0f / (k_const + static_cast<float>(rank));

        auto it = merged_map.find(id);
        if (it != merged_map.end()) {
            it->second.rrf_score += rrf;
            it->second.sparse_rank = rank;
            it->second.sparse_score = score;
        } else {
            RankedResult item;
            item.chunk_id = id;
            item.rrf_score = rrf;
            item.dense_rank = 0;
            item.dense_score = 0.0f;
            item.sparse_rank = rank;
            item.sparse_score = score;
            merged_map[id] = item;
        }
    }

    // 3. Собираем в вектор и сортируем по убыванию rrf_score
    std::vector<RankedResult> combined;
    combined.reserve(merged_map.size());
    for (const auto& pair : merged_map) {
        combined.push_back(pair.second);
    }

    int result_count = std::min(top_k, static_cast<int>(combined.size()));
    std::partial_sort(
        combined.begin(),
        combined.begin() + result_count,
        combined.end(),
        std::greater<RankedResult>()
    );

    combined.resize(result_count);
    return combined;
}

} // namespace biblerag
