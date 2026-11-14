#include <jni.h>
#include <dlfcn.h>
#include <cstring>

namespace {
using LibVlcVersion = const char* (*)();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_softppj_nextv_NativeLibVlcProbe_nativeRuntimeVersion(
    JNIEnv* env, jclass, jstring library) {
    if (!library) return nullptr;
    const char* name = env->GetStringUTFChars(library, nullptr);
    if (!name) return nullptr;
    // Never accept an arbitrary path or user-controlled dlopen target.
    // The probe is restricted to the native LibVLC shared object.
    const bool allowed = std::strcmp(name, "libvlc.so") == 0;
    if (!allowed) {
        env->ReleaseStringUTFChars(library, name);
        return nullptr;
    }
    void* handle = dlopen(name, RTLD_NOW | RTLD_LOCAL);
    env->ReleaseStringUTFChars(library, name);
    if (!handle) return nullptr;
    auto getVersion = reinterpret_cast<LibVlcVersion>(
        dlsym(handle, "libvlc_get_version"));
    jstring result = nullptr;
    if (getVersion) {
        const char* version = getVersion();
        if (version && version[0] != '\0') result = env->NewStringUTF(version);
    }
    dlclose(handle);
    return result;
}
