# LibVLC multi-version isolation: engineering gate

## Verified baseline

The `vlc351`, `vlc377`, and `vlc400eap29` Gradle flavors compile independently. Each APK flavor contains **one** LibVLC implementation alongside Media3. Passing Kotlin compilation does not establish playback or native ABI compatibility.

## Why combining the dependencies is unsafe

The three `libvlc-all` AARs expose the same `org.videolan.libvlc` Java classes and native library filenames. Android's APK packaging and classloader do not provide per-dependency Java or JNI namespaces. Declaring all three as `implementation` dependencies, renaming only the AAR, or running them in different processes within the same APK does not resolve duplicate classes and native symbols. `pickFirst` silently selects one native binary; it does not preserve three versions.

## Required architecture before enabling additional menu entries

1. **Inspect the actual AAR contents and licenses** for each release, including `classes.jar`, JNI `.so` libraries, transitive native dependencies, and supported ABIs. Record collisions and ABI differences. Do not guess JNI compatibility from version strings.
2. **Establish a reproducible source-based relocation build** for each extra version. Java/Kotlin API packages, JNI registration/signatures, native sonames, `DT_NEEDED` dependencies, and native loader names must be independently namespaced. Relocating Java bytecode alone is insufficient.
3. **Validate Android process loading** on the supported Android TV ABIs and OS versions. Prefer separate service processes per relocated version to reduce global native state conflicts; process isolation alone is not a substitute for relocation.
4. **Implement an IPC playback contract** (start/stop, URL, surface ownership, audio/subtitle tracks, state, errors, cleanup) and version-specific service adapters. Keep Media3 and the original LibVLC 3.5.1 path as rollback options.
5. **Extend engine inventory only after runtime verification.** Store stable version-specific engine IDs; migrate old `LibVLC` preferences to the baseline; update per-channel pinning, URL-first/engine-first attempts, background probes, confirmation, and recovery consistently.
6. **Gate the release** on native dependency collision checks, flavor compile/unit tests, process death/restart, stale callback handling, TV remote focus, and on-device playback tests. Do not publish a combined APK before these checks pass.

## Current decision

The project has not yet established relocatable builds of LibVLC 3.7.7 and 4.0.0-eap29. Until that prerequisite is met, the safe implementation is three independently compiled flavors, each advertising only its packaged LibVLC version. No UI placeholder should pretend that all three versions are loaded in one APK.

## Release constraints

Review LibVLC and codec licenses, source availability, patent/codec distribution obligations, and any modifications needed for reproducible builds before distribution.
\n## Relocated artifact verification gate\n\n`tools/verify_libvlc_isolation.py` is a fail-closed validator for **candidate source-built, relocated** AARs. Run `python3 tools/verify_libvlc_isolation.py 3.5.1=/path/a.aar 3.7.7=/path/b.aar 4.0.0-eap29=/path/c.aar`. It rejects missing artifacts, legacy Java class packages, missing per-version Java namespaces, unrenamed LibVLC binaries, original ELF SONAME/DT_NEEDED links, and duplicate packaged Java/native paths. This does **not** build or relocate LibVLC and does not prove JNI registration, native loader behavior, codec compatibility, or on-device playback. Passing it is necessary but not sufficient for enabling combined playback.\n
## Source-build orchestrator (requires audited upstream inputs)

Run `python3 tools/build_isolated_libvlc.py manifest.json --provenance build/libvlc-source-provenance.json --verify-relocation`. The manifest must contain `versions` with entries for exactly `3.5.1`, `3.7.7`, and `4.0.0-eap29`. Each entry requires `source_dir`, a full 40-character `git_commit`, an argument-array `build_command`, and an absolute or resolvable `output_aar`. Source trees must be clean and match pinned commits. Existing output files are rejected to avoid accepting stale binaries. The script executes the explicitly supplied source-build commands, records SHA-256 hashes, and optionally invokes the strict relocation verifier. It intentionally does not invent upstream revision hashes or claim that stock LibVLC sources already support three independently relocated JNI runtimes. A verified relocation patchset, build flags and native loading tests remain necessary. Do not enable version IDs or package a combined APK merely because this orchestration completes.
