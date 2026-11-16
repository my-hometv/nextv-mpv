# Isolated LibVLC playback state audit

The service reports a small Binder status enum: 0 idle/stale, 1 starting, 2 native Playing, 3 error, 4 ended. The Activity polls this only while the same channel, Surface, and generation are active. Native Playing is a decoder state notification, not independent proof that video frames are visible.

Safety checks in this batch:
- Remove duplicated native playback boolean; the state code is authoritative.
- On JNI linkage failure, stop and detach the native video output before releasing the player.
- Reset playback state on failed start so no stale success is exposed.
- Preserve the direct LibVLC player and Media3 routes.

Remaining release blockers: the remote session is currently polled rather than event-driven; background stall detection needs real playback progress metrics, and three LibVLC versions still cannot coexist in the current APK. Compile and instrumentation tests have not run.
