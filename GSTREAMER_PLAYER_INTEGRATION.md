# GStreamer native playback package — integration status

This package adds a native `playbin` player implementation and a SurfaceView Kotlin adapter with JNI calls for create, surface attach/detach, play, pause/resume, seek, volume and release. It includes an Android NDK `Android.mk` based on the official GStreamer Android SDK packaging pattern.

**Not enabled in MainActivity**: existing TV controls, signed licensing, playlists and MPV playback remain unchanged. The native target requires the official SDK at `GSTREAMER_ROOT_ANDROID` and is **not** built by the existing Gradle/CMake workflow. It has not been compiled in CI and is not yet proven to render video.

## Critical outstanding integration work
1. Download and pin official Android universal SDK, with hash/license and ABI support; provision it in CI.
2. Build `nextv_gst_player` and `gstreamer_android` using ndk-build, package binaries and required plugins, initialize the SDK's Java native support.
3. Implement bus/error/EOS/first-frame callbacks, buffering and thread-safe lifecycle; test surface recreation.
4. Implement authenticated HTTP headers, audio/subtitle selection, aspect and picture controls, live transport compatibility and decoder diagnostics.
5. Wire adapter into MainActivity only after it is buildable, remove MPV/Media3/VLC only after replacement and verification.

Do not count the above work as complete or claim 50% progress. In particular the current JNI player is a **source-level starting point**, not a tested production playback engine.
