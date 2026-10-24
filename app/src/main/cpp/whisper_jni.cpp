// JNI híd a whisper.cpp-hez (hu.angolmagyar.fordito.WhisperNative).
// Egy kontextus = egy modell; egy kontextust egyszerre csak egy szál használhat.
#include <jni.h>
#include <atomic>
#include <cstdio>
#include <cstring>
#include <cstdint>
#include <string>
#include <vector>

#include "whisper.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AMWhisper", __VA_ARGS__)
#else
#define LOGI(...) do { fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); } while (0)
#endif

namespace {

struct Handle {
    whisper_context * ctx = nullptr;
    std::atomic<bool> abort_flag{false};
    // a tevékenységi visszahívásokhoz (csak a nTranscribe hívás ideje alatt érvényes)
    JNIEnv * env = nullptr;
    jobject listener = nullptr;
    jmethodID mid_progress = nullptr;
    jmethodID mid_segment = nullptr;
    int last_reported_segments = 0;
};

bool abort_cb(void * ud) {
    return static_cast<Handle *>(ud)->abort_flag.load();
}

void progress_cb(whisper_context *, whisper_state *, int progress, void * ud) {
    auto * h = static_cast<Handle *>(ud);
    if (h->env && h->listener && h->mid_progress) {
        h->env->CallVoidMethod(h->listener, h->mid_progress, (jint) progress);
        if (h->env->ExceptionCheck()) h->env->ExceptionClear();
    }
}

jbyteArray to_bytes(JNIEnv * env, const char * s) {
    size_t n = s ? strlen(s) : 0;
    jbyteArray arr = env->NewByteArray((jsize) n);
    if (n > 0) env->SetByteArrayRegion(arr, 0, (jsize) n, reinterpret_cast<const jbyte *>(s));
    return arr;
}

void segment_cb(whisper_context * ctx, whisper_state *, int n_new, void * ud) {
    auto * h = static_cast<Handle *>(ud);
    if (!h->env || !h->listener || !h->mid_segment) return;
    const int n = whisper_full_n_segments(ctx);
    for (int i = n - n_new; i < n; i++) {
        if (i < 0) continue;
        const int64_t t0 = whisper_full_get_segment_t0(ctx, i);
        const int64_t t1 = whisper_full_get_segment_t1(ctx, i);
        const char * text = whisper_full_get_segment_text(ctx, i);
        jbyteArray bytes = to_bytes(h->env, text);
        h->env->CallVoidMethod(h->listener, h->mid_segment, (jlong) t0, (jlong) t1, bytes);
        h->env->DeleteLocalRef(bytes);
        if (h->env->ExceptionCheck()) h->env->ExceptionClear();
    }
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_hu_angolmagyar_fordito_WhisperNative_nInit(JNIEnv * env, jclass, jstring path) {
    const char * p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context * ctx = whisper_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    if (!ctx) return 0;
    auto * h = new Handle();
    h->ctx = ctx;
    return reinterpret_cast<jlong>(h);
}

// Silero VAD (whisper.cpp): beszédszakaszok [t0,t1,...] centiszekundumban; null = hiba.
JNIEXPORT jfloatArray JNICALL
Java_hu_angolmagyar_fordito_WhisperNative_nVad(JNIEnv * env, jclass, jstring path, jfloatArray audio,
        jint threads, jfloat threshold, jint minSilenceMs) {
    const char * p = env->GetStringUTFChars(path, nullptr);
    whisper_vad_context_params cp = whisper_vad_default_context_params();
    cp.n_threads = threads;
    cp.use_gpu = false;
    whisper_vad_context * v = whisper_vad_init_from_file_with_params(p, cp);
    env->ReleaseStringUTFChars(path, p);
    if (!v) return nullptr;
    jsize n = env->GetArrayLength(audio);
    jfloat * a = env->GetFloatArrayElements(audio, nullptr);
    whisper_vad_params vp = whisper_vad_default_params();
    vp.threshold = threshold;
    vp.min_speech_duration_ms = 250;
    vp.min_silence_duration_ms = minSilenceMs;
    vp.speech_pad_ms = 100;
    whisper_vad_segments * segs = whisper_vad_segments_from_samples(v, vp, a, (int) n);
    env->ReleaseFloatArrayElements(audio, a, JNI_ABORT);
    if (!segs) { whisper_vad_free(v); return nullptr; }
    int k = whisper_vad_segments_n_segments(segs);
    std::vector<float> tmp((size_t) k * 2);
    for (int i = 0; i < k; i++) {
        tmp[(size_t) i * 2]     = whisper_vad_segments_get_segment_t0(segs, i);
        tmp[(size_t) i * 2 + 1] = whisper_vad_segments_get_segment_t1(segs, i);
    }
    whisper_vad_free_segments(segs);
    whisper_vad_free(v);
    jfloatArray out = env->NewFloatArray(k * 2);
    if (out && k > 0) env->SetFloatArrayRegion(out, 0, k * 2, tmp.data());
    return out;
}

JNIEXPORT void JNICALL
Java_hu_angolmagyar_fordito_WhisperNative_nFree(JNIEnv *, jclass, jlong handle) {
    auto * h = reinterpret_cast<Handle *>(handle);
    if (!h) return;
    if (h->ctx) whisper_free(h->ctx);
    delete h;
}

JNIEXPORT void JNICALL
Java_hu_angolmagyar_fordito_WhisperNative_nAbort(JNIEnv *, jclass, jlong handle, jboolean on) {
    auto * h = reinterpret_cast<Handle *>(handle);
    if (h) h->abort_flag.store(on == JNI_TRUE);
}

// Visszatérés: 0 = rendben, nem 0 = hiba / megszakítva
JNIEXPORT jint JNICALL
Java_hu_angolmagyar_fordito_WhisperNative_nTranscribe(
        JNIEnv * env, jclass, jlong handle, jint threads, jfloatArray audio,
        jstring lang, jstring prompt, jint beam, jobject listener) {
    auto * h = reinterpret_cast<Handle *>(handle);
    if (!h || !h->ctx) return -100;

    const jsize n = env->GetArrayLength(audio);
    jfloat * samples = env->GetFloatArrayElements(audio, nullptr);

    const char * lang_c = env->GetStringUTFChars(lang, nullptr);
    std::string lang_s = lang_c ? lang_c : "hu";
    env->ReleaseStringUTFChars(lang, lang_c);
    std::string prompt_s;
    if (prompt) {
        const char * pc = env->GetStringUTFChars(prompt, nullptr);
        if (pc) prompt_s = pc;
        env->ReleaseStringUTFChars(prompt, pc);
    }

    whisper_full_params p = whisper_full_default_params(
            beam > 1 ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY);
    if (beam > 1) p.beam_search.beam_size = beam;
    p.n_threads = threads > 0 ? threads : 4;
    p.language = lang_s.c_str();
    p.translate = false;
    p.detect_language = false;
    p.print_realtime = false;
    p.print_progress = false;
    p.print_timestamps = false;
    p.print_special = false;
    p.no_context = false;
    p.single_segment = false;
    p.token_timestamps = true;
    p.max_len = 84;
    p.split_on_word = true;
    p.suppress_blank = true;
    p.suppress_nst = true;
    p.no_speech_thold = 0.6f;
    p.initial_prompt = prompt_s.empty() ? nullptr : prompt_s.c_str();

    h->abort_flag.store(false);
    h->env = env;
    h->listener = listener;
    h->last_reported_segments = 0;
    if (listener) {
        jclass cls = env->GetObjectClass(listener);
        h->mid_progress = env->GetMethodID(cls, "onProgress", "(I)V");
        h->mid_segment = env->GetMethodID(cls, "onSegment", "(JJ[B)V");
        env->DeleteLocalRef(cls);
        p.progress_callback = progress_cb;
        p.progress_callback_user_data = h;
        p.new_segment_callback = segment_cb;
        p.new_segment_callback_user_data = h;
    }
    p.abort_callback = abort_cb;
    p.abort_callback_user_data = h;

    LOGI("whisper_full: %d minta, %d szál, nyelv=%s, beam=%d", (int) n, (int) p.n_threads, lang_s.c_str(), (int) beam);
    int rc = whisper_full(h->ctx, p, samples, (int) n);

    h->env = nullptr;
    h->listener = nullptr;
    env->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);
    return rc;
}

JNIEXPORT jint JNICALL
Java_hu_angolmagyar_fordito_WhisperNative_nSegCount(JNIEnv *, jclass, jlong handle) {
    auto * h = reinterpret_cast<Handle *>(handle);
    return h && h->ctx ? whisper_full_n_segments(h->ctx) : 0;
}

JNIEXPORT jlong JNICALL
Java_hu_angolmagyar_fordito_WhisperNative_nSegT0(JNIEnv *, jclass, jlong handle, jint i) {
    auto * h = reinterpret_cast<Handle *>(handle);
    return whisper_full_get_segment_t0(h->ctx, i);
}

JNIEXPORT jlong JNICALL
Java_hu_angolmagyar_fordito_WhisperNative_nSegT1(JNIEnv *, jclass, jlong handle, jint i) {
    auto * h = reinterpret_cast<Handle *>(handle);
    return whisper_full_get_segment_t1(h->ctx, i);
}

JNIEXPORT jbyteArray JNICALL
Java_hu_angolmagyar_fordito_WhisperNative_nSegText(JNIEnv * env, jclass, jlong handle, jint i) {
    auto * h = reinterpret_cast<Handle *>(handle);
    return to_bytes(env, whisper_full_get_segment_text(h->ctx, i));
}

JNIEXPORT jfloat JNICALL
Java_hu_angolmagyar_fordito_WhisperNative_nSegNoSpeech(JNIEnv *, jclass, jlong handle, jint i) {
    auto * h = reinterpret_cast<Handle *>(handle);
    return whisper_full_get_segment_no_speech_prob(h->ctx, i);
}

JNIEXPORT jstring JNICALL
Java_hu_angolmagyar_fordito_WhisperNative_nSystemInfo(JNIEnv * env, jclass) {
    return env->NewStringUTF(whisper_print_system_info());
}

} // extern "C"
