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
2. **Legacy migration:** Direct Media3 and LibVLC Gradle dependencies, isolated VLC service, AIDL and associated unit tests were removed in commits through `5498231`. `PlaybackEnginePolicy.kt` still contains obsolete Media3/LibVLC selection semantics; confirm no live call sites and retire it with its legacy tests. GStreamer experimental source and JNI verification remain deliberately preserved, not a foreground player.
3. **Native licensing and ABI:** Inspect bundled libmpv/FFmpeg versions, supported ABIs, GPL compliance and source-distribution obligations before publishing.
4. **Physical Android TV acceptance:** Verify MPEG-TS and HLS, startup delay, 50/60 fps, audio sync, captions, remote-control navigation, VOD resume, transient network loss, alternate URL policy and memory/native-crash stability. CI does not establish these results.
5. **Release approval:** Build artifacts and pre-release publication remain separate from source verification; no APK should be generated before acceptance.

## Regression scenarios
- A previously confirmed working URL loses signal briefly: keep its selected URL, retry silently and never show a speculative alternative URL dialog.
- Playlist refresh replaces the channel at the same index: old MPV callbacks, watchdog and delayed recovery must not act on the replacement.
- A first unconfirmed automatic hardware decoder startup fails: attempt software only once; an explicit decoder selection must not be overridden.
- An SDR-only TV selects Force HDR10: fall back to Auto without overwriting the saved preference.
- A TV advertises HDR10: distinguish advertised capability from actual 10-bit HDR output; compare with Media3 on the physical TV.

**Status:** Source improvements committed; Android TV HDR output, dependency cleanup and real-device verification remain open.

## Current CI and device gate (2026-11-15)
- Commit `5498231`: MPV integration verification passed; source verification was still running at the last inspection. The earlier `a28cc6c` test compilation failures were due to removed VLC classes still referenced by obsolete tests; those tests were cleaned up in `5498231`.
- MPV native runtime now rejects all Media3 runtime modules and LibVLC/nextlib dependencies; the source gate rejects restoration of the removed VLC service and AIDL.
- **Not performed:** Physical Android TV testing, HDR10 signal negotiation, picture-quality comparison with Media3, long-running stability, and final native licensing/ABI publication review. CI success is not approval to ship an APK.
