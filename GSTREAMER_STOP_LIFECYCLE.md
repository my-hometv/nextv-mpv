# GStreamer reusable stop lifecycle

Added stop() to the standalone Android player and nativeStop() to the JNI adapter. Stop transitions playbin to NULL, clears stale bus messages, errors, buffering and first-video-buffer state, and cancels Android polling with a generation change. The native player and Android Surface are retained so a later play(url) can reuse them without rebuilding the view.

The method is main-thread-only and safe to call after release (no native operation). This change does not enable GStreamer in MainActivity or prove on-device playback. Native compilation and runtime verification are required.
