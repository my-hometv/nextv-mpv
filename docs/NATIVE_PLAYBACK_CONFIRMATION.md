# Native playback confirmation and Surface lifecycle fixes

Previously the UI called `rememberWorking` as soon as LibVLC reported Playing, even if the stream never advanced a frame or timestamp. The UI now confirms a working native stream only after a positive progress counter is observed. This avoids polluting the remembered working-link selection with blank streams.

The startup watchdog now begins when a Surface-backed native session actually starts, rather than when the Surface callback is registered. A delayed Surface creation no longer consumes the native startup timeout.

The recovery callback is guarded against duplicate entry, and the watchdog is discarded when the Surface is destroyed. Tests cover confirmation, premature progress and first-progress timeout.

No workflow or APK generated. This is not the three-version native coexistence solution.
