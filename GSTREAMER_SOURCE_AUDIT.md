# GStreamer migration — source inventory (stage 01)

Reviewed the main app sources before replacing playback. This is an **inventory**, not a claim that GStreamer playback works.

## Playback integration to replace
- `MainActivity.kt` (about 10,001 lines): active MPV playback created in `startMpv` near line 8723; engine selection and picture settings still reference MPV.
- Same activity contains many **Media3 ExoPlayer** references: live playback fallback, background URL probes, previews, audio tracks, picture presets and YouTube quality. These are **not all dead code**; simply deleting Media3 dependencies breaks compilation and probing.
- Same activity contains **LibVLC** playback and probe paths. The manifest registers `IsolatedVlcPlaybackService`; VLC runtime is currently compile-only but legacy classes and tests remain.
- `MpvPlaybackView.kt`, `MpvPlaybackPolicy.kt` and their tests implement the active MPV adapter.
- `app/build.gradle.kts` packages `dev.jdtech.mpv:libmpv:1.0.0`, Media3 runtime artifacts and compile-only LibVLC. The old `verifyMpvRuntime` and three MPV GitHub workflows must be replaced only after GStreamer is compiled and CI-ready.
- GStreamer Android SDK binaries/JNI headers are **not currently present** in the repository. SDK integration and real-device testing are prerequisites for replacing the active playback engine.

## Protected contracts
- Shared preferences file `nextv`, independent application ID `com.softppj.nextv.mpv` (preserved to avoid breaking existing installed app identity).
- License server and signed offline license cache; device and token keys, expiry, RSA signature verification.
- `M3uParser` playlist parsing and header validation.
- Android TV leanback launcher, boot receiver, remote navigation and user preferences.

## Added safety gate
`gradle :app:verifyGstreamerPreservation` checks core source contracts and is wired into `check`. This is a **regression tripwire**, not a substitute for unit tests or device verification.

## Next substantial package
Acquire/verify an actual GStreamer Android SDK distribution with appropriate ABI and licenses; implement JNI/Surface integration and compile. No engine removal or false progress before native SDK integration.
