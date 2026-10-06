#pragma once

#include <string>
#include <functional>

/**
 * Обертка над llama.cpp для мобильного инференса квантованных моделей GGUF (Qwen2.5-1.5B).
 */
class LlamaEngine {
public:
    static LlamaEngine& getInstance();

    bool loadModel(const std::string& modelPath, int nCtx = 2048, int nThreads = 4);
    void freeModel();
    bool isLoaded() const;

    /**
     * Потоковая генерация ответа.
     * @param prompt Форматированный входной промпт (ChatML)
     * @param maxTokens Лимит генерируемых токенов
     * @param tokenCallback Функция обратного вызова для каждого кусочка текста (piece).
     *                      Возврат false прерывает генерацию.
     * @return true при успешном завершении
     */
    bool generate(
        const std::string& prompt,
        int maxTokens,
        std::function<bool(const std::string& piece)> tokenCallback
    );

private:
    LlamaEngine();
    ~LlamaEngine();

    // Запрет копирования
    LlamaEngine(const LlamaEngine&) = delete;
    LlamaEngine& operator=(const LlamaEngine&) = delete;

    struct Impl;
    Impl* pImpl;
};
