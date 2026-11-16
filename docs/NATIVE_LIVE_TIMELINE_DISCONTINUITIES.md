# Native live-stream discontinuity handling

LibVLC streams can jump to a new media timeline when a live playlist rotates or switches segments. The native tracker now recognizes a sustained backwards time jump, rebases its internal reference, and counts progress only when time advances again. One stale regressing callback never counts as progress.

Fractional position values outside the valid 0–1 interval are ignored. Regressing position is treated as a reference reset, not as evidence of healthy playback.

Tests cover discontinuities and invalid positions. These source changes have not been compiled or run on Android TV. The three-version single-APK native integration is still outstanding.
