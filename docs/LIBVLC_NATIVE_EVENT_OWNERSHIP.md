# LibVLC event ownership ordering

The isolated native service now registers player/session ownership **before** configuring the native video Surface and media. This avoids discarding early native events when LibVLC invokes callbacks during setup. Failure paths also clear the last known native time/position so a failed start cannot contaminate subsequent progress monitoring.

The existing Gradle flavor still contains only one native LibVLC implementation. The requested three-version single APK requires independently relocated Java/JNI/native binaries; this change is a functional service integration correction, **not** those binaries. No workflow, compilation or APK build was run.
