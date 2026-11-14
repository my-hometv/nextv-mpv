#include <jni.h>
#include <gst/gst.h>
#include <gst/video/videooverlay.h>
#include <android/native_window_jni.h>
#include <mutex>

// Standalone GStreamer playbin adapter; enabled only when the official SDK is packaged.
// Bus polling and playback callbacks must be integrated before routing live TV here.
struct Player {
    GstElement *pipeline = nullptr;
    GstElement *videoSink = nullptr;
    ANativeWindow *window = nullptr;
    std::mutex lock;
};
static Player* fromHandle(jlong value) { return reinterpret_cast<Player*>(value); }

extern "C" JNIEXPORT jlong JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativeCreate(JNIEnv*, jobject) {
    gst_init(nullptr, nullptr);
    auto *p = new Player();
    p->pipeline = gst_element_factory_make("playbin", nullptr);
    p->videoSink = gst_element_factory_make("glimagesink", nullptr);
    // An explicitly selected overlay sink is necessary: playbin's automatically
    // selected sink may not exist when Android first delivers the Surface.
    if (!p->pipeline || !p->videoSink || !GST_IS_VIDEO_OVERLAY(p->videoSink)) {
        if (p->videoSink) gst_object_unref(p->videoSink);
        if (p->pipeline) gst_object_unref(p->pipeline);
        delete p;
        return 0;
    }
    g_object_set(p->pipeline, "video-sink", p->videoSink, nullptr);
    // playbin owns a reference; keep our own until teardown.
    return reinterpret_cast<jlong>(p);
}

extern "C" JNIEXPORT void JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativeSetSurface(JNIEnv* env, jobject, jlong handle, jobject surface) {
    auto *p = fromHandle(handle);
    if (!p) return;
    std::lock_guard<std::mutex> guard(p->lock);
    ANativeWindow *next = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    // Clear the previous overlay handle before releasing its ANativeWindow.
    gst_video_overlay_set_window_handle(GST_VIDEO_OVERLAY(p->videoSink),
        reinterpret_cast<guintptr>(next));
    gst_video_overlay_expose(GST_VIDEO_OVERLAY(p->videoSink));
    if (p->window) ANativeWindow_release(p->window);
    p->window = next;
}

extern "C" JNIEXPORT void JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativePlay(JNIEnv* env, jobject, jlong handle, jstring url) {
    auto *p = fromHandle(handle);
    if (!p || !url) return;
    const char *value = env->GetStringUTFChars(url, nullptr);
    if (!value) return;
    {
        std::lock_guard<std::mutex> guard(p->lock);
        gst_element_set_state(p->pipeline, GST_STATE_NULL);
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
        gst_video_overlay_set_window_handle(GST_VIDEO_OVERLAY(p->videoSink), 0);
        if (p->window) ANativeWindow_release(p->window);
        gst_object_unref(p->pipeline);
        gst_object_unref(p->videoSink);
    }
    delete p;
}
