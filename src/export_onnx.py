import os
import sys
from pathlib import Path
import torch
import torch.nn as nn
import torch.nn.functional as F
from transformers import AutoTokenizer, AutoModel
from onnxruntime.quantization import quantize_dynamic, QuantType

# Принудительно устанавливаем UTF-8 для вывода в Windows консоли
sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

MODEL_NAME = "cointegrated/rubert-tiny2"
OUTPUT_DIR = Path("data/output")


class BertEmbeddingWrapper(nn.Module):
    """
    Обертка, которая выполняет инференс BERT,
    затем Mean Pooling по токенам и L2-нормализацию.
    На выходе: готовый нормализованный вектор предложения размерности 312.
    """
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, input_ids, attention_mask, token_type_ids):
        outputs = self.model(
            input_ids=input_ids,
            attention_mask=attention_mask,
            token_type_ids=token_type_ids,
        )
        token_embeddings = outputs.last_hidden_state  # [batch_size, seq_len, 312]

        # Mean Pooling с учетом attention_mask
        input_mask_expanded = (
            attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float()
        )
        sum_embeddings = torch.sum(token_embeddings * input_mask_expanded, dim=1)
        sum_mask = torch.clamp(input_mask_expanded.sum(dim=1), min=1e-9)
        pooled = sum_embeddings / sum_mask  # [batch_size, 312]

        # L2-нормализация (чтобы косинусное сходство = скалярное произведение)
        normalized = F.normalize(pooled, p=2, dim=1)
        return normalized


def export_and_quantize():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    onnx_fp32_path = OUTPUT_DIR / "rubert_tiny2_fp32.onnx"
    onnx_int8_path = OUTPUT_DIR / "embedding_model_quantized.onnx"

    print(f"1. Загрузка модели и токенизатора: {MODEL_NAME}...")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    base_model = AutoModel.from_pretrained(MODEL_NAME)
    base_model.eval()

    # Оборачиваем модель
    model = BertEmbeddingWrapper(base_model)
    model.eval()

    print("2. Сохранение токенизатора и словаря (vocab.txt)...")
    tokenizer.save_pretrained(OUTPUT_DIR)

    # Тестовые данные для трассировки ONNX
    dummy_text = "В начале сотворил Бог небо и землю."
    inputs = tokenizer(dummy_text, return_tensors="pt")

    print(f"3. Экспорт в ONNX FP32 (со встроенным Mean Pooling) -> {onnx_fp32_path}...")
    torch.onnx.export(
        model,
        (inputs["input_ids"], inputs["attention_mask"], inputs["token_type_ids"]),
        str(onnx_fp32_path),
        input_names=["input_ids", "attention_mask", "token_type_ids"],
        output_names=["sentence_embedding"],
        dynamic_axes={
            "input_ids": {0: "batch_size", 1: "sequence_length"},
            "attention_mask": {0: "batch_size", 1: "sequence_length"},
            "token_type_ids": {0: "batch_size", 1: "sequence_length"},
            "sentence_embedding": {0: "batch_size"},
        },
        opset_version=14,
        dynamo=False,
    )

    print(f"4. Динамическое квантование весов в INT8 -> {onnx_int8_path}...")
    quantize_dynamic(
        model_input=str(onnx_fp32_path),
        model_output=str(onnx_int8_path),
        weight_type=QuantType.QInt8,
    )

    # Удаляем промежуточную FP32 версию
    if onnx_fp32_path.exists():
        os.remove(onnx_fp32_path)

    int8_size_mb = onnx_int8_path.stat().st_size / (1024 * 1024)
    print("\n--------------------------------------------------")
    print(f"✓ Успешно! Квантованная модель: {onnx_int8_path}")
    print(f"✓ Итоговый вес модели: {int8_size_mb:.2f} МБ")
    print("--------------------------------------------------")


if __name__ == "__main__":
    export_and_quantize()