# Native LibVLC integration verification checklist

The `vlc351`, `vlc377`, and `vlc400eap29` flavors currently produce **three separate app variants**, not one APK with three native versions.

## Release-blocking checks

- [ ] Compile all three flavor variants and run JVM tests.
- [ ] Inspect resolved AARs and APK contents to confirm every version's actual native libraries and supported ABIs.
- [ ] Build separately namespaced Java/JNI/native implementations and prove they can coexist in **one** application package.
- [ ] Implement a real Surface-attached playback service for each native runtime; do not report `isRuntimeReady` until native initialization succeeds.
- [ ] Verify Media3, LibVLC 3.5.1, 3.7.7, and 4.0.0-eap29 individually on Android TV.
- [ ] Verify engine-first alternative URL checks, audio/video synchronization, remote navigation, diagnostics export and recovery after service death.
- [ ] Run the consolidated workflow and full APK audit.

A valid Binder connection or recognized engine ID does not satisfy any native playback acceptance criterion.
