package com.softppj.nextv;

import android.view.Surface;

// Version-specific native playback IPC. Readiness must be established by
// the actual relocated native runtime, never by an engine version label.
interface IIsolatedVlcPlayback {
    boolean isRuntimeReady(String engineId);
    // Legacy transport; remains fail-closed until an isolated runtime exists.
    boolean start(String engineId, String url, long generation);
    // Surface transport required for video from a remote playback process.
    // The service must hold and release its own Surface reference and must
    // reject an obsolete generation before attaching native video output.
    boolean startWithSurface(String engineId, String url, long generation, in Surface surface);
    // 0=idle/stale, 1=starting, 2=playing, 3=error, 4=ended.
    int playbackState(long generation);
    // Monotonic count of native time/position advancement callbacks.
    long playbackProgressCount(long generation);
    void stop(long generation);
    void release();
}
