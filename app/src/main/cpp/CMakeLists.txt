cmake_minimum_required(VERSION 3.18)
project(angolmagyar_whisper C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# A Gradle debug build alapból -O0-val fordítana: a Whisper így használhatatlanul lassú lenne.
if (NOT CMAKE_BUILD_TYPE OR CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
endif()
set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG")

set(WH ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)

if (ANDROID AND ANDROID_ABI STREQUAL "arm64-v8a")
    # ARMv8.2 + dotprod + fp16: kb. 2018 utáni telefonokon fut (a Kotlin oldal futásidőben ellenőrzi).
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
endif()

add_subdirectory(${WH}/ggml ggml)

add_library(angolwhisper SHARED
    ${WH}/src/whisper.cpp
    whisper_jni.cpp)

target_include_directories(angolwhisper PRIVATE ${WH}/include ${WH}/ggml/include ${WH}/src)
target_compile_definitions(angolwhisper PRIVATE GGML_USE_CPU)
target_link_libraries(angolwhisper PRIVATE ggml)

if (ANDROID)
    find_library(LOG_LIB log)
    target_link_libraries(angolwhisper PRIVATE ${LOG_LIB})
    target_link_options(angolwhisper PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
    # Android 15: 16 KB-os oldalméret
    target_link_options(angolwhisper PRIVATE -Wl,-z,max-page-size=16384)
endif()
