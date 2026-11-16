# Single-select LibVLC engine setting

Settings > Enabled playback engines > LibVLC version now offers a single-choice dialog. Only LibVLC versions backed by this installed APK's native runtime discovery are listed; selecting one removes other LibVLC versions from the engine discovery list. Media3 remains independently available.

The chosen version is persisted as `selected_libvlc_version`. Changing it refreshes background probes and restarts active playback. The pure selection policy has unit tests for single selection, missing native runtime, and legacy flavor behavior.

**Limitation:** Existing flavors still package only one LibVLC version each. This UI does not make all three versions coexist in a single APK, nor does it fabricate unavailable engines. No build, workflow, or APK was triggered.
