#include <jni.h>
#include <android/native_window_jni.h>
#include <gst/gst.h>
#include <gst/video/videooverlay.h>
#include <mutex>
#include <string>
#include <atomic>

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
    std::atomic<bool> firstBuffer{false};
    bool firstBufferDelivered = false;
    int bufferingPercent = 100;
    double volume = 1.0;
    bool paused = false;
    gulong videoProbeId = 0;
    GstPad* videoProbePad = nullptr;
    bool waitingForSurface = false;
};
Player* player(jlong handle) { return reinterpret_cast<Player*>(handle); }
void stop(Player& p);  // Used by the surface-resume error path.
void setWindow(Player& p, JNIEnv* env, jobject surface) {
    ANativeWindow* next = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    ANativeWindow* previous = p.window;
    p.window = next;
    // A NULL handle must be delivered before the old window is released.
    // Otherwise an asynchronous sink may keep referencing freed native memory.
    if (p.pipeline && !p.window && !p.waitingForSurface) {
        if (gst_element_set_state(p.pipeline, GST_STATE_PAUSED) == GST_STATE_CHANGE_FAILURE) {
            stop(p);
            p.status = 3;
            p.error = "GStreamer failed to suspend playback after surface loss";
        } else {
            p.waitingForSurface = true;
        }
        p.firstBuffer.store(false, std::memory_order_release);
        p.firstBufferDelivered = false;
        p.status = 0;
    }
    if (p.sink && GST_IS_VIDEO_OVERLAY(p.sink)) {
        gst_video_overlay_set_window_handle(GST_VIDEO_OVERLAY(p.sink),
                                             reinterpret_cast<guintptr>(p.window));
        if (p.window) gst_video_overlay_expose(GST_VIDEO_OVERLAY(p.sink));
    }
    if (previous) ANativeWindow_release(previous);
    if (p.window && p.waitingForSurface && p.pipeline) {
        p.waitingForSurface = false;
        if (gst_element_set_state(p.pipeline,
                p.paused ? GST_STATE_PAUSED : GST_STATE_PLAYING) == GST_STATE_CHANGE_FAILURE) {
            stop(p);
            p.status = 3;
            p.error = "GStreamer failed to resume after surface creation";
        } else p.status = p.paused ? 0 : 1;
    }
}

