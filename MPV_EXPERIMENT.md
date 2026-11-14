# NexTV MPV Edition — isolated experiment

## Status (development, not certified for release)
- Native libmpv 1.0.0 AAR integrated with a SurfaceView controller, playback events, lifecycle, volume, scaling, picture and seek controls.
- Main native playback is routed through MPV; a dedicated package ID and launcher name avoid replacing other NexTV apps.
- The experiment includes an automatic APK artifact build and a manual pre-release workflow, both gated by compilation/tests and basic native library presence.
- **Not yet a fully single-engine APK**: inherited Media3/LibVLC dependencies and probe/preview code remain in the build. These require refactoring before claiming that only mpv is packaged or used everywhere.
- **Not yet verified**: no successful Android compilation or hardware TV playback validation has been confirmed here. Do not equate a source commit with a successful test.
- Required Android version: API 26 / Android 8.0 or later (libmpv 1.0.0 requirement).
- Source and bundled native libraries have distinct licenses. Review GPL-3.0-or-later obligations before distributing an APK.

## Goal
Use exactly one video playback engine: libmpv. Preserve authorized license checks, playlists, favorites, remote navigation, diagnostics, and clock. Keep existing stable and beta packages unchanged.

## Integration requirements
1. Choose an audited native libmpv build pipeline or versioned, verifiable binaries for Android ARM64 and required ABIs. Track licensing obligations and source/build provenance.
2. Integrate libmpv through JNI, modeled on the upstream mpv-android source (MPVLib, BaseMPVView, native bridge). mpv-android itself is not an importable AAR.
3. Implement video surface lifecycle, audio, player state, first-frame detection, buffering, error handling, headers and authentication, and video track metadata.
4. Replace runtime video engine selection and all video playback routes with libmpv. Do not silently fall back to Media3 or LibVLC in this edition.
5. Compare Android MediaCodec direct rendering versus GPU rendering with software/hardware decode choices for fidelity; preserve source colors as default, no artificial saturation.
6. Test MPEG-TS/HLS/DASH/MP4, video/audio codecs supported by target devices, subtitles, stream redirects, startup time, remote control and app lifecycle.
7. Pass compile/packaging tests, check APK native contents for libmpv and absence of other video engines, then perform TV compatibility tests.

## Security and release gates
- No live stream or licensing URL credentials in code, CI logs, documentation, or committed tests.
- Never bypass a missing, expired, or invalid license or parental lock.
- Do not run or publish an APK from this branch until real libmpv integration and verification succeeds.
- Performance and full codec compatibility are empirical, not guaranteed.
