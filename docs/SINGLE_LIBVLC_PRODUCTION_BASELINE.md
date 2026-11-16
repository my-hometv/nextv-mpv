# Single-LibVLC production baseline

The production APK now uses **one Android application variant** and includes:
- Media3 ExoPlayer 1.11.1
- LibVLC 3.5.1

The three mutually exclusive Gradle flavors and their extra upstream LibVLC dependencies are removed. `BuildConfig.LIBVLC_VERSION` remains `3.5.1` for existing service code. The settings list only Media3 and LibVLC; the former version chooser is removed. No three-runtime verification task is part of the production build.

Existing source files for experimental isolated playback remain for future work, but must not be mistaken for three packaged native runtimes.

**Before declaring readiness:** inspect GitHub workflows for flavor-specific Gradle tasks, compile `:app:assembleDebug`, run `:app:testDebugUnitTest`, audit playback and recovery, then test on Android TV. No workflow or APK was triggered by this commit.