GstPadProbeReturn onBuffer(GstPad*, GstPadProbeInfo* info, gpointer data) {
    auto* p = static_cast<Player*>(data);
    if (GST_PAD_PROBE_INFO_TYPE(info) & GST_PAD_PROBE_TYPE_BUFFER) {
        p->firstBuffer.store(true, std::memory_order_release);
    }
    return GST_PAD_PROBE_OK;
}
void stop(Player& p) {
    if (p.pipeline) {
        // Stop producing buffers before removing the callback. Do not release
        // the Player until the callback is detached from its owning pad.
        gst_element_set_state(p.pipeline, GST_STATE_NULL);
        if (p.videoProbePad) {
            if (p.videoProbeId != 0)
                gst_pad_remove_probe(p.videoProbePad, p.videoProbeId);
            gst_object_unref(p.videoProbePad);
            p.videoProbePad = nullptr;
            p.videoProbeId = 0;
        }
        // Hold our own sink reference while using the overlay and pad probe.
        // Release it only after the pipeline has stopped and the probe is gone.
        gst_object_unref(p.pipeline);
        p.pipeline = nullptr;
        if (p.sink) gst_object_unref(p.sink);
        p.sink = nullptr;
    }
    p.status = 0;
    p.firstBuffer.store(false, std::memory_order_release);
    p.firstBufferDelivered = false;
    p.bufferingPercent = 100;
    p.paused = false;
    p.waitingForSurface = false;
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
    if (!p->sink) {
        p->status = 3;
        p->error = "No GStreamer video sink plugin is available";
        stop(*p);
        p->status = 3;
        p->error = "No GStreamer video sink plugin is available";
        return;
    }
    if (!GST_IS_VIDEO_OVERLAY(p->sink)) {
        gst_object_unref(p->sink);
        p->sink = nullptr;
        stop(*p);
        p->status = 3;
        p->error = "Video sink does not support Android surface overlay";
        return;
    }
    if (p->sink) {
        // A SurfaceView may create its native surface asynchronously.
        // Configure the pipeline now and begin playback when it arrives.
        g_object_set(p->pipeline, "video-sink", p->sink, nullptr);
        // Keep the factory-owned sink reference until stop(), so overlay
        // operations cannot access a sink released by a playbin reconfiguration.
        if (p->window && GST_IS_VIDEO_OVERLAY(p->sink))
            gst_video_overlay_set_window_handle(GST_VIDEO_OVERLAY(p->sink),
                                                 reinterpret_cast<guintptr>(p->window));
        GstPad* pad = gst_element_get_static_pad(p->sink, "sink");
        if (pad) {
            p->videoProbeId = gst_pad_add_probe(pad, GST_PAD_PROBE_TYPE_BUFFER, onBuffer, p, nullptr);
            p->videoProbePad = pad;
        }
    }
    g_object_set(p->pipeline, "uri", url.c_str(), nullptr);
    g_object_set(p->pipeline, "volume", p->volume, nullptr);
    if (!p->window) {
        p->waitingForSurface = true;
        p->status = 0;
    } else if (gst_element_set_state(p->pipeline, GST_STATE_PLAYING) == GST_STATE_CHANGE_FAILURE) {
        stop(*p);
        p->status = 3;
        p->error = "GStreamer failed to start pipeline";
    } else p->status = 1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_softppj_nextv_GstreamerPlaybackView_nativePause(JNIEnv*, jobject, jlong h, jboolean paused) {
    auto* p = player(h); if (!p) return;
    std::lock_guard<std::mutex> guard(p->mutex);
    p->paused = paused;
    if (p->pipeline && !p->waitingForSurface && p->status != 2 && p->status != 3) {
        if (gst_element_set_state(p->pipeline,
                paused ? GST_STATE_PAUSED : GST_STATE_PLAYING) == GST_STATE_CHANGE_FAILURE) {
            stop(*p);
            p->status = 3;
            p->error = "GStreamer failed to change pause state";
        } else if (paused) {
            p->status = 0;
        }
    }
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
    p->volume = static_cast<gdouble>(volume) / 100.0;
    if (p->pipeline) g_object_set(p->pipeline, "volume", p->volume, nullptr);
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
                case GST_MESSAGE_BUFFERING: {
                    gint percent = 0;
                    gst_message_parse_buffering(msg, &percent);
                    p->bufferingPercent = percent;
                    if (p->status != 3 && p->status != 2 && !p->waitingForSurface)
                        p->status = percent < 100 ? 1 : (p->paused ? 0 : 4);
                    break;
                }
                case GST_MESSAGE_STATE_CHANGED:
                    if (GST_MESSAGE_SRC(msg) == GST_OBJECT(p->pipeline) &&
                        p->status != 3 && p->status != 2 && !p->waitingForSurface) {
                        GstState oldState, newState, pending;
                        gst_message_parse_state_changed(msg, &oldState, &newState, &pending);
                        if (newState == GST_STATE_PLAYING && p->bufferingPercent == 100)
                            p->status = 4;
                        else if (newState == GST_STATE_PAUSED && p->paused)
                            p->status = 0;
                    }
                    break;
                default: break;
            }
            gst_message_unref(msg);
        }
        gst_object_unref(bus);
    }
    if (p->status == 2 || p->status == 3) return p->status;
    if (p->firstBuffer.load(std::memory_order_acquire) &&
        !p->firstBufferDelivered && p->status != 3 && p->status != 2 &&
        !p->waitingForSurface) {
        p->firstBufferDelivered = true;
        return 5;
    }
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
