#include "llama_engine.h"
#include "llama.h"

#include <vector>
#include <mutex>
#include <algorithm>

#if defined(__ANDROID__)
#include <android/log.h>
#define LOG_TAG "BibleRAG-Llama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) do { printf("[INFO] " __VA_ARGS__); printf("\n"); } while(0)
#define LOGE(...) do { fprintf(stderr, "[ERROR] " __VA_ARGS__); fprintf(stderr, "\n"); } while(0)
#endif

struct LlamaEngine::Impl {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    const llama_vocab* vocab = nullptr;
    std::mutex mtx;

    Impl() {
        llama_backend_init();
    }

    ~Impl() {
        freeModel();
        llama_backend_free();
    }

    void freeModel() {
        std::lock_guard<std::mutex> lock(mtx);
        if (ctx) {
            llama_free(ctx);
            ctx = nullptr;
        }
        if (model) {
            llama_model_free(model);
            model = nullptr;
            vocab = nullptr;
        }
    }
};

LlamaEngine::LlamaEngine() : pImpl(new Impl()) {}

LlamaEngine::~LlamaEngine() {
    delete pImpl;
}

LlamaEngine& LlamaEngine::getInstance() {
    static LlamaEngine instance;
    return instance;
}

bool LlamaEngine::isLoaded() const {
    std::lock_guard<std::mutex> lock(pImpl->mtx);
    return (pImpl->model != nullptr && pImpl->ctx != nullptr);
}

void LlamaEngine::freeModel() {
    pImpl->freeModel();
    LOGI("LlamaEngine: модель успешно выгружена из памяти");
}

bool LlamaEngine::loadModel(const std::string& modelPath, int nCtx, int nThreads) {
    pImpl->freeModel();
    std::lock_guard<std::mutex> lock(pImpl->mtx);

    LOGI("LlamaEngine: Загрузка GGUF модели: %s", modelPath.c_str());

    llama_model_params mparams = llama_model_default_params();

    pImpl->model = llama_model_load_from_file(modelPath.c_str(), mparams);
    if (!pImpl->model) {
        LOGE("LlamaEngine: Не удалось загрузить GGUF модель из файла: %s", modelPath.c_str());
        return false;
    }

    pImpl->vocab = llama_model_get_vocab(pImpl->model);

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(nCtx > 0 ? nCtx : 2048);
    cparams.n_threads = static_cast<int32_t>(nThreads > 0 ? nThreads : 4);
    cparams.n_threads_batch = cparams.n_threads;
    cparams.n_batch = 512;

    pImpl->ctx = llama_init_from_model(pImpl->model, cparams);
    if (!pImpl->ctx) {
        LOGE("LlamaEngine: Не удалось создать контекст инференса llama");
        llama_model_free(pImpl->model);
        pImpl->model = nullptr;
        pImpl->vocab = nullptr;
        return false;
    }

    LOGI("LlamaEngine: Модель успешно инициализирована (n_ctx=%d, threads=%d)", nCtx, nThreads);
    return true;
}

bool LlamaEngine::generate(
    const std::string& prompt,
    int maxTokens,
    std::function<bool(const std::string& piece)> tokenCallback
) {
    std::lock_guard<std::mutex> lock(pImpl->mtx);
    if (!pImpl->model || !pImpl->ctx || !pImpl->vocab) {
        LOGE("LlamaEngine: Модель не загружена, генерация невозможна");
        return false;
    }

    // 1. Токенизация входного ChatML промпта
    std::vector<llama_token> tokens(prompt.length() + 64);
    int n_tokens = llama_tokenize(
        pImpl->vocab,
        prompt.c_str(),
        static_cast<int32_t>(prompt.length()),
        tokens.data(),
        static_cast<int32_t>(tokens.size()),
        true,  // add_special
        true   // parse_special
    );

    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(
            pImpl->vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.length()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            true,
            true
        );
    }

    if (n_tokens <= 0) {
        LOGE("LlamaEngine: Ошибка токенизации промпта");
        return false;
    }
    tokens.resize(n_tokens);

    LOGI("LlamaEngine: Промпт токенизирован: %d токенов", n_tokens);

    // Очистка KV кэша
    llama_memory_t mem = llama_get_memory(pImpl->ctx);
    if (mem) {
        llama_memory_clear(mem, true);
    }

    // 2. Инициализация сэмплера (температура 0.7, top-p 0.9, top-k 40)
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // 3. Обработка промпта батчами
    const int batch_size = 512;
    for (int i = 0; i < n_tokens; i += batch_size) {
        int cur_batch = std::min(batch_size, n_tokens - i);
        llama_batch batch = llama_batch_get_one(tokens.data() + i, cur_batch);
        if (llama_decode(pImpl->ctx, batch) != 0) {
            LOGE("LlamaEngine: Ошибка llama_decode при обработке промпта");
            llama_sampler_free(smpl);
            return false;
        }
    }

    // 4. Потоковая генерация новых токенов
    int generated = 0;
    while (generated < maxTokens) {
        llama_token new_token_id = llama_sampler_sample(smpl, pImpl->ctx, -1);
        llama_sampler_accept(smpl, new_token_id);

        if (llama_vocab_is_eog(pImpl->vocab, new_token_id)) {
            LOGI("LlamaEngine: Достигнут EOG маркер, генерация завершена");
            break;
        }

        // Преобразование токена в строку
        char piece_buf[256];
        int piece_len = llama_token_to_piece(pImpl->vocab, new_token_id, piece_buf, sizeof(piece_buf), 0, false);
        if (piece_len > 0) {
            std::string piece(piece_buf, piece_len);
            if (!tokenCallback(piece)) {
                LOGI("LlamaEngine: Генерация прервана клиентом");
                break;
            }
        }

        llama_batch batch = llama_batch_get_one(&new_token_id, 1);
        if (llama_decode(pImpl->ctx, batch) != 0) {
            LOGE("LlamaEngine: Ошибка llama_decode при шаге генерации токена %d", generated);
            break;
        }

        generated++;
    }

    llama_sampler_free(smpl);
    LOGI("LlamaEngine: Генерация завершена. Всего токенов: %d", generated);
    return true;
}
