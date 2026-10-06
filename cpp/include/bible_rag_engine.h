#pragma once

#include "simd_math.h"
#include "rrf_ranker.h"
#include <string>
#include <vector>
#include <memory>

// Forward declaration для sqlite3
struct sqlite3;
struct sqlite3_stmt;

namespace biblerag {

struct BibleSearchResult {
    int32_t chunk_id;
    std::string canonical_ref;
    std::string testament;
    std::string category;
    std::string text;
    float rrf_score;
    int dense_rank;
    float dense_score;
    int sparse_rank;
    float sparse_score;
};

class BibleRagEngine {
public:
    BibleRagEngine();
    ~BibleRagEngine();

    // Запрет копирования
    BibleRagEngine(const BibleRagEngine&) = delete;
    BibleRagEngine& operator=(const BibleRagEngine&) = delete;

    /**
     * Инициализация движка: открытие SQLite БД и предзагрузка векторов.
     * @param db_path Путь к файлу bible.db
     * @return true в случае успеха, false при ошибке
     */
    bool init(const std::string& db_path);

    /**
     * Выполняет гибридный поиск (Dense SIMD + Sparse FTS5 + RRF)
     * @param query_embedding Нормализованный вектор запроса (размерности vector_dim)
     * @param query_text Текст поискового запроса для полнотекстового поиска FTS5
     * @param top_k Количество возвращаемых результатов
     */
    std::vector<BibleSearchResult> hybrid_search(
        const float* query_embedding,
        const std::string& query_text,
        int top_k = 3
    );

    /**
     * Возвращает размерность векторов (по умолчанию 312)
     */
    int get_vector_dim() const { return vector_dim_; }

    /**
     * Возвращает количество загруженных чанков
     */
    int get_total_chunks() const { return static_cast<int>(chunk_ids_.size()); }

    /**
     * Освобождает ресурсы базы данных и память
     */
    void close();

private:
    sqlite3* db_ = nullptr;
    sqlite3_stmt* stmt_fts_ = nullptr;
    sqlite3_stmt* stmt_chunk_lookup_ = nullptr;

    int vector_dim_ = 312;
    std::vector<int32_t> chunk_ids_;
    std::vector<float> embeddings_buffer_; // плоский непрерывный массив [N * 312]

    bool load_vectors_from_db();
    bool prepare_statements();
    std::vector<ScoredItem> search_fts5(const std::string& query_text, int top_k);
};

} // namespace biblerag
