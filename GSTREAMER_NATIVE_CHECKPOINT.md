# GStreamer native integration checkpoint

This commit corrects the standalone native Surface lifecycle and explicitly
selects a GStreamer video overlay sink before Android delivers its Surface.
It is **source-only** and is **not** evidence of working Android TV playback.

## Remaining integration blockers

1. Provision and verify the official GStreamer Android SDK for all intended ABIs.
2. Confirm an overlay-capable sink is packaged; `glimagesink` is only a candidate
   and can fail on devices without a compatible GL implementation.
3. Initialize GStreamer Android Java bindings and package native plugin assets.
4. Compile `app/src/main/jni/Android.mk` with the official SDK in CI.
5. Add bus error, buffering, first-frame, EOS and device decoder diagnostics.
6. Connect MainActivity only after real compilation and device playback tests.
7. Preserve all licensing, playlist, favourites, remote and non-engine settings.

Do not remove MPV/Media3 fallback source until the GStreamer-only APK has
passed equivalent regression tests. A green existing Gradle CI run does not
compile this standalone JNI adapter.
