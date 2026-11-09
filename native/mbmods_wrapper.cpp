#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include "water_reflections.h"
#define LOG_TAG "MBLoader-WaterFX"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
static void* g_original = nullptr;
static void load_original() {
    if (g_original) return;
    g_original = dlopen("libmods_original.so", RTLD_NOW | RTLD_LOCAL);
    if (!g_original)
        g_original = dlopen("/data/data/io.github.bambosan.mbloader/lib/libmods_original.so", RTLD_NOW | RTLD_LOCAL);
}
template <typename T> static T original(const char* name) {
    load_original();
    return g_original ? reinterpret_cast<T>(dlsym(g_original, name)) : nullptr;
}
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
    waterfx::install();
    LOGI("WaterFX wrapper loaded");
    return JNI_VERSION_1_6;
}
extern "C" JNIEXPORT void JNICALL Java_io_bambosan_mbloader_launcherUtils_NativeMods_customBuffers(JNIEnv* e,jclass c){
    using F=void(*)(JNIEnv*,jclass); if(auto f=original<F>("Java_io_bambosan_mbloader_launcherUtils_NativeMods_customBuffers")) f(e,c); waterfx::install();
}
extern "C" JNIEXPORT void JNICALL Java_io_bambosan_mbloader_launcherUtils_NativeMods_dynamicShadows(JNIEnv* e,jclass c){
    using F=void(*)(JNIEnv*,jclass); if(auto f=original<F>("Java_io_bambosan_mbloader_launcherUtils_NativeMods_dynamicShadows")) f(e,c); waterfx::install();
}
extern "C" JNIEXPORT void JNICALL Java_io_bambosan_mbloader_launcherUtils_NativeMods_dofBlur(JNIEnv* e,jclass c){
    using F=void(*)(JNIEnv*,jclass); if(auto f=original<F>("Java_io_bambosan_mbloader_launcherUtils_NativeMods_dofBlur")) f(e,c);
}
extern "C" JNIEXPORT void JNICALL Java_io_bambosan_mbloader_launcherUtils_NativeMods_fixGameLogo(JNIEnv* e,jclass c){
    using F=void(*)(JNIEnv*,jclass); if(auto f=original<F>("Java_io_bambosan_mbloader_launcherUtils_NativeMods_fixGameLogo")) f(e,c);
}
extern "C" JNIEXPORT void JNICALL Java_io_bambosan_mbloader_launcherUtils_NativeMods_forceCloseOreUi(JNIEnv* e,jclass c){
    using F=void(*)(JNIEnv*,jclass); if(auto f=original<F>("Java_io_bambosan_mbloader_launcherUtils_NativeMods_forceCloseOreUi")) f(e,c);
}
