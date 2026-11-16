# Native Binder generation isolation

The Activity's `playbackGeneration` is a UI lifecycle counter that can reset on Activity recreation. The isolated native service can survive Activity recreation and retain its generation high-water mark. Passing the UI counter directly to the service could reject valid new playback.

A process-scoped `NativePlaybackGenerationAllocator` now supplies strictly increasing Long IDs based on a monotonic clock, with duplicate/regressing clock protection. The Activity keeps its original UI generation for stale callback checks. Unit tests cover collisions, regressions and stale-stop rejection.

This corrects a native integration problem but **does not** produce the requested relocated LibVLC Java/JNI implementations. Native source builds, ABI verification and collision-free packaging are still required. No workflow or APK was run.
