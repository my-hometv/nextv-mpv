# Native playback progress monitoring

The isolated LibVLC service exposes a generation-scoped, monotonically increasing counter of native TimeChanged/PositionChanged callbacks. The TV Activity polls this alongside the native playback state. A counter increment updates the last-progress timestamp; repeated Playing state alone does **not** reset the stall timer. A confirmed session with observed progress and no further progress for 30 seconds enters existing recovery.

A stream that has not emitted any position/time callbacks is **not** automatically marked stalled, because some live streams do not publish meaningful time/position updates. A subsequent implementation should add native video frame rendering or decoder counters for stronger live-stream stall detection.

The counter is reset for each native session and returns -1 when disconnected or stale. This change does not solve the three-native-versions-in-one-APK requirement. Compile and device tests are still required.
