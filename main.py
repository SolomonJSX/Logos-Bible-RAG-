import os
import sys
import time
import sqlite3
import argparse
from pathlib import Path
from typing import List, Dict, Any, Tuple
import numpy as np
import onnxruntime as ort
from transformers import AutoTokenizer

# Настройка UTF-8 для Windows консоли
sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

OUTPUT_DIR = Path("data/output")
DB_PATH = OUTPUT_DIR / "bible.db"
MODEL_PATH = OUTPUT_DIR / "embedding_model_quantized.onnx"
RRF_K = 60  # Константа сглаживания для Reciprocal Rank Fusion


class BibleSearchEngine:
    def __init__(self, db_path: Path = DB_PATH, model_path: Path = MODEL_PATH):
        if not db_path.exists():
            raise FileNotFoundError(f"База данных {db_path} не найдена! Сначала запустите src/build_mobile_db.py")
        if not model_path.exists():
            raise FileNotFoundError(f"Модель {model_path} не найдена! Сначала запустите src/export_onnx.py")

        t0 = time.perf_counter()
        self.tokenizer = AutoTokenizer.from_pretrained(str(OUTPUT_DIR))
        self.session = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
        self.conn = sqlite3.connect(str(db_path))
        self._load_vectors()
        load_time_ms = (time.perf_counter() - t0) * 1000
        print(f"✓ Поисковый движок инициализирован за {load_time_ms:.1f} мс.")
        print(f"✓ Загружено {len(self.chunk_ids)} чанков и векторов ({self.embeddings_matrix.nbytes / (1024*1024):.2f} МБ RAM).\n")

    def _load_vectors(self):
        """
        Предзагружает матрицу векторов всех чанков.
        На C++/Android в NDK это делается через единый mmap буфер или массив в памяти.
        """
        cursor = self.conn.cursor()
        cursor.execute("SELECT chunk_id, embedding FROM chunk_embeddings ORDER BY chunk_id ASC;")
        rows = cursor.fetchall()

        self.chunk_ids = np.array([r[0] for r in rows], dtype=np.int32)
        raw_blobs = [r[1] for r in rows]

        # Преобразуем список BLOB в единую 2D-матрицу float32 [10499, 312]
        all_vecs = np.frombuffer(b"".join(raw_blobs), dtype=np.float32).reshape(len(rows), 312)
        self.embeddings_matrix = all_vecs

    def encode_query(self, query: str) -> Tuple[np.ndarray, float]:
        """Токенизирует запрос и получает L2-нормализованный вектор через ONNX Runtime."""
        t0 = time.perf_counter()
        inputs = self.tokenizer(
            [query],
            padding=True,
            truncation=True,
            max_length=128,
            return_tensors="np",
        )
        outputs = self.session.run(
            None,
            {
                "input_ids": inputs["input_ids"],
                "attention_mask": inputs["attention_mask"],
                "token_type_ids": inputs["token_type_ids"],
            },
        )
        vec = outputs[0][0].astype(np.float32)
        elapsed_ms = (time.perf_counter() - t0) * 1000
        return vec, elapsed_ms

    def search_dense(self, query_vec: np.ndarray, top_k: int = 30) -> Tuple[List[Dict[str, Any]], float]:
        """
        Векторный поиск (скалярное произведение = косинусное сходство).
        На Android выполняется через ARM NEON SIMD.
        """
        t0 = time.perf_counter()
        scores = np.dot(self.embeddings_matrix, query_vec)
        top_indices = np.argpartition(scores, -top_k)[-top_k:]
        sorted_indices = top_indices[np.argsort(-scores[top_indices])]

        results = []
        for rank, idx in enumerate(sorted_indices, start=1):
            results.append({
                "chunk_id": int(self.chunk_ids[idx]),
                "score": float(scores[idx]),
                "dense_rank": rank,
            })
        elapsed_ms = (time.perf_counter() - t0) * 1000
        return results, elapsed_ms

    def search_sparse(self, query: str, top_k: int = 30) -> Tuple[List[Dict[str, Any]], float]:
        """
        Полнотекстовый поиск FTS5 (BM25). Идеален для редких имен, топонимов и точных фраз.
        """
        t0 = time.perf_counter()
        cursor = self.conn.cursor()

        clean_query = "".join(c if c.isalnum() or c.isspace() else " " for c in query)
        terms = [t for t in clean_query.split() if len(t) > 1]
        if not terms:
            return [], 0.0

        fts_expression = " OR ".join(f'"{t}"*' for t in terms)

        try:
            cursor.execute(
                """
                SELECT rowid, rank
                FROM fts_chunks
                WHERE fts_chunks MATCH ?
                ORDER BY rank
                LIMIT ?;
                """,
                (fts_expression, top_k),
            )
            rows = cursor.fetchall()
            results = [
                {"chunk_id": r[0], "score": float(r[1]), "sparse_rank": rank}
                for rank, r in enumerate(rows, start=1)
            ]
        except sqlite3.OperationalError:
            results = []

        elapsed_ms = (time.perf_counter() - t0) * 1000
        return results, elapsed_ms

    def hybrid_search(self, query: str, top_k: int = 3) -> Dict[str, Any]:
        """
        Гибридный поиск Reciprocal Rank Fusion (RRF):
        Score(d) = 1 / (60 + Rank_dense) + 1 / (60 + Rank_sparse)
        """
        t_start = time.perf_counter()

        query_vec, encode_ms = self.encode_query(query)
        dense_results, dense_ms = self.search_dense(query_vec, top_k=30)
        sparse_results, sparse_ms = self.search_sparse(query, top_k=30)

        t_rrf = time.perf_counter()
        rrf_scores: Dict[int, Dict[str, Any]] = {}

        for item in dense_results:
            cid = item["chunk_id"]
            dense_rank = item["dense_rank"]
            rrf_score = 1.0 / (RRF_K + dense_rank)
            rrf_scores[cid] = {
                "chunk_id": cid,
                "rrf_score": rrf_score,
                "dense_rank": dense_rank,
                "dense_score": item["score"],
                "sparse_rank": None,
                "sparse_score": None,
            }

        for item in sparse_results:
            cid = item["chunk_id"]
            sparse_rank = item["sparse_rank"]
            sparse_component = 1.0 / (RRF_K + sparse_rank)
            if cid in rrf_scores:
                rrf_scores[cid]["rrf_score"] += sparse_component
                rrf_scores[cid]["sparse_rank"] = sparse_rank
                rrf_scores[cid]["sparse_score"] = item["score"]
            else:
                rrf_scores[cid] = {
                    "chunk_id": cid,
                    "rrf_score": sparse_component,
                    "dense_rank": None,
                    "dense_score": 0.0,
                    "sparse_rank": sparse_rank,
                    "sparse_score": item["score"],
                }

        sorted_ranked = sorted(rrf_scores.values(), key=lambda x: x["rrf_score"], reverse=True)[:top_k]

        cursor = self.conn.cursor()
        final_items = []
        for item in sorted_ranked:
            cid = item["chunk_id"]
            cursor.execute(
                """
                SELECT canonical_ref, testament, category, text
                FROM chunks
                WHERE id = ?;
                """,
                (cid,),
            )
            row = cursor.fetchone()
            if row:
                final_items.append({
                    **item,
                    "canonical_ref": row[0],
                    "testament": "Новый Завет" if row[1] == "new" else "Ветхий Завет",
                    "category": row[2],
                    "text": row[3],
                })

        total_ms = (time.perf_counter() - t_start) * 1000

        return {
            "query": query,
            "results": final_items,
            "timing": {
                "encode_ms": encode_ms,
                "dense_ms": dense_ms,
                "sparse_ms": sparse_ms,
                "total_ms": total_ms,
            },
        }

    def build_llm_prompt(self, query: str, results: List[Dict[str, Any]]) -> str:
        """Формирует готовый лаконичный промпт для мобильной LLM (Qwen2.5-1.5B)."""
        context_parts = []
        for i, res in enumerate(results, start=1):
            context_parts.append(f"[{res['canonical_ref']}]: {res['text']}")
        context_str = "\n\n".join(context_parts)

        prompt = (
            "<|im_start|>system\n"
            "Ты библейский помощник. Отвечай кратко и только по фактам из контекста. "
            "Обязательно указывай ссылки на стихи в скобках [Книга Глава:Стих]. "
            "Если ответа нет в контексте — напиши: «В предоставленном тексте об этом не сказано».<|im_end|>\n"
            "<|im_start|>user\n"
            f"Контекст:\n{context_str}\n\n"
            f"Вопрос: {query}<|im_end|>\n"
            "<|im_start|>assistant\n"
        )
        return prompt


