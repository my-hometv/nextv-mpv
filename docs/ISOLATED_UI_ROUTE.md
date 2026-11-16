# Isolated native playback UI route

The Activity now recognizes an explicitly selected `LibVLC-<version>` engine and creates a `SurfaceView` for the isolated service. It starts the service only after `surfaceCreated` and stops the matching generation during `surfaceDestroyed`. Channel changes and teardown also call `isolatedVlcSessions.stopActive()`.

This route is separate from the existing `LibVLC` direct-player path, which is preserved. The service exposes only the native version included in the selected Gradle flavor. It is **not** yet a single APK with all three versions.

Limitations requiring verification: isolated service does not yet return asynchronous Playing/Error callbacks to the Activity, so the UI cannot confirm actual decoded playback, update working-stream records, or run the same watchdog logic as the legacy direct player. Native decoder APIs and Gradle compilation have not been tested. Release requires a Binder playback event channel, full recovery integration, and multi-version JNI relocation.
