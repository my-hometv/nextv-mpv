# Native event terminal-state and timeline correction

Two native event-ordering issues are corrected:

1. A late EncounteredError can no longer overwrite a session that has already ended. Error and ended are mutually terminal until a new session starts.
2. Duplicate regressing TimeChanged values can no longer satisfy the two-sample timeline-discontinuity rule. Only consecutive forward-moving samples below the old timeline can rebase the reference. Regressions and pending resets are cleared on session reset.

These changes protect the existing playback watchdog against false progress after native callbacks are reordered or repeated. Added deterministic tests for terminal-event ordering and duplicate regressing timestamps.

No GitHub Actions, APK, or test execution was requested or performed. The separate LibVLC native versions still do not coexist in one APK.
