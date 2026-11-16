# Independently relocated LibVLC artifacts — build gate

The three upstream LibVLC AARs cannot be merged directly into one APK: their Java class names and native shared-object paths overlap. Merely using separate processes, renaming AAR filenames, or excluding duplicate libraries does not make them independent.

This commit defines an **input contract and verification task** for independently built, relocated native runtimes. It does **not** pretend to rewrite upstream JNI symbols.

## Artifact input contract

Place three independently produced, licensed AARs in the local untracked `app/native-runtimes/` directory:

| Engine | Expected local AAR |
| --- | --- |
| LibVLC 3.5.1 | `libvlc-351.aar` |
| LibVLC 3.7.7 | `libvlc-377.aar` |
| LibVLC 4.0.0-eap29 | `libvlc-400eap29.aar` |

Each AAR must contain `classes.jar` and `jni/<abi>/*.so`, a distinct Java namespace, and noncolliding native library filenames. The task also checks for at least one common ABI and records SHA-256 hashes of the supplied artifacts.

Run manually when native artifacts exist:

```bash
./gradlew :app:verifyRelocatedLibVlcArtifacts
```

It writes `app/build/reports/relocated-libvlc-verification.txt` and fails when artifacts are missing or paths collide. It **does not** assemble or publish APKs, and passing its structural checks alone does not prove JNI bindings or native playback work.

## Remaining work

Rebuild/relocate each upstream native LibVLC and its Java/JNI bindings as a consistent version-specific implementation; verify library dependencies and licenses; supply the three real artifacts; verify them; connect each to a dedicated service/process and a unified APK; then run compilation and native device tests. Until then, the existing per-version product flavors remain unchanged.
