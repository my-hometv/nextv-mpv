# NexTV MPV native component provenance and license review

**Status: NOT DISTRIBUTION-CLEARED.** This is an engineering evidence inventory, not a license grant, a complete source bundle, or legal advice.

## Exact upstream release reference

- Gradle dependency: `dev.jdtech.mpv:libmpv:1.0.0` (AAR).
- Upstream source tag: https://github.com/jarnedemeulemeester/libmpv-android/tree/v1.0.0 ; release: https://github.com/jarnedemeulemeester/libmpv-android/releases/tag/v1.0.0 (release commit abbreviated `fcf6745`).
- Maven metadata: https://central.sonatype.com/artifact/dev.jdtech.mpv/libmpv/1.0.0 (MIT wrapper metadata; **not** native binary license).
- Upstream build recipe: https://github.com/jarnedemeulemeester/libmpv-android/tree/v1.0.0/buildscripts .
- Native binaries: upstream identifies effective GPL-3.0-or-later; review the exact binary build flags and static links before distribution.

## Upstream v1.0.0 component versions (from release notes)

| Component | Version | License family / verification status |
|---|---|---|
| mpv | 0.41.0 | GPL-2.0-or-later |
| FFmpeg | 8.1 | GPL-3.0-or-later per upstream binary distribution; confirm exact build |
| libplacebo | 7.360.1 | LGPL-2.1-or-later |
| libass | 0.17.4 | ISC |
| lua | 5.2.4 | MIT |
| libunibreak | 6.1 | zlib |
| harfbuzz | 14.1.0 | MIT-style |
| fribidi | 1.0.16 | LGPL-2.1-or-later |
| freetype | 2.14.3 | FTL or GPL |
| libxml2 | 2.15.2 | MIT |
| fontconfig | 2.17.1 | permissive |
| mbedtls | 3.6.6 | Apache-2.0 |
| dav1d | 1.5.3 | BSD-2-Clause |

The license-family column is provisional pending verification against the v1.0.0 tagged build scripts and each component's license files. Do not substitute the newer main-branch component versions for this release.

## What has been verified

- CI run 43681545625 uploaded native audit artifact 14148373759 with ELF inventory and Gradle dependency tree.
- `libmpv.so` exists for arm64-v8a, armeabi-v7a, x86 and x86_64 in merged debug libraries.
- ELF dynamic linkage identifies FFmpeg `libavcodec`, `libavformat`, `libavfilter`, `libavdevice`, `libavutil`, `libswscale`, `libswresample`; statically linked dependencies cannot be established from `DT_NEEDED` alone.

## Mandatory evidence before an APK can be shared

1. Download and checksum the exact Maven AAR and POM and map their contents to the upstream tagged source and build recipe; retain per-ABI library hashes and toolchain/build flags.
2. Obtain and verify complete corresponding source for the precise native binaries, all included patches, scripts, configuration and dependent code; verify whether upstream artifacts are reproducible or document differences.
3. Prepare complete license texts, copyright notices, GPL-compliant source-access mechanism, and any required installation/relinking information; address the combined Android application's GPL obligations with an appropriate distribution strategy.
4. Confirm rights and licensing for NexTV application source and all other bundled third-party dependencies. Get legal review where ownership or GPL compatibility is uncertain.
5. Record reviewer, date, evidence hashes, source location and an explicit release approval. User approval for APK generation is a **separate** requirement.

No license-cleared marker is present; the manual APK workflows deliberately fail closed until compliance is independently established and approved.
