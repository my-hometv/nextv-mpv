# GStreamer Android native integration — verified boundaries

## Implemented
- Android NDK shared library `libnextv_gstreamer_probe.so`, linked only against `dl`.
- JNI entry point detects **actual** `libgstreamer-1.0.so` at runtime and resolves `gst_version` to report the version, or returns null if absent.
- Kotlin `GstreamerNativeProbe` catches native loading errors; no user credentials, URLs or headers are exposed.
- Legacy playback stays untouched while an official SDK distribution and JNI playback implementation are being integrated.

## Not implemented — must not be reported as completed
- GStreamer Android SDK and plugins are **not** packaged in the APK.
- GStreamer initialization, native `playbin` / `uridecodebin` pipelines, `ANativeWindow` video sink, bus events, buffering, HTTP headers, track selection, TV controls and playback have **not** been implemented.
- The new probe will report null on the current build. Its existence is not proof of video playback or image-quality improvement.

## Integration prerequisites
Obtain a reproducible Android GStreamer SDK distribution with license notices, supported ABIs, headers, libraries, plugin registry and plugin dependencies. Build and package a native pipeline bridge; then connect to the existing TV UI while preserving licensing and settings. Only after real playback passes CI and device tests should MPV/Media3/VLC code be removed.
