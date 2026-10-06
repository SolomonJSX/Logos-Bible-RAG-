package com.biblerag.engine

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.util.Log

/**
 * Менеджер памяти устройства: следит за Low Memory сигналами Android
 * и предотвращает аварийное завершение процесса (LMK - Low Memory Killer).
 */
class MemoryManager(
    private val onClearCache: () -> Unit,
    private val onReleaseHeavyResources: () -> Unit
) : ComponentCallbacks2 {

    companion object {
        private const val TAG = "BibleRagMemoryManager"
    }

    override fun onTrimMemory(level: Int) {
        Log.w(TAG, "onTrimMemory level: $level")
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "Критическая нехватка памяти: полное освобождение кэшей и моделей")
                onClearCache()
                onReleaseHeavyResources()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Log.i(TAG, "Приложение в фоне или память на исходе: сброс KV-кэша LLM")
                onClearCache()
            }
        }
    }

    override fun onLowMemory() {
        Log.e(TAG, "onLowMemory: принудительная очистка всех тяжелых ресурсов!")
        onClearCache()
        onReleaseHeavyResources()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}
}
