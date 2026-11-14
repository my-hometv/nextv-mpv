#include <jni.h>
#include <android/native_window_jni.h>
#include <gst/gst.h>
#include <gst/video/videooverlay.h>
#include <mutex>
#include <string>
#include <memory>

// Requires the official GStreamer Android SDK and its transitive libraries.
// JNI calls are serialized with the same lock as the nonblocking bus poll.
namespace {
struct Player {
    std::mutex mutex;
    GstElement* pipeline = nullptr;
    GstElement* sink = nullptr;
    ANativeWindow* window = nullptr;
    std::string error;
    int status = 0;
    bool firstBuffer = false;
};
Player* player(jlong handle) { return reinterpret_cast<Player*>(handle); }
void setWindow(Player& p, JNIEnv* env, jobject surface) {
    ANativeWindow* next = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    if (p.window) ANativeWindow_release(p.window);
    p.window = next;
    if (p.sink && GST_IS_VIDEO_OVERLAY(p.sink) && p.window)
        gst_video_overlay_set_window_handle(GST_VIDEO_OVERLAY(p.sink),
                                             reinterpret_cast<guintptr>(p.window));
}
GstPadProbeReturn onBuffer(GstPad*, GstPadProbeInfo* info, gpointer data) {
    auto* p = static_cast<Player*>(data);
    if (GST_PAD_PROBE_INFO_TYPE(info) & GST_PAD_PROBE_TYPE_BUFFER) {
        std::lock_guard<std::mutex> guard(p->mutex);
        p->firstBuffer = true;
    }
    return GST_PAD_PROBE_OK;
}
void stop(Player& p) {
    if (p.pipeline) {
        gst_element_set_state(p.pipeline, GST_STATE_NULL);
        gst_object_unref(p.pipeline);
        p.pipeline = nullptr;
        p.sink = nullptr;
    }
    p.status = 0;
    p.firstBuffer = false;
    p.error.clear();
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativeCreate(JNIEnv*, jobject) {
    GError* error = nullptr;
    if (!gst_init_check(nullptr, nullptr, &error)) {
        if (error) g_error_free(error);
        return 0;
    }
    return reinterpret_cast<jlong>(new Player());
}

extern "C" JNIEXPORT void JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativeSetSurface(JNIEnv* env, jobject, jlong h, jobject surface) {
    auto* p = player(h);
    if (!p) return;
    std::lock_guard<std::mutex> guard(p->mutex);
    setWindow(*p, env, surface);
}

extern "C" JNIEXPORT void JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativePlay(JNIEnv* env, jobject, jlong h, jstring uri) {
    auto* p = player(h);
    if (!p || !uri) return;
    const char* chars = env->GetStringUTFChars(uri, nullptr);
    if (!chars) return;
    std::string url(chars);
    env->ReleaseStringUTFChars(uri, chars);
    std::lock_guard<std::mutex> guard(p->mutex);
    stop(*p);
    p->pipeline = gst_element_factory_make("playbin", nullptr);
    if (!p->pipeline) { p->status = 3; p->error = "GStreamer playbin plugin unavailable"; return; }
    p->sink = gst_element_factory_make("glimagesink", nullptr);
    if (!p->sink) p->sink = gst_element_factory_make("autovideosink", nullptr);
    if (p->sink) {
        g_object_set(p->pipeline, "video-sink", p->sink, nullptr);
        if (p->window && GST_IS_VIDEO_OVERLAY(p->sink))
            gst_video_overlay_set_window_handle(GST_VIDEO_OVERLAY(p->sink),
                                                 reinterpret_cast<guintptr>(p->window));
        GstPad* pad = gst_element_get_static_pad(p->sink, "sink");
        if (pad) {
            gst_pad_add_probe(pad, GST_PAD_PROBE_TYPE_BUFFER, onBuffer, p, nullptr);
            gst_object_unref(pad);
        }
    }
    g_object_set(p->pipeline, "uri", url.c_str(), nullptr);
    if (gst_element_set_state(p->pipeline, GST_STATE_PLAYING) == GST_STATE_CHANGE_FAILURE) {
        p->status = 3;
        p->error = "GStreamer failed to start pipeline";
    } else p->status = 1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativePause(JNIEnv*, jobject, jlong h, jboolean paused) {
    auto* p = player(h); if (!p) return;
    std::lock_guard<std::mutex> guard(p->mutex);
    if (p->pipeline) gst_element_set_state(p->pipeline, paused ? GST_STATE_PAUSED : GST_STATE_PLAYING);
}
extern "C" JNIEXPORT void JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativeStop(JNIEnv*, jobject, jlong h) {
    auto* p = player(h); if (!p) return;
    std::lock_guard<std::mutex> guard(p->mutex); stop(*p);
}
extern "C" JNIEXPORT void JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativeSeek(JNIEnv*, jobject, jlong h, jlong ms) {
    auto* p = player(h); if (!p) return;
    std::lock_guard<std::mutex> guard(p->mutex);
    if (p->pipeline) gst_element_seek_simple(p->pipeline, GST_FORMAT_TIME,
        static_cast<GstSeekFlags>(GST_SEEK_FLAG_FLUSH | GST_SEEK_FLAG_KEY_UNIT),
        static_cast<gint64>(ms) * GST_MSECOND);
}
extern "C" JNIEXPORT void JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativeVolume(JNIEnv*, jobject, jlong h, jint volume) {
    auto* p = player(h); if (!p) return;
    std::lock_guard<std::mutex> guard(p->mutex);
    if (p->pipeline) g_object_set(p->pipeline, "volume", static_cast<gdouble>(volume) / 100.0, nullptr);
}
extern "C" JNIEXPORT jint JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativePollStatus(JNIEnv*, jobject, jlong h) {
    auto* p = player(h); if (!p) return 3;
    std::lock_guard<std::mutex> guard(p->mutex);
    if (!p->pipeline) return p->status;
    GstBus* bus = gst_element_get_bus(p->pipeline);
    if (bus) {
        GstMessage* msg;
        while ((msg = gst_bus_pop_filtered(bus, static_cast<GstMessageType>(
            GST_MESSAGE_ERROR | GST_MESSAGE_EOS | GST_MESSAGE_BUFFERING | GST_MESSAGE_STATE_CHANGED)))) {
            switch (GST_MESSAGE_TYPE(msg)) {
                case GST_MESSAGE_ERROR: {
                    GError* err = nullptr; gchar* debug = nullptr;
                    gst_message_parse_error(msg, &err, &debug);
                    p->error = err ? err->message : "Unknown GStreamer error";
                    if (err) g_error_free(err);
                    g_free(debug);
                    p->status = 3;
                    break;
                }
                case GST_MESSAGE_EOS: p->status = 2; break;
                case GST_MESSAGE_BUFFERING:
                    if (p->status != 3) p->status = 1;
                    break;
                case GST_MESSAGE_STATE_CHANGED:
                    if (GST_MESSAGE_SRC(msg) == GST_OBJECT(p->pipeline) && p->status != 3) {
                        GstState oldState, newState, pending;
                        gst_message_parse_state_changed(msg, &oldState, &newState, &pending);
                        if (newState == GST_STATE_PLAYING) p->status = 4;
                    }
                    break;
                default: break;
            }
            gst_message_unref(msg);
        }
        gst_object_unref(bus);
    }
    if (p->firstBuffer && p->status != 3 && p->status != 2) return 5;
    return p->status;
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativeLastError(JNIEnv* env, jobject, jlong h) {
    auto* p = player(h); if (!p) return env->NewStringUTF("Player unavailable");
    std::lock_guard<std::mutex> guard(p->mutex);
    return env->NewStringUTF(p->error.c_str());
}
extern "C" JNIEXPORT void JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativeRelease(JNIEnv*, jobject, jlong h) {
    auto* p = player(h); if (!p) return;
    {
        std::lock_guard<std::mutex> guard(p->mutex);
        stop(*p);
        if (p->window) ANativeWindow_release(p->window);
        p->window = nullptr;
    }
    delete p;
}
