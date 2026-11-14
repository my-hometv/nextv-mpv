#include <jni.h>
#include <dlfcn.h>
#include <cstring>
#include <cstdio>

// Runtime capability probe only. No GStreamer SDK is bundled yet.
// This code deliberately never initializes a player or claims playback support.
extern "C" JNIEXPORT jstring JNICALL
Java_com_softppj_nextv_GstreamerNativeProbe_nativeRuntimeVersion(JNIEnv* env, jclass) {
    void* handle = dlopen("libgstreamer-1.0.so", RTLD_NOW | RTLD_LOCAL);
    if (!handle) return nullptr;
    using VersionString = void (*)(unsigned int*, unsigned int*, unsigned int*, unsigned int*);
    auto getVersion = reinterpret_cast<VersionString>(dlsym(handle, "gst_version"));
    if (!getVersion) { dlclose(handle); return nullptr; }
    unsigned int major = 0, minor = 0, micro = 0, nano = 0;
    getVersion(&major, &minor, &micro, &nano);
    dlclose(handle);
    char version[64];
    std::snprintf(version, sizeof(version), "%u.%u.%u.%u", major, minor, micro, nano);
    return env->NewStringUTF(version);
}
