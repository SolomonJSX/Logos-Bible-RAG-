#include "bible_rag_engine.h"
#include <iostream>
#include <chrono>
#include <vector>
#include <cmath>

int main(int argc, char** argv) {
    std::string db_path = "data/output/bible.db";
    if (argc > 1) {
        db_path = argv[1];
    }

    std::cout << "========================================" << std::endl;
    std::cout << "  C++ Core Engine: Bible RAG Test Suite" << std::endl;
    std::cout << "========================================" << std::endl;

    biblerag::BibleRagEngine engine;
    auto t0 = std::chrono::high_resolution_clock::now();
    if (!engine.init(db_path)) {
        std::cerr << "Ошибка инициализации движка с базой: " << db_path << std::endl;
        return 1;
    }
    auto t1 = std::chrono::high_resolution_clock::now();
    double init_ms = std::chrono::duration<double, std::milli>(t1 - t0).count();

    std::cout << "✓ Движок успешно инициализирован за " << init_ms << " мс" << std::endl;
    std::cout << "✓ Загружено чанков в память: " << engine.get_total_chunks() << std::endl;
    std::cout << "✓ Размерность векторов: " << engine.get_vector_dim() << std::endl;

    // Синтетический нормализованный вектор для проверки скалярного произведения
    std::vector<float> test_vec(engine.get_vector_dim(), 1.0f / std::sqrt(static_cast<float>(engine.get_vector_dim())));

    // Тест гибридного поиска
    std::string query = "Мелхиседек царь";
    std::cout << "\nТестовый поиск по запросу: «" << query << "»" << std::endl;

    auto res = engine.hybrid_search(test_vec.data(), query, 3);
    std::cout << "Найдено результатов: " << res.size() << std::endl;

    for (size_t i = 0; i < res.size(); ++i) {
        const auto& r = res[i];
        std::cout << "\n[" << (i + 1) << "] " << r.canonical_ref << " (" << r.testament << ")" << std::endl;
        std::cout << "    RRF Score: " << r.rrf_score << " | Dense Rank: #" << r.dense_rank
                  << " | FTS5 Rank: #" << r.sparse_rank << std::endl;
        std::cout << "    Текст: " << r.text.substr(0, 100) << "..." << std::endl;
    }

    // Бенчмарк производительности скалярного произведения SIMD
    std::cout << "\n--- Бенчмарк: 100 проходов гибридного поиска по всей Библии ---" << std::endl;
    auto bench_t0 = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < 100; ++i) {
        auto bench_res = engine.hybrid_search(test_vec.data(), query, 3);
    }
    auto bench_t1 = std::chrono::high_resolution_clock::now();
    double total_ms = std::chrono::duration<double, std::milli>(bench_t1 - bench_t0).count();
    std::cout << "✓ Среднее время 1 поиска: " << (total_ms / 100.0) << " мс!" << std::endl;
    std::cout << "========================================" << std::endl;

    return 0;
}
