# Native watchdog terminal-state hardening

The watchdog now latches its first terminal result. A late Playing/progress event cannot resurrect a generation that has already timed out, ended, or failed. Unknown native state codes fail closed. Stale elapsed-time samples are ignored, and timeout arithmetic saturates on overflow. Timeout configuration must be positive.

This prevents repeated asynchronous callbacks from misclassifying an already-failed native stream as healthy. Unit tests were added for terminal stickiness, invalid states, non-monotonic timestamps and invalid configuration.

No workflow or APK was triggered. Compilation and physical Android TV behavior remain unverified. The three-version single-APK LibVLC requirement remains outstanding.
