# NexTV GStreamer Edition — Autonomous Development Flow

Repository: `my-hometv/nextv-mpv`, branch `main`. Reuse this independent app; do not create a new repository or change `my-hometv/nextv`.

## Non-negotiable invariants
- Preserve licensing, activation, network licensing, storage keys, preferences, playlist parsing/persistence, favourites, channel settings, remote mappings and other user settings unchanged.
- Keep existing stable app untouched and keep the independent Android application ID unless a verified install/update requirement dictates otherwise.
- GStreamer must be the **only** final playback engine. Remove MPV, Media3 and VLC native dependencies, unused code and settings after their integration points have been replaced. Do not leave hidden fallback players.
- Never invent a GStreamer Maven coordinate, SDK binary, compilation result, picture-quality result or successful APK.
- Preserve credentials: never log IPTV URLs, tokens, headers or license secrets.

## Ordered implementation stages and acceptance gates
- [ ] **01 Source inventory and baseline:** enumerate MPV/Media3/VLC references, call sites, native libraries, build/workflow files, license and settings regression surfaces. Commit audit and preservation tests.
- [ ] **02 GStreamer Android SDK:** select a verifiable, redistributable GStreamer Android SDK, document version and licenses, integrate native SDK and supported ABIs; compile JNI bridge. Commit and CI check.
- [ ] **03 GStreamer playback adapter:** surface lifecycle, live network streams, headers, buffering, playback state, error callbacks, first-frame detection, play/pause/seek. Commit and compile/test.
- [ ] **04 UI integration:** channel switching, audio/subtitle tracks, volume, picture controls, aspect/zoom, decoder diagnostics, remote navigation; preserve all existing settings keys. Commit and compile/test.
- [ ] **05 Fidelity and compatibility:** MPEG-TS/HLS, H.264/H.265, SD/HD/FHD, interlacing, colours, audio sync, dropped frames and channel startup; implement testable fixes with evidence. Commit and CI check.
- [ ] **06 Remove legacy engines:** delete MPV/Media3/VLC code, runtime/compile dependencies, unused settings and dead tests only after equivalent GStreamer paths compile; enforce dependency/packaging audit. Commit and CI check.
- [ ] **07 Final audit:** full Gradle compile, unit/regression tests, Android native binary packaging, licensing and settings preservation. Fix failures before APK generation. Commit final fixes.
- [ ] **08 TV test APK:** build an independent APK only after stage 07 succeeds. Verify real device, especially previously degraded Full HD channel; record actual result rather than assume quality.

## Manual development rules
1. Work only when the user requests continuation; the automatic schedule is disabled.
2. Read this file, `GSTREAMER_MIGRATION.md`, and `GSTREAMER_PROGRESS.md` before changes.
3. Group work into substantial, coherent code and test commits rather than many trivial commits.
4. Verify compilation/CI and update `GSTREAMER_PROGRESS.md` with earned completion percentage, SHA and next steps.
5. Report results to the user after each package; ask only for blockers or TV-only validation.
6. Do not trigger an APK workflow until verification gates are satisfied.

## Current baseline
The prior MPV integration verification passed at `3cc281b61118da2efc626f7f804a58cd5a24bef9`. GStreamer is not yet integrated; no claim of playback or picture-quality improvement is warranted.
