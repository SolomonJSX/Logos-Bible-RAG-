#include "bible_rag_engine.h"
#include "llama_engine.h"
#include <jni.h>
#include <string>
#include <vector>

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_biblerag_core_BibleNativeEngine_nativeInit(
    JNIEnv* env,
    jobject /* thiz */,
    jstring j_db_path
) {
    if (!j_db_path) return 0;

    const char* path_chars = env->GetStringUTFChars(j_db_path, nullptr);
    std::string db_path(path_chars);
    env->ReleaseStringUTFChars(j_db_path, path_chars);

    auto* engine = new biblerag::BibleRagEngine();
    if (!engine->init(db_path)) {
        delete engine;
        return 0;
    }

    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT jobjectArray JNICALL
Java_com_biblerag_core_BibleNativeEngine_nativeSearch(
    JNIEnv* env,
    jobject /* thiz */,
    jlong engine_ptr,
    jfloatArray j_query_embedding,
    jstring j_query_text,
    jint top_k
) {
    auto* engine = reinterpret_cast<biblerag::BibleRagEngine*>(engine_ptr);
    if (!engine || !j_query_embedding || !j_query_text) {
        return nullptr;
    }

    jfloat* emb_elements = env->GetFloatArrayElements(j_query_embedding, nullptr);
    const char* query_chars = env->GetStringUTFChars(j_query_text, nullptr);
    std::string query_text(query_chars);

    auto results = engine->hybrid_search(emb_elements, query_text, top_k);

    env->ReleaseFloatArrayElements(j_query_embedding, emb_elements, JNI_ABORT);
    env->ReleaseStringUTFChars(j_query_text, query_chars);

    // Находим класс результата в Java/Kotlin
    jclass result_class = env->FindClass("com/biblerag/core/NativeSearchResult");
    if (!result_class) {
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(
        result_class,
        "<init>",
        "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;FIFIF)V"
    );
    if (!constructor) {
        return nullptr;
    }

    jobjectArray result_array = env->NewObjectArray(
        static_cast<jsize>(results.size()),
        result_class,
        nullptr
    );

    for (size_t i = 0; i < results.size(); ++i) {
        const auto& r = results[i];

        jstring j_ref = env->NewStringUTF(r.canonical_ref.c_str());
        jstring j_test = env->NewStringUTF(r.testament.c_str());
        jstring j_cat = env->NewStringUTF(r.category.c_str());
        jstring j_text = env->NewStringUTF(r.text.c_str());

        jobject obj = env->NewObject(
            result_class,
            constructor,
            r.chunk_id,
            j_ref,
            j_test,
            j_cat,
            j_text,
            r.rrf_score,
            r.dense_rank,
            r.dense_score,
            r.sparse_rank,
            r.sparse_score
        );

        env->SetObjectArrayElement(result_array, static_cast<jsize>(i), obj);

        env->DeleteLocalRef(j_ref);
        env->DeleteLocalRef(j_test);
        env->DeleteLocalRef(j_cat);
        env->DeleteLocalRef(j_text);
        env->DeleteLocalRef(obj);
    }

    return result_array;
}

JNIEXPORT void JNICALL
Java_com_biblerag_core_BibleNativeEngine_nativeClose(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong engine_ptr
) {
    auto* engine = reinterpret_cast<biblerag::BibleRagEngine*>(engine_ptr);
    if (engine) {
        delete engine;
    }
}

// -------------------------------------------------------------
// Llama.cpp GGUF инференс JNI методы
// -------------------------------------------------------------

JNIEXPORT jboolean JNICALL
Java_com_biblerag_core_BibleNativeEngine_nativeLoadLlama(
    JNIEnv* env,
    jobject /* thiz */,
    jstring j_model_path,
    jint n_ctx,
    jint n_threads
) {
    if (!j_model_path) return JNI_FALSE;
    const char* path_chars = env->GetStringUTFChars(j_model_path, nullptr);
    std::string model_path(path_chars);
    env->ReleaseStringUTFChars(j_model_path, path_chars);

    bool ok = LlamaEngine::getInstance().loadModel(model_path, n_ctx, n_threads);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_biblerag_core_BibleNativeEngine_nativeFreeLlama(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    LlamaEngine::getInstance().freeModel();
}

JNIEXPORT jboolean JNICALL
Java_com_biblerag_core_BibleNativeEngine_nativeIsLlamaLoaded(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    return LlamaEngine::getInstance().isLoaded() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_biblerag_core_BibleNativeEngine_nativeGenerateLlama(
    JNIEnv* env,
    jobject /* thiz */,
    jstring j_prompt,
    jint max_tokens,
    jobject j_callback
) {
    if (!j_prompt || !j_callback) return JNI_FALSE;

    const char* prompt_chars = env->GetStringUTFChars(j_prompt, nullptr);
    std::string prompt(prompt_chars);
    env->ReleaseStringUTFChars(j_prompt, prompt_chars);

    jclass callback_class = env->GetObjectClass(j_callback);
    jmethodID on_token_method = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)Z");
    if (!on_token_method) {
        return JNI_FALSE;
    }

    bool success = LlamaEngine::getInstance().generate(
        prompt,
        max_tokens,
        [env, j_callback, on_token_method](const std::string& piece) -> bool {
            jstring j_piece = env->NewStringUTF(piece.c_str());
            jboolean cont = env->CallBooleanMethod(j_callback, on_token_method, j_piece);
            env->DeleteLocalRef(j_piece);
            return (cont == JNI_TRUE);
        }
    );

    return success ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
