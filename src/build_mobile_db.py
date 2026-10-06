import os
import sys
import sqlite3
from pathlib import Path
import numpy as np
from tqdm import tqdm
import onnxruntime as ort
from transformers import AutoTokenizer

# Настройка UTF-8 вывода для Windows
sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

try:
    from src.parser import load_raw_bible, extract_verses, chunk_bible
except ImportError:
    from parser import load_raw_bible, extract_verses, chunk_bible

RAW_DATA_PATH = Path("data/raw/synodal.json")
OUTPUT_DIR = Path("data/output")
DB_PATH = OUTPUT_DIR / "bible.db"
MODEL_PATH = OUTPUT_DIR / "embedding_model_quantized.onnx"
BATCH_SIZE = 64


def create_schema(conn: sqlite3.Connection):
    cursor = conn.cursor()

    # 1. Таблица отдельных стихов
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS verses (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        book_order INTEGER NOT NULL,
        book_name_ru TEXT NOT NULL,
        book_name_en TEXT NOT NULL,
        testament TEXT NOT NULL,
        category TEXT NOT NULL,
        chapter INTEGER NOT NULL,
        verse INTEGER NOT NULL,
        text TEXT NOT NULL
    );
    """)

    cursor.execute("""
    CREATE INDEX IF NOT EXISTS idx_verses_lookup 
    ON verses(book_order, chapter, verse);
    """)

    # 2. Таблица скользящих окон (чанков)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS chunks (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        chunk_key TEXT UNIQUE NOT NULL,
        canonical_ref TEXT NOT NULL,
        book_order INTEGER NOT NULL,
        book_name_ru TEXT NOT NULL,
        book_name_en TEXT NOT NULL,
        testament TEXT NOT NULL,
        category TEXT NOT NULL,
        chapter INTEGER NOT NULL,
        start_verse INTEGER NOT NULL,
        end_verse INTEGER NOT NULL,
        total_verses INTEGER NOT NULL,
        text TEXT NOT NULL
    );
    """)

    cursor.execute("""
    CREATE INDEX IF NOT EXISTS idx_chunks_lookup 
    ON chunks(book_order, chapter);
    """)

    # 3. Таблица бинарных векторов (BLOB)
    cursor.execute("""
    CREATE TABLE IF NOT EXISTS chunk_embeddings (
        chunk_id INTEGER PRIMARY KEY,
        embedding BLOB NOT NULL,
        FOREIGN KEY (chunk_id) REFERENCES chunks(id) ON DELETE CASCADE
    );
    """)

    # 4. Полнотекстовый поиск FTS5 для стихов
    cursor.execute("""
    CREATE VIRTUAL TABLE IF NOT EXISTS fts_verses USING fts5(
        text,
        content='verses',
        content_rowid='id',
        tokenize='unicode61 remove_diacritics 2'
    );
    """)

    # 5. Полнотекстовый поиск FTS5 для чанков (ключевой для гибридного RAG)
    cursor.execute("""
    CREATE VIRTUAL TABLE IF NOT EXISTS fts_chunks USING fts5(
        text,
        content='chunks',
        content_rowid='id',
        tokenize='unicode61 remove_diacritics 2'
    );
    """)

    conn.commit()


