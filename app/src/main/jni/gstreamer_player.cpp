#include <jni.h>
#include <gst/gst.h>
#include <gst/video/videooverlay.h>
#include <android/native_window_jni.h>
#include <mutex>
#include <string>

// GStreamer playback adapter. Compiled only with the official Android SDK.
// Each player owns a playbin, bus watch thread, and Android surface.
struct Player {
    GstElement *pipeline = nullptr;
    ANativeWindow *window = nullptr;
    std::mutex lock;
};
static Player* fromHandle(jlong value) { return reinterpret_cast<Player*>(value); }

extern "C" JNIEXPORT jlong JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativeCreate(JNIEnv*, jobject) {
    gst_init(nullptr, nullptr);
    auto *player = new Player();
    player->pipeline = gst_element_factory_make("playbin", nullptr);
    if (!player->pipeline) { delete player; return 0; }
    return reinterpret_cast<jlong>(player);
}
extern "C" JNIEXPORT void JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativeSetSurface(JNIEnv* env, jobject, jlong handle, jobject surface) {
    auto *p = fromHandle(handle);
    if (!p) return;
    std::lock_guard<std::mutex> guard(p->lock);
    if (p->window) { ANativeWindow_release(p->window); p->window = nullptr; }
    if (surface) p->window = ANativeWindow_fromSurface(env, surface);
    // playbin exposes the active sink only once it has been selected.
    if (p->window) {
        GstElement *sink = nullptr;
        g_object_get(p->pipeline, "video-sink", &sink, nullptr);
        if (sink) {
            if (GST_IS_VIDEO_OVERLAY(sink))
                gst_video_overlay_set_window_handle(GST_VIDEO_OVERLAY(sink),
                    reinterpret_cast<guintptr>(p->window));
            gst_object_unref(sink);
        }
    }
}
extern "C" JNIEXPORT void JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativePlay(JNIEnv* env, jobject, jlong handle, jstring url) {
    auto *p = fromHandle(handle);
    if (!p || !url) return;
    const char *value = env->GetStringUTFChars(url, nullptr);
    if (!value) return;
    {
        std::lock_guard<std::mutex> guard(p->lock);
        gst_element_set_state(p->pipeline, GST_STATE_READY);
        g_object_set(p->pipeline, "uri", value, nullptr);
        gst_element_set_state(p->pipeline, GST_STATE_PLAYING);
    }
    env->ReleaseStringUTFChars(url, value);
}
extern "C" JNIEXPORT void JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativePause(JNIEnv*, jobject, jlong handle, jboolean paused) {
    auto *p = fromHandle(handle);
    if (!p) return;
    std::lock_guard<std::mutex> guard(p->lock);
    gst_element_set_state(p->pipeline, paused ? GST_STATE_PAUSED : GST_STATE_PLAYING);
}
extern "C" JNIEXPORT void JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativeSeek(JNIEnv*, jobject, jlong handle, jlong ms) {
    auto *p = fromHandle(handle);
    if (!p) return;
    std::lock_guard<std::mutex> guard(p->lock);
    gst_element_seek_simple(p->pipeline, GST_FORMAT_TIME,
        static_cast<GstSeekFlags>(GST_SEEK_FLAG_FLUSH | GST_SEEK_FLAG_KEY_UNIT),
        static_cast<gint64>(ms) * GST_MSECOND);
}
extern "C" JNIEXPORT void JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativeVolume(JNIEnv*, jobject, jlong handle, jint volume) {
    auto *p = fromHandle(handle);
    if (!p) return;
    std::lock_guard<std::mutex> guard(p->lock);
    g_object_set(p->pipeline, "volume", static_cast<double>(volume) / 100.0, nullptr);
}
extern "C" JNIEXPORT void JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativeRelease(JNIEnv*, jobject, jlong handle) {
    auto *p = fromHandle(handle);
    if (!p) return;
    {
        std::lock_guard<std::mutex> guard(p->lock);
        gst_element_set_state(p->pipeline, GST_STATE_NULL);
        gst_object_unref(p->pipeline);
        if (p->window) ANativeWindow_release(p->window);
    }
    delete p;
}
