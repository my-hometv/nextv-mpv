# Native Surface loss recovery

A SurfaceHolder may be destroyed while an isolated LibVLC session is active. The Activity now stops the old generation, cancels its watchdog and requests a fresh playback recovery once, subject to Activity/channel generation checks. It refuses to reattach the destroyed Surface with the old generation.

Also corrected the recovery banner interpolation: it previously displayed a literal `$failure` instead of the actual error reason.

A pure Kotlin surface-recovery gate and unit tests document the one-shot recovery contract. Activity callback behavior still requires Android instrumentation/TV validation. No workflow, APK or release generated.

The three LibVLC native implementations in a single APK remain unimplemented.
