# Native playback recovery watchdog

Replaced callback-count startup timeout and progress-dependent stall detection with a deterministic, elapsed-time watchdog. A native engine that emits `Playing` but never advances playback is now recovered after 20 seconds rather than being left indefinitely on a blank/frozen screen.

- Startup: 15 seconds without a Playing event
- First progress: 20 seconds after Playing without any forward progress
- Stall: 30 seconds after the last forward progress
- Error, ended and disconnected states: immediate recovery

The watchdog is pure Kotlin and has deterministic unit tests for each timeout, repeated samples and terminal states. Timeouts are initial defaults; Android TV tests are still required to tune for slow streams.

This is a substantial playback recovery change, **not** a three-LibVLC-native-libraries packaging solution. No APK or workflow was started.
