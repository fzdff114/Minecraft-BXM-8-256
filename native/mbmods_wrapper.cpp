#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include "water_reflections.h"

#define LOG_TAG "MBLoader-WaterFX"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void* g_original = nullptr;

static bool load_original() {
    if (g_original) return true;

    // The patched package keeps the vendor library beside this wrapper under
    // the non-conflicting name libmods_original.so.
    g_original = dlopen("libmods_original.so", RTLD_NOW | RTLD_LOCAL);
    if (!g_original) {
        LOGE("Cannot load original MB Loader libmods.so: %s", dlerror());
        return false;
    }
    return true;
}

template <typename T>
static T original(const char* name) {
    if (!load_original()) return nullptr;
    return reinterpret_cast<T>(dlsym(g_original, name));
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    using OnLoadFn = jint (*)(JavaVM*, void*);
    const auto onLoad = original<OnLoadFn>("JNI_OnLoad");
    if (!onLoad) {
        LOGE("Original library does not export JNI_OnLoad");
        return JNI_ERR;
    }

    const jint version = onLoad(vm, reserved);
    if (version < JNI_VERSION_1_4) {
        LOGE("Original JNI_OnLoad failed: %d", version);
        return JNI_ERR;
    }

    // Initialize the optional WaterFX module only after the original loader
    // has initialized successfully. Unsupported graphics contexts remain safe.
    waterfx::install();
    LOGI("Original MB Loader native initialization forwarded; WaterFX probe initialized");
    return version;
}

#define FORWARD_VOID(method) \
extern "C" JNIEXPORT void JNICALL \
Java_io_bambosan_mbloader_launcherUtils_NativeMods_##method(JNIEnv* env, jclass cls) { \
    using Fn = void (*)(JNIEnv*, jclass); \
    if (auto fn = original<Fn>("Java_io_bambosan_mbloader_launcherUtils_NativeMods_" #method)) { \
        fn(env, cls); \
    } else { \
        LOGE("Missing original JNI method: " #method); \
    } \
}

FORWARD_VOID(customBuffers)
FORWARD_VOID(dynamicShadows)
FORWARD_VOID(dofBlur)
FORWARD_VOID(fixGameLogo)
FORWARD_VOID(forceCloseOreUi)
