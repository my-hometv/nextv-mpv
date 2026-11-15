# NexTV MPV consolidated playback audit

## Verified engineering baseline
- Single foreground native playback path invokes `MpvPlaybackView` backed by `dev.jdtech.mpv:libmpv:1.0.0`.
- Automatic decoder uses MediaCodec copy-back, with a bounded software first-tune fallback.
- Confirmed channels retry quietly after a temporary failure; a stored working or manual URL does not require a legacy engine pin.
- Delayed playback, retry and decoder fallback callbacks now verify the original channel object, screen and playback generation, preventing an old playlist's callback from acting on a replacement.
- Source transfer metadata is reported separately from requested output and display HDR capabilities.
- Verification-only workflows compile Kotlin, run regression checks and check native library packaging without generating an APK.

## Outstanding release blockers — do not claim 100% or ship
1. **True HDR presentation:** The Android SurfaceView currently requests `PixelFormat.RGBA_8888`. The MPV Android OpenGL ES output has not been proven to negotiate HDR10/10-bit buffers or Android HDR metadata. Requesting `target-trc=pq` is **not** proof of HDR10 output. Test actual TV HDR signalling, SDR tone mapping and Surya TV HDR10 picture quality. Do not force 10-bit pixel formats without device/backend compatibility testing.
2. **Legacy migration:** Direct Media3 and LibVLC Gradle dependencies, isolated VLC service, AIDL and associated unit tests were removed in commits through `5498231`. The obsolete `PlaybackEnginePolicy.kt` and its legacy tests were removed in commit `0f802ed`. Check remaining UI labels and resources separately; GStreamer experimental source and JNI verification remain deliberately preserved, not a foreground player. GStreamer experimental source and JNI verification remain deliberately preserved, not a foreground player.
3. **Native licensing and ABI:** Inspect bundled libmpv/FFmpeg versions, supported ABIs, GPL compliance and source-distribution obligations before publishing.
4. **Physical Android TV acceptance:** Verify MPEG-TS and HLS, startup delay, 50/60 fps, audio sync, captions, remote-control navigation, VOD resume, transient network loss, alternate URL policy and memory/native-crash stability. CI does not establish these results.
5. **Release approval:** Build artifacts and pre-release publication remain separate from source verification; no APK should be generated before acceptance.

## Regression scenarios
- A previously confirmed working URL loses signal briefly: keep its selected URL, retry silently and never show a speculative alternative URL dialog.
- Playlist refresh replaces the channel at the same index: old MPV callbacks, watchdog and delayed recovery must not act on the replacement.
- A first unconfirmed automatic hardware decoder startup fails: attempt software only once; an explicit decoder selection must not be overridden.
- An SDR-only TV selects Force HDR10: fall back to Auto without overwriting the saved preference.
- A TV advertises HDR10: distinguish advertised capability from actual 10-bit HDR output; compare with Media3 on the physical TV.

**Status:** Source migration cleanup verified by CI through `0f802ed`; actual Android TV HDR output, licensing review and real-device acceptance remain open.

## Current CI and device gate (2026-11-15)
- Commit `0f802ed`: MPV integration verification and source verification both passed. Commit `28bb944` passed all three verification workflows. Commit `5498231` passed both integration and source verification. The earlier `a28cc6c` test compilation failures were due to removed VLC classes still referenced by obsolete tests; those tests were cleaned up in `5498231`.
- MPV native runtime now rejects all Media3 runtime modules and LibVLC/nextlib dependencies; the source gate rejects restoration of the removed VLC service and AIDL.
- **Not performed:** Physical Android TV testing, HDR10 signal negotiation, picture-quality comparison with Media3, long-running stability, and final native licensing/ABI publication review. CI success is not approval to ship an APK.

## Engineering review — source-only checks (2026-11-15)
- Playback defaults and stale preference normalization are now centralized in `MpvPlaybackPolicy`, with regression coverage for first-run and migrated legacy values. Per-channel manual overrides remain available.
- URL background probes use the HTTP-only transport checker, exclude foreground alternatives and do not assert successful decoding from an HTTP response. Device tests still needed for short signal loss and alternative prompts.
- MPV HDR rendering uses Android `RGBA_8888` and GPU/OpenGL ES. This is **not verified 10-bit HDR presentation**; retain Auto as the safe first-run default and avoid unconditional PQ or 10-bit surface forcing.
- Build dependency: `dev.jdtech.mpv:libmpv:1.0.0` is annotated GPL-3.0-or-later in Gradle. A source audit alone cannot certify the transitive native FFmpeg/mpv license set, exact binary ABIs, attribution, corresponding-source availability or redistribution obligations. Before publication, inspect the resolved AAR and dependency graph, ABI inventory, licenses and source-offer requirements; consult qualified licensing review if distributing binaries.
- GStreamer optional SDK verification remains in the project, and SDK-enabled builds restrict ABI to arm64-v8a. Do not assume that filter proves the libmpv AAR supports all target TV ABIs.