def print_search_results(res_dict: Dict[str, Any], show_prompt: bool = False, engine: BibleSearchEngine = None):
    query = res_dict["query"]
    results = res_dict["results"]
    timing = res_dict["timing"]

    print("\n" + "=" * 75)
    print(f"🔍 Запрос: «{query}»")
    print(f"⏱️  Время: {timing['total_ms']:.1f} мс (ONNX эмбеддер: {timing['encode_ms']:.1f} мс | SIMD Dense: {timing['dense_ms']:.1f} мс | FTS5: {timing['sparse_ms']:.1f} мс)")
    print("=" * 75)

    for i, res in enumerate(results, start=1):
        dr = f"#{res['dense_rank']} (cos={res['dense_score']:.3f})" if res['dense_rank'] else "—"
        sr = f"#{res['sparse_rank']}" if res['sparse_rank'] else "—"
        print(f"\n[{i}] 📖 {res['canonical_ref']} [{res['testament']}]")
        print(f"    RRF Score: {res['rrf_score']:.5f} | Dense Rank: {dr} | FTS5 Rank: {sr}")
        print(f"    Текст:")
        print(f"    {res['text']}")

    if show_prompt and engine:
        print("\n" + "-" * 75)
        print("🤖 ГОТОВЫЙ ПРОМПТ ДЛЯ LLM (Qwen2.5-1.5B):")
        print("-" * 75)
        prompt = engine.build_llm_prompt(query, results)
        print(prompt)

    print("=" * 75)


