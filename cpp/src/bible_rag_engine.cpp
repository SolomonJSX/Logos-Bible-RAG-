#include "bible_rag_engine.h"
#include <sqlite3.h>
#include <iostream>
#include <cstring>
#include <sstream>
#include <unordered_set>

namespace biblerag {

static std::string to_lower_utf8(const std::string& str) {
    std::string result;
    result.reserve(str.size());
    for (size_t i = 0; i < str.size(); ++i) {
        unsigned char c1 = static_cast<unsigned char>(str[i]);
        if (c1 == 0xD0 && i + 1 < str.size()) {
            unsigned char c2 = static_cast<unsigned char>(str[i + 1]);
            if (c2 >= 0x90 && c2 <= 0x9F) {
                result += static_cast<char>(0xD0);
                result += static_cast<char>(c2 + 0x20);
                ++i;
                continue;
            } else if (c2 >= 0xA0 && c2 <= 0xAF) {
                result += static_cast<char>(0xD1);
                result += static_cast<char>(c2 - 0x20);
                ++i;
                continue;
            } else if (c2 == 0x81) {
                result += static_cast<char>(0xD1);
                result += static_cast<char>(0x91);
                ++i;
                continue;
            }
        } else if (c1 >= 'A' && c1 <= 'Z') {
            result += static_cast<char>(c1 + 32);
            continue;
        }
        result += str[i];
    }
    return result;
}

static const std::unordered_set<std::string> BIBLE_STOP_WORDS = {
    // Вопросительные и указательные местоимения
    "кто", "что", "как", "где", "куда", "откуда", "почему", "зачем", "когда", "какой", "какая", "какое", "какие",
    "такой", "такая", "такое", "такие", "этот", "эта", "это", "эти", "тот", "та", "то", "те",
    // Частицы и союзы
    "ли", "не", "ни", "бы", "же", "то", "да", "но", "или", "ибо", "ведь", "вот", "вон", "уже", "еще", "ещё", "лишь",
    "если", "чтобы", "дабы", "тоже", "также", "даже", "хотя", "хоть", "как", "так",
    // Предлоги
    "для", "при", "под", "над", "без", "про", "через", "около", "из", "до", "от", "по", "со", "ко", "во", "на", "за",
    "об", "обо", "между", "среди", "ради", "сквозь", "вдоль", "вместо", "после", "пред", "перед", "возле", "вокруг",
    // Местоимения
    "все", "всё", "весь", "вся", "всех", "всем", "всеми", "всего",
    "его", "ему", "него", "нему", "ним", "ней", "нею", "них", "ими", "их", "ее", "её",
    "мой", "моя", "мое", "мои", "твой", "твоя", "твое", "твои",
    "наш", "наша", "наше", "наши", "ваш", "ваша", "ваше", "ваши",
    "себя", "себе", "собой",
    // Глаголы бытия
    "был", "была", "было", "были", "быть", "есть", "будет", "будут",
    // Мета-слова запросов
    "говорится", "сказано", "написано", "сколько", "библия", "библии", "писании", "писания",
    "расскажи", "покажи", "найди", "стих", "стихи", "глава", "текст"
};

// Простой стеммер для русского языка (отсекает падежные окончания для FTS prefix match)
static std::string stem_russian_term(const std::string& term) {
    // В UTF-8 кириллица занимает 2 байта на букву.
    // Если слово короче 4 букв (8 байт), не урезаем.
    if (term.length() < 8) return term;

    // Специфические частые окончания
    // 2 буквы окончания = 4 байта
    if (term.length() >= 8) {
        std::string end2 = term.substr(term.length() - 4);
        if (end2 == "ами" || end2 == "ями" || end2 == "ого" || end2 == "его" ||
            end2 == "ому" || end2 == "ему" || end2 == "ыми" || end2 == "ими") {
            return term.substr(0, term.length() - 6);
        }
        if (end2 == "ом" || end2 == "ем" || end2 == "ой" || end2 == "ей" ||
            end2 == "ах" || end2 == "ях" || end2 == "ам" || end2 == "ям" ||
            end2 == "ов" || end2 == "ев" || end2 == "ия" || end2 == "ие" ||
            end2 == "ий" || end2 == "ый" || end2 == "ой" || end2 == "ая" || end2 == "яя") {
            return term.substr(0, term.length() - 4);
        }
    }
    // 1 буква окончания = 2 байта (а, я, о, е, ы, и, у, ю)
    if (term.length() >= 6) {
        std::string end1 = term.substr(term.length() - 2);
        if (end1 == "а" || end1 == "я" || end1 == "о" || end1 == "е" ||
            end1 == "ы" || end1 == "и" || end1 == "у" || end1 == "ю") {
            return term.substr(0, term.length() - 2);
        }
    }
    return term;
}

BibleRagEngine::BibleRagEngine() {}

BibleRagEngine::~BibleRagEngine() {
    close();
}

bool BibleRagEngine::init(const std::string& db_path) {
    close();

    int rc = sqlite3_open_v2(
        db_path.c_str(),
        &db_,
        SQLITE_OPEN_READONLY | SQLITE_OPEN_NOMUTEX,
        nullptr
    );

    if (rc != SQLITE_OK) {
        std::cerr << "BibleRagEngine: Не удалось открыть базу SQLite: "
                  << sqlite3_errmsg(db_) << std::endl;
        close();
        return false;
    }

    // Предзагрузка векторов в непрерывный RAM-буфер
    if (!load_vectors_from_db()) {
        std::cerr << "BibleRagEngine: Ошибка загрузки векторов!" << std::endl;
        close();
        return false;
    }

    // Подготовка кэшированных SQL-выражений
    if (!prepare_statements()) {
        std::cerr << "BibleRagEngine: Ошибка подготовки SQL statements!" << std::endl;
        close();
        return false;
    }

    return true;
}

bool BibleRagEngine::load_vectors_from_db() {
    sqlite3_stmt* stmt = nullptr;
    const char* sql = "SELECT chunk_id, embedding FROM chunk_embeddings ORDER BY chunk_id ASC;";
    int rc = sqlite3_prepare_v2(db_, sql, -1, &stmt, nullptr);
    if (rc != SQLITE_OK) {
        return false;
    }

    chunk_ids_.clear();
    embeddings_buffer_.clear();

    // Заранее резервируем память под ~10 500 элементов
    chunk_ids_.reserve(11000);
    embeddings_buffer_.reserve(11000 * vector_dim_);

    while (sqlite3_step(stmt) == SQLITE_ROW) {
        int32_t chunk_id = sqlite3_column_int(stmt, 0);
        const void* blob = sqlite3_column_blob(stmt, 1);
        int bytes = sqlite3_column_bytes(stmt, 1);

        if (bytes == vector_dim_ * sizeof(float) && blob != nullptr) {
            chunk_ids_.push_back(chunk_id);
            const float* float_data = reinterpret_cast<const float*>(blob);
            embeddings_buffer_.insert(
                embeddings_buffer_.end(),
                float_data,
                float_data + vector_dim_
            );
        }
    }

    sqlite3_finalize(stmt);
    return !chunk_ids_.empty();
}

bool BibleRagEngine::prepare_statements() {
    // 1. Полнотекстовый поиск по виртуальной таблице FTS5
    const char* fts_sql =
        "SELECT rowid, rank FROM fts_chunks WHERE fts_chunks MATCH ? ORDER BY rank LIMIT ?;";
    int rc = sqlite3_prepare_v2(db_, fts_sql, -1, &stmt_fts_, nullptr);
    if (rc != SQLITE_OK) {
        return false;
    }

    // 2. Выборка метаданных чанка по ID
    const char* lookup_sql =
        "SELECT canonical_ref, testament, category, text FROM chunks WHERE id = ?;";
    rc = sqlite3_prepare_v2(db_, lookup_sql, -1, &stmt_chunk_lookup_, nullptr);
    if (rc != SQLITE_OK) {
        return false;
    }

    return true;
}

std::vector<ScoredItem> BibleRagEngine::search_fts5(const std::string& query_text, int top_k) {
    if (!stmt_fts_ || query_text.empty()) {
        return {};
    }

    // Очистка от спецсимволов FTS5 с сохранением UTF-8 кириллицы
    std::string clean_query;
    clean_query.reserve(query_text.size());
    for (char c : query_text) {
        unsigned char uc = static_cast<unsigned char>(c);
        // Заменяем ASCII знаки препинания и разделители на пробелы
        if (uc < 128 && (std::ispunct(uc) || std::isspace(uc))) {
            clean_query += ' ';
        } else {
            clean_query += c;
        }
    }

    std::stringstream clean_stream(clean_query);
    std::string term;
    std::vector<std::string> terms;
    while (clean_stream >> term) {
        // Минимум 3 русские буквы (6 байт в UTF-8)
        if (term.length() >= 6) {
            std::string lower = to_lower_utf8(term);
            if (BIBLE_STOP_WORDS.find(lower) == BIBLE_STOP_WORDS.end()) {
                std::string stemmed = stem_russian_term(lower);
                terms.push_back(stemmed);
            }
        }
    }

    // Если все слова запроса оказались в стоп-листе, берем исходные длинные слова
    if (terms.empty()) {
        std::stringstream fallback_stream(clean_query);
        while (fallback_stream >> term) {
            if (term.length() >= 6) {
                terms.push_back(stem_russian_term(to_lower_utf8(term)));
            }
        }
    }

    if (terms.empty()) {
        return {};
    }

    std::stringstream match_stream;
    for (size_t i = 0; i < terms.size(); ++i) {
        if (i > 0) match_stream << " OR ";
        match_stream << "\"" << terms[i] << "\"*";
    }

    std::string match_query = match_stream.str();

    sqlite3_reset(stmt_fts_);
    sqlite3_clear_bindings(stmt_fts_);

    sqlite3_bind_text(stmt_fts_, 1, match_query.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(stmt_fts_, 2, top_k);

    std::vector<ScoredItem> fts_results;
    while (sqlite3_step(stmt_fts_) == SQLITE_ROW) {
        int32_t rowid = sqlite3_column_int(stmt_fts_, 0);
        float rank = static_cast<float>(sqlite3_column_double(stmt_fts_, 1));
        fts_results.push_back({rowid, rank});
    }

    sqlite3_reset(stmt_fts_);
    return fts_results;
}

std::vector<BibleSearchResult> BibleRagEngine::hybrid_search(
    const float* query_embedding,
    const std::string& query_text,
    int top_k
) {
    if (chunk_ids_.empty()) {
        return {};
    }

    // 1. Dense SIMD векторный поиск (только если передан валидный ненулевой вектор!)
    std::vector<ScoredItem> dense_results;
    if (query_embedding != nullptr) {
        float norm_sq = 0.0f;
        for (int i = 0; i < std::min(vector_dim_, 32); ++i) {
            norm_sq += query_embedding[i] * query_embedding[i];
        }
        if (norm_sq > 1e-4f) {
            auto raw_dense = find_top_k(
                embeddings_buffer_.data(),
                chunk_ids_.data(),
                static_cast<int>(chunk_ids_.size()),
                vector_dim_,
                query_embedding,
                30
            );
            // Вариант 4: Семантический порог сходства (отсечение слабо релевантного шума)
            for (const auto& item : raw_dense) {
                if (item.score >= 0.35f) {
                    dense_results.push_back(item);
                }
            }
        }
    }

    // 2. Sparse FTS5 BM25 поиск
    std::vector<ScoredItem> sparse_results = search_fts5(query_text, 30);

    // 3. Слияние рангов Reciprocal Rank Fusion
    std::vector<RankedResult> fused = fuse_ranks(dense_results, sparse_results, top_k, 60.0f);

    // 4. Обогащение текстами из таблицы chunks
    std::vector<BibleSearchResult> final_results;
    final_results.reserve(fused.size());

    for (const auto& item : fused) {
        sqlite3_reset(stmt_chunk_lookup_);
        sqlite3_clear_bindings(stmt_chunk_lookup_);
        sqlite3_bind_int(stmt_chunk_lookup_, 1, item.chunk_id);

        if (sqlite3_step(stmt_chunk_lookup_) == SQLITE_ROW) {
            const char* ref = reinterpret_cast<const char*>(sqlite3_column_text(stmt_chunk_lookup_, 0));
            const char* test = reinterpret_cast<const char*>(sqlite3_column_text(stmt_chunk_lookup_, 1));
            const char* cat = reinterpret_cast<const char*>(sqlite3_column_text(stmt_chunk_lookup_, 2));
            const char* txt = reinterpret_cast<const char*>(sqlite3_column_text(stmt_chunk_lookup_, 3));

            BibleSearchResult r;
            r.chunk_id = item.chunk_id;
            r.canonical_ref = ref ? ref : "";
            r.testament = (test && std::string(test) == "new") ? "Новый Завет" : "Ветхий Завет";
            r.category = cat ? cat : "";
            r.text = txt ? txt : "";
            r.rrf_score = item.rrf_score;
            r.dense_rank = item.dense_rank;
            r.dense_score = item.dense_score;
            r.sparse_rank = item.sparse_rank;
            r.sparse_score = item.sparse_score;

            final_results.push_back(r);
        }
        sqlite3_reset(stmt_chunk_lookup_);
    }

    return final_results;
}

void BibleRagEngine::close() {
    if (stmt_fts_) {
        sqlite3_finalize(stmt_fts_);
        stmt_fts_ = nullptr;
    }
    if (stmt_chunk_lookup_) {
        sqlite3_finalize(stmt_chunk_lookup_);
        stmt_chunk_lookup_ = nullptr;
    }
    if (db_) {
        sqlite3_close(db_);
        db_ = nullptr;
    }
    chunk_ids_.clear();
    embeddings_buffer_.clear();
}

} // namespace biblerag
