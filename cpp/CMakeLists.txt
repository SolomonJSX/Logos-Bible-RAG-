cmake_minimum_required(VERSION 3.18)
project(bible_rag_core VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 99)

# SQLite compilation definitions: включаем FTS5 и оптимизации
add_definitions(
    -DSQLITE_ENABLE_FTS5=1
    -DSQLITE_THREADSAFE=1
    -DSQLITE_OMIT_LOAD_EXTENSION=1
    -DSQLITE_DEFAULT_MEMSTATUS=0
)

# Оптимизации компилятора
if(CMAKE_CXX_COMPILER_ID MATCHES "Clang|GNU")
    add_compile_options(-O3 -Wall -Wextra)
    if(ANDROID)
        if(ANDROID_ABI STREQUAL "arm64-v8a")
            add_compile_options(-march=armv8-a)
        endif()
    endif()
elseif(MSVC)
    add_compile_options(/O2 /W3)
endif()

# Опции llama.cpp для мобильной сборки
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
if(ANDROID AND ANDROID_ABI STREQUAL "x86_64")
    set(GGML_AVX OFF CACHE BOOL "" FORCE)
    set(GGML_AVX2 OFF CACHE BOOL "" FORCE)
    set(GGML_FMA OFF CACHE BOOL "" FORCE)
    set(GGML_F16C OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory(llama.cpp EXCLUDE_FROM_ALL)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
)

# Исходные файлы C++ ядра + встроенный SQLite3 + LlamaEngine
set(CORE_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/src/sqlite3.c
    ${CMAKE_CURRENT_SOURCE_DIR}/src/simd_math.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/src/rrf_ranker.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/src/bible_rag_engine.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/src/llama_engine.cpp
)

# 1. Статическая библиотека ядра
add_library(biblerag_core STATIC ${CORE_SOURCES})
target_include_directories(biblerag_core PUBLIC 
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
)
target_link_libraries(biblerag_core PUBLIC llama)

# 2. Динамическая JNI библиотека для Android (libbiblerag.so)
if(ANDROID OR BUILD_SHARED_LIBS)
    add_library(biblerag SHARED
        ${CORE_SOURCES}
        ${CMAKE_CURRENT_SOURCE_DIR}/src/jni_bridge.cpp
    )
    target_include_directories(biblerag PUBLIC 
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
        ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
    )
    target_link_libraries(biblerag PRIVATE llama)

    if(ANDROID)
        target_link_options(biblerag PRIVATE "-Wl,-z,max-page-size=16384")
    endif()

    find_library(log-lib log)
    if(log-lib)
        target_link_libraries(biblerag PRIVATE ${log-lib})
    endif()
    message(STATUS "Настроена сборка Android JNI библиотеки libbiblerag.so с llama.cpp")
endif()

# 3. Тестовый бинарник для десктопа (если не под Android)
if(NOT ANDROID)
    add_executable(test_bible_rag
        ${CMAKE_CURRENT_SOURCE_DIR}/tests/test_engine.cpp
    )
    target_link_libraries(test_bible_rag PRIVATE biblerag_core)
endif()