def run_benchmark(engine: BibleSearchEngine):
    print("\n" + "=" * 80)
    print("🚀 ЗАПУСК БЕНЧМАРКА ГИБРИДНОГО ПОИСКА (RRF TEST SUITE)")
    print("=" * 80)

    test_cases = [
        ("Кто такой Мелхиседек?", "Редкое имя / Ветхий и Новый Завет"),
        ("Валаам и ослица", "Исторический сюжет / Имя собственное"),
        ("Любовь долготерпит, милосердствует", "Точная библейская цитата"),
        ("Как относиться к врагам и ненавидящим?", "Семантический этический вопрос"),
        ("Притча о блудном сыне", "Притча / Евангелия"),
        ("В начале сотворил Бог небо и землю", "Догматика / Первые стихи"),
    ]

    table_rows = []
    total_times = []

    for query, category in test_cases:
        out = engine.hybrid_search(query, top_k=2)
        timing = out["timing"]
        total_times.append(timing["total_ms"])

        top1 = out["results"][0] if out["results"] else None
        top_ref = top1["canonical_ref"] if top1 else "—"
        dr = f"#{top1['dense_rank']}" if top1 and top1['dense_rank'] else "—"
        sr = f"#{top1['sparse_rank']}" if top1 and top1['sparse_rank'] else "—"
        rrf = f"{top1['rrf_score']:.4f}" if top1 else "—"

        table_rows.append((category, query, top_ref, dr, sr, rrf, f"{timing['total_ms']:.1f} мс"))

    print(f"\n{'Категория':<28} | {'Запрос':<33} | {'Топ-1':<18} | {'Dense':<6} | {'FTS5':<5} | {'RRF':<7} | {'Время':<8}")
    print("-" * 115)
    for cat, q, ref, dr, sr, rrf, tm in table_rows:
        print(f"{cat:<28} | {q:<33} | {ref:<18} | {dr:<6} | {sr:<5} | {rrf:<7} | {tm:<8}")

    print("-" * 115)
    print(f"📊 Среднее время гибридного поиска по всей Библии (10 499 чанков): {np.mean(total_times):.2f} мс")
    print("=" * 80 + "\n")


def main():
    parser = argparse.ArgumentParser(description="Bible On-Device RAG Hybrid Search CLI")
    parser.add_argument("--query", "-q", type=str, help="Поисковый запрос (если не указан, интерактивный режим)")
    parser.add_argument("--top_k", "-k", type=int, default=3, help="Количество лучших фрагментов (по умолчанию: 3)")
    parser.add_argument("--prompt", "-p", action="store_true", help="Сформировать и показать промпт для LLM")
    parser.add_argument("--benchmark", "-b", action="store_true", help="Запустить автоматический бенчмарк тест-сьюита")
    args = parser.parse_args()

    engine = BibleSearchEngine()

    if args.benchmark:
        run_benchmark(engine)
        return

    if args.query:
        res = engine.hybrid_search(args.query, top_k=args.top_k)
        print_search_results(res, show_prompt=args.prompt, engine=engine)
        return

    print("--- Интерактивный поиск по Библии (для выхода введите 'exit' или 'q') ---")
    while True:
        try:
            query = input("\nВведите вопрос или тему: ").strip()
            if not query:
                continue
            if query.lower() in ("exit", "quit", "q"):
                print("Выход из программы.")
                break

            res = engine.hybrid_search(query, top_k=args.top_k)
            print_search_results(res, show_prompt=args.prompt, engine=engine)
        except (KeyboardInterrupt, EOFError):
            print("\nВыход из программы.")
            break


if __name__ == "__main__":
    main()
