import json
from pathlib import Path
from typing import List, Dict, Tuple
try:
    from src.schemas import BibleChunk, Verse, Testament, BookCategory
except ImportError:
    from schemas import BibleChunk, Verse, Testament, BookCategory

BOOK_CATALOG: Dict[str, Tuple[int, str, Testament, BookCategory]] = {
    # Ветхий Завет (1-39)
    "Genesis": (1, "Бытие", Testament.OLD, BookCategory.LAW),
    "Exodus": (2, "Исход", Testament.OLD, BookCategory.LAW),
    "Leviticus": (3, "Левит", Testament.OLD, BookCategory.LAW),
    "Numbers": (4, "Числа", Testament.OLD, BookCategory.LAW),
    "Deuteronomy": (5, "Второзаконие", Testament.OLD, BookCategory.LAW),
    "Joshua": (6, "Иисус Навин", Testament.OLD, BookCategory.HISTORY),
    "Judges": (7, "Судьи", Testament.OLD, BookCategory.HISTORY),
    "Ruth": (8, "Руфь", Testament.OLD, BookCategory.HISTORY),
    "1 Samuel": (9, "1 Царств", Testament.OLD, BookCategory.HISTORY),
    "2 Samuel": (10, "2 Царств", Testament.OLD, BookCategory.HISTORY),
    "1 Kings": (11, "3 Царств", Testament.OLD, BookCategory.HISTORY),
    "2 Kings": (12, "4 Царств", Testament.OLD, BookCategory.HISTORY),
    "1 Chronicles": (13, "1 Паралипоменон", Testament.OLD, BookCategory.HISTORY),
    "2 Chronicles": (14, "2 Паралипоменон", Testament.OLD, BookCategory.HISTORY),
    "Ezra": (15, "Ездра", Testament.OLD, BookCategory.HISTORY),
    "Nehemiah": (16, "Неемия", Testament.OLD, BookCategory.HISTORY),
    "Esther": (17, "Есфирь", Testament.OLD, BookCategory.HISTORY),
    "Job": (18, "Иов", Testament.OLD, BookCategory.POETRY_WISDOM),
    "Psalms": (19, "Псалтирь", Testament.OLD, BookCategory.POETRY_WISDOM),
    "Proverbs": (20, "Притчи", Testament.OLD, BookCategory.POETRY_WISDOM),
    "Ecclesiastes": (21, "Екклесиаст", Testament.OLD, BookCategory.POETRY_WISDOM),
    "Song of Solomon": (22, "Песнь Песней", Testament.OLD, BookCategory.POETRY_WISDOM),
    "Isaiah": (23, "Исаия", Testament.OLD, BookCategory.PROPHETS),
    "Jeremiah": (24, "Иеремия", Testament.OLD, BookCategory.PROPHETS),
    "Lamentations": (25, "Плач Иеремии", Testament.OLD, BookCategory.PROPHETS),
    "Ezekiel": (26, "Иезекииль", Testament.OLD, BookCategory.PROPHETS),
    "Daniel": (27, "Даниил", Testament.OLD, BookCategory.PROPHETS),
    "Hosea": (28, "Осия", Testament.OLD, BookCategory.PROPHETS),
    "Joel": (29, "Иоиль", Testament.OLD, BookCategory.PROPHETS),
    "Amos": (30, "Амос", Testament.OLD, BookCategory.PROPHETS),
    "Obadiah": (31, "Авдий", Testament.OLD, BookCategory.PROPHETS),
    "Jonah": (32, "Иона", Testament.OLD, BookCategory.PROPHETS),
    "Micah": (33, "Михей", Testament.OLD, BookCategory.PROPHETS),
    "Nahum": (34, "Наум", Testament.OLD, BookCategory.PROPHETS),
    "Habakkuk": (35, "Аввакум", Testament.OLD, BookCategory.PROPHETS),
    "Zephaniah": (36, "Софония", Testament.OLD, BookCategory.PROPHETS),
    "Haggai": (37, "Аггей", Testament.OLD, BookCategory.PROPHETS),
    "Zechariah": (38, "Захария", Testament.OLD, BookCategory.PROPHETS),
    "Malachi": (39, "Малахия", Testament.OLD, BookCategory.PROPHETS),
    
    "Matthew": (40, "От Матфея", Testament.NEW, BookCategory.GOSPELS),
    "Mark": (41, "От Марка", Testament.NEW, BookCategory.GOSPELS),
    "Luke": (42, "От Луки", Testament.NEW, BookCategory.GOSPELS),
    "John": (43, "От Иоанна", Testament.NEW, BookCategory.GOSPELS),
    "Acts": (44, "Деяния", Testament.NEW, BookCategory.ACTS),
    "Romans": (45, "Римлянам", Testament.NEW, BookCategory.EPISTLES),
    "1 Corinthians": (46, "1 Коринфянам", Testament.NEW, BookCategory.EPISTLES),
    "2 Corinthians": (47, "2 Коринфянам", Testament.NEW, BookCategory.EPISTLES),
    "Galatians": (48, "Галатам", Testament.NEW, BookCategory.EPISTLES),
    "Ephesians": (49, "Ефесянам", Testament.NEW, BookCategory.EPISTLES),
    "Philippians": (50, "Филиппийцам", Testament.NEW, BookCategory.EPISTLES),
    "Colossians": (51, "Колоссянам", Testament.NEW, BookCategory.EPISTLES),
    "1 Thessalonians": (52, "1 Фессалоникийцам", Testament.NEW, BookCategory.EPISTLES),
    "2 Thessalonians": (53, "2 Фессалоникийцам", Testament.NEW, BookCategory.EPISTLES),
    "1 Timothy": (54, "1 Тимофею", Testament.NEW, BookCategory.EPISTLES),
    "2 Timothy": (55, "2 Тимофею", Testament.NEW, BookCategory.EPISTLES),
    "Titus": (56, "Титу", Testament.NEW, BookCategory.EPISTLES),
    "Philemon": (57, "Филимону", Testament.NEW, BookCategory.EPISTLES),
    "Hebrews": (58, "Евреям", Testament.NEW, BookCategory.EPISTLES),
    "James": (59, "Иакова", Testament.NEW, BookCategory.EPISTLES),
    "1 Peter": (60, "1 Петра", Testament.NEW, BookCategory.EPISTLES),
    "2 Peter": (61, "2 Петра", Testament.NEW, BookCategory.EPISTLES),
    "1 John": (62, "1 Иоанна", Testament.NEW, BookCategory.EPISTLES),
    "2 John": (63, "2 Иоанна", Testament.NEW, BookCategory.EPISTLES),
    "3 John": (64, "3 Иоанна", Testament.NEW, BookCategory.EPISTLES),
    "Jude": (65, "Иуды", Testament.NEW, BookCategory.EPISTLES),
    "Revelation": (66, "Откровение", Testament.NEW, BookCategory.APOCALYPSE),
}


