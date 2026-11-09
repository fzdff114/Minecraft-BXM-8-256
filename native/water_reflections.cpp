#include "water_reflections.h"
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <dlfcn.h>
#include <mutex>
#define LOG_TAG "MBLoader-WaterFX"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
namespace {
std::once_flag g_once;
bool g_enabled = true;
EGLBoolean (*real_eglSwapBuffers)(EGLDisplay, EGLSurface) = nullptr;
void resolve_swap() {
    void* sym = dlsym(RTLD_NEXT, "eglSwapBuffers");
    if (!sym) sym = dlsym(RTLD_DEFAULT, "eglSwapBuffers");
    real_eglSwapBuffers = reinterpret_cast<EGLBoolean (*)(EGLDisplay, EGLSurface)>(sym);
}
}
namespace waterfx {
void install() {
    std::call_once(g_once, [] {
        resolve_swap();
        if (!real_eglSwapBuffers) {
            LOGW("eglSwapBuffers unavailable; WaterFX disabled");
            g_enabled = false;
            return;
        }
        LOGI("WaterFX native module initialized");
    });
}
void set_enabled(bool enabled) { g_enabled = enabled; }
bool enabled() { return g_enabled; }
}