def populate_verses_and_chunks(conn: sqlite3.Connection, books: list):
    cursor = conn.cursor()

    print("1. Извлечение стихов и нарезка чанков...")
    verses_list = extract_verses(books)
    chunks_list = chunk_bible(books, window_size=4, overlap=1)

    print(f"   -> Найдено стихов: {len(verses_list)}")
    print(f"   -> Сформировано чанков: {len(chunks_list)}")

    # Вставка стихов
    print("2. Запись стихов в таблицу verses...")
    verse_records = [
        (
            v.book_order,
            v.book_name_ru,
            v.book_name_en,
            v.testament.value,
            v.category.value,
            v.chapter,
            v.verse,
            v.text,
        )
        for v in verses_list
    ]
    cursor.executemany(
        """
        INSERT INTO verses (
            book_order, book_name_ru, book_name_en, testament,
            category, chapter, verse, text
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        verse_records,
    )

    # Заполнение FTS5 для стихов
    cursor.execute("INSERT INTO fts_verses(rowid, text) SELECT id, text FROM verses;")

    # Вставка чанков
    print("3. Запись чанков в таблицу chunks...")
    chunk_records = [
        (
            c.chunk_id,
            c.canonical_ref,
            c.book_order,
            c.book_name_ru,
            c.book_name_en,
            c.testament.value,
            c.category.value,
            c.chapter,
            c.start_verse,
            c.end_verse,
            c.total_verses,
            c.text,
        )
        for c in chunks_list
    ]
    cursor.executemany(
        """
        INSERT INTO chunks (
            chunk_key, canonical_ref, book_order, book_name_ru, book_name_en,
            testament, category, chapter, start_verse, end_verse, total_verses, text
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        chunk_records,
    )

    # Заполнение FTS5 для чанков
    cursor.execute("INSERT INTO fts_chunks(rowid, text) SELECT id, text FROM chunks;")

    conn.commit()
    return chunks_list


def generate_and_store_embeddings(conn: sqlite3.Connection):
    if not MODEL_PATH.exists():
        raise FileNotFoundError(f"Файл модели не найден: {MODEL_PATH}. Запустите сначала export_onnx.py!")

    print(f"4. Инициализация ONNX Runtime ({MODEL_PATH.name})...")
    session = ort.InferenceSession(str(MODEL_PATH), providers=["CPUExecutionProvider"])
    tokenizer = AutoTokenizer.from_pretrained(str(OUTPUT_DIR))

    cursor = conn.cursor()
    cursor.execute("SELECT id, text FROM chunks ORDER BY id ASC;")
    chunks = cursor.fetchall()
    total_chunks = len(chunks)

    print(f"5. Векторизация {total_chunks} чанков (батчи по {BATCH_SIZE})...")
    cursor_insert = conn.cursor()

    for i in tqdm(range(0, total_chunks, BATCH_SIZE), desc="Векторизация чанков"):
        batch = chunks[i : i + BATCH_SIZE]
        batch_ids = [item[0] for item in batch]
        batch_texts = [item[1] for item in batch]

        encoded = tokenizer(
            batch_texts,
            padding=True,
            truncation=True,
            max_length=512,
            return_tensors="np",
        )

        onnx_inputs = {
            "input_ids": encoded["input_ids"],
            "attention_mask": encoded["attention_mask"],
            "token_type_ids": encoded["token_type_ids"],
        }

        outputs = session.run(None, onnx_inputs)
        # outputs[0] имеет форму [batch_size, 312] и уже нормализован L2
        embeddings: np.ndarray = outputs[0].astype(np.float32)

        records_to_insert = [
            (chunk_id, emb.tobytes())
            for chunk_id, emb in zip(batch_ids, embeddings)
        ]

        cursor_insert.executemany(
            "INSERT INTO chunk_embeddings (chunk_id, embedding) VALUES (?, ?);",
            records_to_insert,
        )
        conn.commit()


def optimize_database(conn: sqlite3.Connection):
    print("6. Оптимизация FTS индексов и базы данных...")
    cursor = conn.cursor()
    cursor.execute("INSERT INTO fts_verses(fts_verses) VALUES('optimize');")
    cursor.execute("INSERT INTO fts_chunks(fts_chunks) VALUES('optimize');")
    cursor.execute("PRAGMA optimize;")
    conn.commit()
    conn.close()

    # VACUUM для максимального сжатия
    print("7. Выполнение VACUUM для минимального веса файла...")
    raw_conn = sqlite3.connect(DB_PATH)
    raw_conn.execute("VACUUM;")
    raw_conn.close()


def test_quick_search():
    print("\n8. Проверочный тестовый поиск...")
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # Проверка FTS5
    query_word = "Мелхиседек"
    cursor.execute(
        """
        SELECT c.canonical_ref, snippet(fts_chunks, 0, '<b>', '</b>', '...', 10)
        FROM fts_chunks f
        JOIN chunks c ON f.rowid = c.id
        WHERE fts_chunks MATCH ?
        LIMIT 3;
        """,
        (query_word,),
    )
    fts_results = cursor.fetchall()
    print(f"\n   [FTS5 тест по слову '{query_word}']:")
    for ref, snip in fts_results:
        print(f"   * {ref}: {snip}")

    # Проверка чтения векторов
    cursor.execute("SELECT chunk_id, length(embedding) FROM chunk_embeddings LIMIT 3;")
    vec_results = cursor.fetchall()
    print("\n   [Тест таблицы векторов]:")
    for cid, blen in vec_results:
        print(f"   * Chunk ID {cid}: BLOB размер {blen} байт (312 float32 * 4 = 1248)")

    conn.close()


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    if DB_PATH.exists():
        print(f"Удаление старой версии {DB_PATH}...")
        os.remove(DB_PATH)

    conn = sqlite3.connect(DB_PATH)
    # Настройки производительности SQLite для быстрой массовой записи
    conn.execute("PRAGMA synchronous = OFF;")
    conn.execute("PRAGMA journal_mode = MEMORY;")

    create_schema(conn)

    books = load_raw_bible(RAW_DATA_PATH)
    populate_verses_and_chunks(conn, books)
    generate_and_store_embeddings(conn)
    optimize_database(conn)

    size_mb = DB_PATH.stat().st_size / (1024 * 1024)
    print("\n" + "=" * 55)
    print(f"✓ База данных успешно создана: {DB_PATH}")
    print(f"✓ Итоговый вес bible.db: {size_mb:.2f} МБ")
    print("=" * 55)

    test_quick_search()


if __name__ == "__main__":
    main()
