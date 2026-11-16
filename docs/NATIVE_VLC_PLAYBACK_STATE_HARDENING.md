# Native LibVLC playback state hardening

A dedicated pure Kotlin state machine now handles native playback events. This prevents late `Playing` callbacks from overriding error/ended states, ignores invalid and regressing progress samples, and resets all progress on a new session or teardown.

The Activity also preserves the attempted native engine ID when its Surface is destroyed, so a delayed recovery callback is not misclassified as a Media3 failure.

These are functional changes to the currently packaged native LibVLC service. **The three collision-free native implementations are not yet built or integrated in one APK.** Tests were added but not run; no workflow or APK was triggered.
