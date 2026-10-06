import sys
import shutil
from pathlib import Path
from huggingface_hub import hf_hub_download

# Настройка UTF-8 для Windows
sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

REPO_ID = "Qwen/Qwen2.5-1.5B-Instruct-GGUF"
FILENAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
OUTPUT_DIR = Path("data/output")
TARGET_PATH = OUTPUT_DIR / FILENAME


def download_model():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    if TARGET_PATH.exists():
        size_gb = TARGET_PATH.stat().st_size / (1024 ** 3)
        print(f"Файл уже существует: {TARGET_PATH} ({size_gb:.2f} ГБ)")
        return TARGET_PATH

    print(f"Загрузка модели {FILENAME} из репозитория {REPO_ID}...")
    cached_path = hf_hub_download(
        repo_id=REPO_ID,
        filename=FILENAME,
        local_dir=OUTPUT_DIR,
        local_dir_use_symlinks=False,
    )

    size_gb = Path(cached_path).stat().st_size / (1024 ** 3)
    print(f"✓ Модель успешно загружена: {cached_path}")
    print(f"✓ Размер: {size_gb:.2f} ГБ")
    return Path(cached_path)


if __name__ == "__main__":
    download_model()
