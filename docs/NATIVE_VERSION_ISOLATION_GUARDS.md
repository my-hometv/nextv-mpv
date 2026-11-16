# Native engine version isolation and recovery safety

This change hardens the existing version-scoped service boundary while the full three-version implementation is developed.

* The service checks that the requested version matches its **actual packaged** LibVLC flavor before loading native code.
* A failed native-engine lookup or Binder startup retains the requested engine identity until the recovery decision, preventing accidental Media3 attribution.
* The Surface callback refuses an invalid video Surface before entering the remote session controller.
* Stable version engine IDs are distinct from the legacy `LibVLC` selection; only runtime-verified IDs are offered.

**Architecture blocker:** Separate Android processes do not resolve duplicate Java classes or JNI libraries packaged in the same APK. A true four-engine single APK still requires independently namespaced Java/JNI implementations and collision-free native libraries. The upstream LibVLC AARs have not yet been repackaged, compiled, or device-tested. The collision audit can be run later without assembling an APK.
