from enum import Enum
from typing import Optional
from pydantic import BaseModel, Field


class Testament(str, Enum):
    OLD = "old"  # Ветхий Завет
    NEW = "new"  # Новый Завет


class BookCategory(str, Enum):
    # Ветхий Завет
    LAW = "law"                    # Пятикнижие (Тора / Закон Моисея)
    HISTORY = "history"            # Исторические книги (Иисус Навин — Есфирь)
    POETRY_WISDOM = "wisdom"       # Учительные и поэтические (Иов — Песнь Песней)
    PROPHETS = "prophets"          # Пророки (Исаия — Малахия)
    
    # Новый Завет
    GOSPELS = "gospels"            # Евангелия (Матфей — Иоанн)
    ACTS = "acts"                  # Деяния апостолов
    EPISTLES = "epistles"          # Послания апостолов (Павел + Соборные)
    APOCALYPSE = "apocalypse"      # Откровение Иоанна


class Verse(BaseModel):
    book_order: int = Field(description="Порядковый номер книги от 1 до 66")
    book_name_ru: str = Field(description="Русское название, напр. 'От Иоанна'")
    book_name_en: str = Field(description="Английский идентификатор, напр. 'John'")
    testament: Testament
    category: BookCategory
    chapter: int
    verse: int
    text: str


class BibleChunk(BaseModel):
    chunk_id: str = Field(description="Уникальный ID, напр. 'NT_43_John_1_1-4'")
    
    # Текстовое содержимое
    text: str = Field(description="Объединенный текст стихов чанка")
    canonical_ref: str = Field(description="Читаемая каноническая ссылка, напр. 'От Иоанна 1:1-4'")
    
    # Метаданные книги
    book_order: int = Field(description="Порядковый номер книги (1-66)")
    book_name_ru: str = Field(description="Русское название, напр. 'От Иоанна'")
    book_name_en: str = Field(description="Английское имя, напр. 'John'")
    testament: Testament = Field(description="'old' или 'new'")
    category: BookCategory
    
    # Навигация внутри книги
    chapter: int = Field(description="Номер главы")
    start_verse: int = Field(description="Начальный стих окна")
    end_verse: int = Field(description="Конечный стих окна")
    total_verses: int = Field(description="Количество стихов в чанке")