def load_raw_bible(file_path: Path) -> list:
    with open(file_path, "r", encoding="utf-8-sig") as f:
        data = json.load(f)
    return data if isinstance(data, list) else data.get("books", [])


def extract_verses(books: list) -> List[Verse]:
    verses_list: List[Verse] = []
    for book in books:
        en_name = book.get("name") or book.get("title") or "Unknown"
        meta = BOOK_CATALOG.get(en_name)
        if not meta:
            order, ru_name, testament, category = 999, en_name, Testament.OLD, BookCategory.HISTORY
        else:
            order, ru_name, testament, category = meta

        chapters = book.get("chapters", [])
        for ch_idx, chapter in enumerate(chapters, start=1):
            verses = chapter.get("verses", []) if isinstance(chapter, dict) else chapter
            for v_idx, v in enumerate(verses, start=1):
                text_val = v.get("text", "") if isinstance(v, dict) else str(v)
                clean_text = text_val.strip().replace("\n", " ")
                verses_list.append(
                    Verse(
                        book_order=order,
                        book_name_ru=ru_name,
                        book_name_en=en_name,
                        testament=testament,
                        category=category,
                        chapter=ch_idx,
                        verse=v_idx,
                        text=clean_text,
                    )
                )
    return verses_list


def chunk_bible(books: list, window_size: int = 4, overlap: int = 1) -> List[BibleChunk]:
    chunks: List[BibleChunk] = []

    for book in books:
        en_name = book.get("name") or book.get("title") or "Unknown"
        meta = BOOK_CATALOG.get(en_name)

        if not meta:
            # Дефолтные значения на случай непредвиденных названий
            order, ru_name, testament, category = 999, en_name, Testament.OLD, BookCategory.HISTORY
        else:
            order, ru_name, testament, category = meta

        chapters = book.get("chapters", [])
        for ch_idx, chapter in enumerate(chapters, start=1):
            verses = chapter.get("verses", []) if isinstance(chapter, dict) else chapter
            total_verses = len(verses)
            if total_verses == 0:
                continue

            step = max(1, window_size - overlap)
            for i in range(0, total_verses, step):
                window = verses[i : i + window_size]
                if not window:
                    continue

                start_v = i + 1
                end_v = i + len(window)

                chunk_text_parts = []
                for v_offset, v in enumerate(window):
                    verse_num = start_v + v_offset
                    text_val = v.get("text", "") if isinstance(v, dict) else str(v)
                    clean_text = text_val.strip().replace("\n", " ")
                    chunk_text_parts.append(f"{verse_num}. {clean_text}")

                combined_text = " ".join(chunk_text_parts)
                ref_str = (
                    f"{ru_name} {ch_idx}:{start_v}-{end_v}"
                    if start_v != end_v
                    else f"{ru_name} {ch_idx}:{start_v}"
                )

                t_prefix = "OT" if testament == Testament.OLD else "NT"
                chunk_id = f"{t_prefix}_{order:02d}_{en_name}_{ch_idx}_{start_v}_{end_v}"

                chunks.append(
                    BibleChunk(
                        chunk_id=chunk_id,
                        text=combined_text,
                        canonical_ref=ref_str,
                        book_order=order,
                        book_name_ru=ru_name,
                        book_name_en=en_name,
                        testament=testament,
                        category=category,
                        chapter=ch_idx,
                        start_verse=start_v,
                        end_verse=end_v,
                        total_verses=len(window),
                    )
                )

    return chunks


if __name__ == "__main__":
    data_file = Path("data/raw/synodal.json")
    books = load_raw_bible(data_file)
    all_chunks = chunk_bible(books)
    print(f"Всего подготовлено чанков: {len(all_chunks)}")
    
    print("\n--- Пример чанка (Ветхий Завет) ---")
    print(all_chunks[0].model_dump_json(indent=2))

    nt_example = next(c for c in all_chunks if c.testament == Testament.NEW)
    print("\n--- Пример чанка (Новый Завет) ---")
    print(nt_example.model_dump_json(indent=2))