# Single-APK LibVLC coexistence — collision gate

**Goal:** Media3, LibVLC 3.5.1, LibVLC 3.7.7 and LibVLC 4.0.0-eap29 selectable inside a single Android TV APK, with real independent native playback.

The existing Gradle flavors each package exactly one LibVLC release. A service process does **not** eliminate duplicate Java class paths or JNI library filenames in the same APK. Combining the upstream `libvlc-all` dependencies directly risks duplicate class failures and ambiguous native loading.

## New inspection command

```bash
./gradlew :app:auditLibVlcCoexistence
```

This resolves the three flavor-specific upstream AARs, inspects their `classes.jar` and `jni/**/*.so` entries, prints pairwise collisions, and writes `app/build/reports/libvlc-coexistence.txt`. It deliberately **fails** if collisions exist. It does not assemble an APK or publish an artifact.

## Required engineering sequence

1. Record the actual Java and JNI collisions from all three upstream artifacts.
2. Introduce separate namespaced native modules or otherwise supported, independently packaged runtime implementations. Renaming only the AAR or Gradle artifact is insufficient: JNI bindings, Java class names and dependent native library loads must remain consistent.
3. Route each stable engine ID to its own verified native implementation; fail closed for missing versions.
4. Replace the three single-version flavors with one unified application only after collision-free native packaging and per-engine playback are demonstrated.
5. Run compilation, JVM tests, instrumentation/native playback tests and a complete source audit before creating the APK.

**Current status:** Collision-audit task committed, not executed. No claim of three-version coexistence or successful compilation is made.
