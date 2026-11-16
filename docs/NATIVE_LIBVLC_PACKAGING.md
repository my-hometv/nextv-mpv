# Single-APK LibVLC integration: required native packaging work

## Current implementation (not release-ready)

- Media3 is included in all variants.
- Gradle product flavors `vlc351`, `vlc377` and `vlc400eap29` each select **one** `libvlc-all` dependency.
- The isolated Binder service exposes `Surface` transport but has no native player adapter. It deliberately reports unavailable.
- The current flavors use distinct application IDs, so they produce separate APKs; this is **not** the requested single APK.

## Why merging the three AARs is not safe

Each upstream AAR contains overlapping `org.videolan.libvlc` Java classes and native libraries. Simply declaring all three dependencies in one variant leads to duplicate Java classes and/or native `.so` collisions. Distinct Android processes alone do not fix packaging collisions. Renaming only the Java packages does not repair native JNI registrations and dependencies.

## Required implementation milestones

1. Obtain reproducible source and native build toolchains for the exact three upstream versions; record commit hashes and licensing obligations.
2. Build each version with a distinct Java namespace, JNI registration namespace, native library names and dependent native library load paths. Verify Android ABIs and native dependencies with `readelf` and APK inspection.
3. Package all three relocated implementations into one application variant. Remove per-version application ID suffixes **only after** the runtime modules coexist. Keep Media3 independent.
4. Create one independently isolated Android service/process per native version, with a dedicated runtime adapter. Do not share native handles or classloaders across versions.
5. Implement native player lifecycle, video `Surface` attachment/detachment, playback errors, audio focus and crash recovery; report readiness only after native initialization succeeds.
6. Wire version-specific selection into `MainActivity`, and add instrumentation tests for switching, surface recreation, crashes, stream formats and audio/video sync.
7. Run compilation, unit tests, Android TV instrumentation and a complete release audit before calling the APK complete.

## Acceptance criteria

- Exactly one installable APK exposes Media3 plus all three selectable, independently initialized LibVLC engines.
- Every engine can render a real stream on a TV surface, release it, and switch without cross-version JNI crashes.
- Recovery and URL scanning work per engine without stale callbacks.
- A clean CI build passes; no unverified or missing native engine is advertised as available.

**Status:** These milestones are not satisfied. Do not treat the Binder interfaces or flavor declarations as evidence of packaged native engines.
