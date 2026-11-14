#include <jni.h>
#include <gst/gst.h>
#include <gst/video/videooverlay.h>
#include <android/native_window_jni.h>
#include <mutex>
#include <atomic>
#include <string>

// Standalone GStreamer playbin adapter; enabled only when the official SDK is packaged.
// Bus polling and playback callbacks must be integrated before routing live TV here.
struct Player {
    GstElement *pipeline = nullptr;
    GstElement *videoSink = nullptr;
    ANativeWindow *window = nullptr;
    std::mutex lock;
    GstBus *bus = nullptr;
    bool ended = false;
    bool buffering = false;
    bool playing = false;
    std::atomic<bool> videoBufferSeen{false};
    GstPad *videoSinkPad = nullptr;
    gulong videoProbeId = 0;
    std::string lastError;
};
static Player* fromHandle(jlong value) { return reinterpret_cast<Player*>(value); }

// A buffer reaching the video sink is a stronger signal than PLAYING, but
// it is not proof that Android displayed the frame on screen.
static GstPadProbeReturn onVideoBuffer(GstPad*, GstPadProbeInfo *info, gpointer userData) {
    auto *p = static_cast<Player*>(userData);
    if (info && (GST_PAD_PROBE_INFO_TYPE(info) & GST_PAD_PROBE_TYPE_BUFFER))
        p->videoBufferSeen.store(true, std::memory_order_release);
    return GST_PAD_PROBE_OK;
}

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
    p->videoSinkPad = gst_element_get_static_pad(p->videoSink, "sink");
    if (p->videoSinkPad) {
        p->videoProbeId = gst_pad_add_probe(p->videoSinkPad,
            GST_PAD_PROBE_TYPE_BUFFER, onVideoBuffer, p, nullptr);
    }
    p->bus = gst_element_get_bus(p->pipeline);
    if (!p->bus) {
        if (p->videoSinkPad) {
            if (p->videoProbeId) gst_pad_remove_probe(p->videoSinkPad, p->videoProbeId);
            gst_object_unref(p->videoSinkPad);
        }
        gst_object_unref(p->pipeline);
        gst_object_unref(p->videoSink);
        delete p;
        return 0;
    }
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
        p->ended = false;
        p->buffering = false;
        p->playing = false;
        p->videoBufferSeen.store(false, std::memory_order_release);
        p->lastError.clear();
        while (GstMessage *stale = gst_bus_pop(p->bus)) gst_message_unref(stale);
        g_object_set(p->pipeline, "uri", value, nullptr);
        const GstStateChangeReturn result = gst_element_set_state(p->pipeline, GST_STATE_PLAYING);
        if (result == GST_STATE_CHANGE_FAILURE)
            p->lastError = "GStreamer rejected the playback pipeline";
    }
    env->ReleaseStringUTFChars(url, value);
}

// Stop the current channel without destroying the Surface or player instance.
extern "C" JNIEXPORT void JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativeStop(JNIEnv*, jobject, jlong handle) {
    auto *p = fromHandle(handle);
    if (!p) return;
    std::lock_guard<std::mutex> guard(p->lock);
    gst_element_set_state(p->pipeline, GST_STATE_NULL);
    p->ended = false;
    p->buffering = false;
    p->playing = false;
    p->videoBufferSeen.store(false, std::memory_order_release);
    p->lastError.clear();
    while (GstMessage *stale = gst_bus_pop(p->bus)) gst_message_unref(stale);
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
        if (p->videoSinkPad) {
            if (p->videoProbeId) gst_pad_remove_probe(p->videoSinkPad, p->videoProbeId);
            gst_object_unref(p->videoSinkPad);
        }
        if (p->bus) gst_object_unref(p->bus);
        gst_object_unref(p->pipeline);
        gst_object_unref(p->videoSink);
    }
    delete p;
}

// Non-blocking bus status: 0=waiting, 1=buffering, 2=EOS, 3=error,
// 4=pipeline PLAYING, 5=video buffer reached sink (not proof of display).
// Caller polls from a worker or lifecycle-aware timer; never blocks the UI thread.
extern "C" JNIEXPORT jint JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativePollStatus(JNIEnv*, jobject, jlong handle) {
    auto *p = fromHandle(handle);
    if (!p) return 3;
    std::lock_guard<std::mutex> guard(p->lock);
    if (!p->bus) return 3;
    // 0=waiting, 1=buffering, 2=EOS, 3=error, 4=playing.
    int status = !p->lastError.empty() ? 3 : (p->ended ? 2 : (p->buffering ? 1 : (p->playing ? 4 : 0)));
    while (GstMessage *msg = gst_bus_pop(p->bus)) {
        switch (GST_MESSAGE_TYPE(msg)) {
            case GST_MESSAGE_ERROR: {
                GError *error = nullptr;
                gchar *debug = nullptr;
                gst_message_parse_error(msg, &error, &debug);
                p->lastError = error && error->message ? error->message : "GStreamer playback error";
                if (error) g_error_free(error);
                g_free(debug);
                p->buffering = false;
                status = 3;
                break;
            }
            case GST_MESSAGE_EOS:
                p->ended = true;
                p->buffering = false;
                if (p->lastError.empty()) status = 2;
                break;
            case GST_MESSAGE_BUFFERING: {
                gint percent = 100;
                gst_message_parse_buffering(msg, &percent);
                p->buffering = percent < 100;
                if (p->lastError.empty() && !p->ended) status = p->buffering ? 1 : (p->playing ? 4 : 0);
                break;
            }
            case GST_MESSAGE_ASYNC_DONE:
                // ASYNC_DONE alone is not proof of a decoded video frame.
                break;
            case GST_MESSAGE_STATE_CHANGED: {
                if (GST_MESSAGE_SRC(msg) == GST_OBJECT(p->pipeline)) {
                    GstState oldState, newState, pending;
                    gst_message_parse_state_changed(msg, &oldState, &newState, &pending);
                    (void)oldState;
                    (void)pending;
                    p->playing = newState == GST_STATE_PLAYING;
                    if (p->lastError.empty() && !p->ended && !p->buffering)
                        status = p->playing ? 4 : 0;
                }
                break;
            }
            default: break;
        }
        gst_message_unref(msg);
    }
    if ((status == 4 || status == 0) && p->videoBufferSeen.load(std::memory_order_acquire)) status = 5;
    return status;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativeLastError(JNIEnv* env, jobject, jlong handle) {
    auto *p = fromHandle(handle);
    if (!p) return env->NewStringUTF("GStreamer player released");
    std::lock_guard<std::mutex> guard(p->lock);
    return env->NewStringUTF(p->lastError.c_str());
}
