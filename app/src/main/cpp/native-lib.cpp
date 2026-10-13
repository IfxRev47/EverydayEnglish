#include <jni.h>
#include "llama.h"
#include <atomic>
#include <mutex>
#include <memory>
#include <string>
#include <vector>
#include <stdexcept>
#include <algorithm>
#include <cmath>

namespace {
std::mutex gate;
std::atomic<bool> stopped{false};
llama_model * model = nullptr;
llama_context * context = nullptr;
bool abort_decode(void *) { return stopped.load(); }
bool progress(float, void *) { return !stopped.load(); }
void release() {
    if (context) llama_free(context);
    if (model) llama_model_free(model);
    context = nullptr; model = nullptr;
}
void fail(JNIEnv * env, const char * message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
std::string bytes(JNIEnv * env, jbyteArray array) {
    std::string result(env->GetArrayLength(array), '\0');
    env->GetByteArrayRegion(array, 0, result.size(), reinterpret_cast<jbyte *>(result.data()));
    return result;
}
}
extern "C" JNIEXPORT void JNICALL Java_com_oxford_a1_ai_LlamaNative_reset(JNIEnv *, jobject) { stopped = false; }
extern "C" JNIEXPORT void JNICALL Java_com_oxford_a1_ai_LlamaNative_cancel(JNIEnv *, jobject) { stopped = true; }
extern "C" JNIEXPORT void JNICALL Java_com_oxford_a1_ai_LlamaNative_unload(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gate); release();
}
extern "C" JNIEXPORT void JNICALL Java_com_oxford_a1_ai_LlamaNative_load(JNIEnv * env, jobject, jbyteArray path, jint threads, jint context_size) {
    std::lock_guard<std::mutex> lock(gate);
    try {
        if (threads < 1 || threads > 8 || (context_size != 1024 && context_size != 2048 && context_size != 4096))
            throw std::runtime_error("INVALID_SETTINGS");
        release();
        llama_backend_init();
        auto params = llama_model_default_params();
        params.n_gpu_layers = 0;
        params.progress_callback = progress;
        model = llama_model_load_from_file(bytes(env, path).c_str(), params);
        if (!model) throw std::runtime_error("LOAD_FAILED");
        if (stopped) { release(); return; }
        auto cp = llama_context_default_params();
        cp.n_ctx = context_size; cp.n_batch = 128; cp.n_ubatch = 128;
        cp.n_threads = threads; cp.n_threads_batch = threads;
        cp.abort_callback = abort_decode;
        context = llama_init_from_model(model, cp);
        if (!context) throw std::runtime_error("CONTEXT_FAILED");
    } catch (const std::exception & e) { release(); fail(env, e.what()); }
    catch (...) { release(); fail(env, "NATIVE_FAILED"); }
}
extern "C" JNIEXPORT void JNICALL Java_com_oxford_a1_ai_LlamaNative_generate(JNIEnv * env, jobject, jobjectArray input, jbyteArray skill,
        jfloat temperature, jfloat top_p, jint top_k, jfloat repeat_penalty, jint max_tokens, jobject listener) {
    std::lock_guard<std::mutex> lock(gate);
    try {
        if (!model || !context) throw std::runtime_error("NOT_LOADED");
        if (!std::isfinite(temperature) || temperature < 0 || temperature > 1.5f ||
            !std::isfinite(top_p) || top_p < 0.1f || top_p > 1 || top_k < 1 || top_k > 100 ||
            !std::isfinite(repeat_penalty) || repeat_penalty < 1 || repeat_penalty > 1.5f || max_tokens < 64 || max_tokens > 768)
            throw std::runtime_error("INVALID_SETTINGS");
        std::vector<std::string> contents;
        int size = env->GetArrayLength(input);
        if (size < 1 || size > 5 || size % 2 == 0) throw std::runtime_error("INVALID_MESSAGES");
        for (int i = 0; i < size; ++i) {
            auto item = static_cast<jbyteArray>(env->GetObjectArrayElement(input, i));
            contents.push_back(bytes(env, item));
            env->DeleteLocalRef(item);
        }
        auto instruction = bytes(env, skill);
        const char * tmpl = llama_model_chat_template(model, nullptr);
        if (!tmpl) throw std::runtime_error("TEMPLATE_UNSUPPORTED");
        const auto * vocab = llama_model_get_vocab(model);
        std::vector<char> formatted(4096);
        int count = 0, length = 0;
        for (int first = 0; ; first += 2) {
            // Put instructions into the first retained user message for templates without a system role.
            std::string opening = instruction.empty() ? contents[first] :
                "/no_think\nFollow these instructions for this conversation:\n" + instruction +
                "\n\nUser message:\n" + contents[first];
            std::vector<llama_chat_message> messages;
            messages.push_back({"user", opening.c_str()});
            for (int i = first + 1; i < size; ++i)
                messages.push_back({i % 2 ? "assistant" : "user", contents[i].c_str()});
            length = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, formatted.data(), formatted.size());
            if (length > static_cast<int>(formatted.size())) {
                formatted.resize(length);
                length = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, formatted.data(), formatted.size());
            }
            if (length <= 0) throw std::runtime_error("TEMPLATE_UNSUPPORTED");
            count = -llama_tokenize(vocab, formatted.data(), length, nullptr, 0, true, true);
            if (count > 0 && count + max_tokens <= static_cast<int>(llama_n_ctx(context))) break;
            if (first + 2 >= size) throw std::runtime_error("PROMPT_TOO_LONG");
        }
        std::vector<llama_token> tokens(count);
        if (llama_tokenize(vocab, formatted.data(), length, tokens.data(), count, true, true) != count)
            throw std::runtime_error("TOKENIZE_FAILED");
        llama_memory_clear(llama_get_memory(context), true);
        for (int offset = 0; offset < count && !stopped; offset += 128) {
            auto batch = llama_batch_get_one(tokens.data() + offset, std::min(128, count - offset));
            if (llama_decode(context, batch) != 0) {
                if (stopped) return;
                throw std::runtime_error("DECODE_FAILED");
            }
        }
        auto sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>(
            llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
        if (temperature > 0) llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(top_k));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, repeat_penalty, 0.0f, 0.0f));
        if (temperature == 0) {
            llama_sampler_chain_add(sampler.get(), llama_sampler_init_greedy());
        } else {
            llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(top_p, 1));
            llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(temperature));
            llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        }
        // Repetition penalty includes the recent prompt, then generated tokens accepted by sample().
        for (int i = std::max(0, count - 64); i < count; ++i) llama_sampler_accept(sampler.get(), tokens[i]);
        auto callback = env->GetMethodID(env->GetObjectClass(listener), "onText", "([B)V");
        if (!callback) return;
        std::string output;
        for (int i = 0; i < max_tokens && !stopped; ++i) {
            auto token = llama_sampler_sample(sampler.get(), context, -1);
            if (llama_vocab_is_eog(vocab, token)) break;
            std::vector<char> piece(256);
            int n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
            if (n < 0) { piece.resize(-n); n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false); }
            if (n < 0) throw std::runtime_error("TOKENIZE_FAILED");
            output.append(piece.data(), n);
            auto result = env->NewByteArray(output.size());
            if (!result) return;
            env->SetByteArrayRegion(result, 0, output.size(), reinterpret_cast<const jbyte *>(output.data()));
            env->CallVoidMethod(listener, callback, result);
            env->DeleteLocalRef(result);
            if (env->ExceptionCheck()) return;
            auto batch = llama_batch_get_one(&token, 1);
            if (llama_decode(context, batch) != 0) {
                if (stopped) return;
                throw std::runtime_error("DECODE_FAILED");
            }
        }
    } catch (const std::exception & e) { fail(env, e.what()); }
    catch (...) { fail(env, "NATIVE_FAILED"); }
}
