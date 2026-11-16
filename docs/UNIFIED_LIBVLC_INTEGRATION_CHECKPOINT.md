# Unified LibVLC APK — single-commit integration checkpoint

## Current result: blocked by missing native implementations

The current app has three **product flavors**, each containing one upstream LibVLC version. It does **not** have a unified APK containing three independently functioning native engines.

Combining the upstream AARs directly is unsafe because they collide at Java class, JNI symbol and ELF shared-library levels. Native process separation alone does not fix same-package JNI bindings.

This checkpoint adds a fail-closed Gradle preflight and a machine-readable source-input audit. It is not a finished integration and does not create a new APK variant.

### Inputs required to finish the unified implementation

- Three independently built, licensed and JNI/ELF-relocated AARs under `app/native-runtimes/`, as documented in `RELOCATED_LIBVLC_ARTIFACT_CONTRACT.md`.
- Three actual native adapters under `app/src/unified/java/com/softppj/nextv/nativevlc/`, one for each runtime. A stub is not a functioning adapter.
- A unified variant and per-runtime service/process bindings once native artifacts and adapters have been implemented.
- A successful structural verification, Android compilation, native runtime tests and TV playback tests.

### Read-only checks

```sh
python3 tools/audit_unified_libvlc.py --app app --output app/build/reports/unified-libvlc-inputs.json
./gradlew :app:preflightUnifiedLibVlc
```

The Gradle preflight depends on the existing relocated-AAR and ELF/JNI audit. It **fails** until the required native artifacts and adapter sources exist. Passing the file-presence script alone is never evidence of working playback.

This checkpoint intentionally preserves the existing flavor builds and never silently replaces a native backend with Media3 or claims unsupported LibVLC versions are available. No workflow or APK was triggered.
