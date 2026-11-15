# NexTV MPV — release-readiness checklist

## Current decision: HOLD for distribution
This document is an engineering gate, not a legal certification or a claim of verified HDR output.

### Verified engineering baseline
- MPV-only app ID: `com.softppj.nextv.mpv` (independent of the stable NexTV package).
- Native MPV binary observed in merged debug libraries for arm64-v8a, armeabi-v7a, x86, and x86_64 in GitHub Actions run 43681545625.
- Latest source and integration tests for commit ed1088af passed (runs 43681812284 and 43681812286).
- Foreground playback uses MPV; first-run decoder Automatic and output Auto; per-channel manual choices remain.
- `MainActivity.kt` has no remaining literal Media3/LibVLC references as of the source audit.
- Existing manifest includes INTERNET, ACCESS_NETWORK_STATE and RECEIVE_BOOT_COMPLETED; backup disabled; cleartext traffic enabled to support legacy HTTP IPTV endpoints. Review HTTP transport exposure before public release.

### Native redistribution compliance — NOT CLEARED
1. Retrieve the exact `dev.jdtech.mpv:libmpv:1.0.0` AAR, its POM and license notices, and retain immutable checksums.
2. Identify mpv, FFmpeg, codec libraries, build flags, and all statically or dynamically linked components per ABI. ELF NEEDED entries alone cannot reveal statically linked components.
3. Confirm each component's license and any GPL/LGPL compatibility, attribution, license-text, modification and corresponding-source obligations.
4. Prepare required notices, license texts and complete corresponding source/build instructions or source offer, as applicable, for the distributed binary.
5. Record reviewer, evidence, date and approval. Do not check `licensing_cleared` in manual workflows before these steps are complete.

### Manual APK and prerelease workflows — prepared, NOT RUN
- Both workflows use `workflow_dispatch` only, require `licensing_cleared=true` and `apk_approved=true`, run source and unit verification before assembling, and inspect MPV/arm64 native inclusion.
- These inputs are attestations by the person dispatching the run; they are not independently verified legal evidence. Keep permissions limited and restrict release operators.
- Artifact workflow uploads a debug APK; prerelease workflow publishes an experimental GitHub prerelease. Neither should run without user approval.
- Confirm GitHub workflow syntax and run a non-APK source check after editing workflows.

### TV testing — user-owned
- Test Surya TV HDR10 stream and ordinary SDR channels for colour accuracy, HDR signalling, frame pacing, startup latency, audio, short network interruptions, alternative URL behaviour, and extended playback.
- The current RGBA_8888 SurfaceView does not establish genuine 10-bit HDR presentation. Force HDR10 is a request, not proof of HDR output.

### Release decision
Do not mark this project distribution-ready until native compliance is documented, final CI is green, user authorizes APK creation, and physical TV testing passes.

## Native artifact inspection — 2026-11-15
- Inspected downloaded CI artifact `mpv-native-audit` (artifact ID 14148373759) containing `abi-inventory.txt` and `runtime-dependencies.txt`.
- Runtime dependency tree includes `dev.jdtech.mpv:libmpv:1.0.0`.
- The ELF `DT_NEEDED` entries for `libmpv.so` include `libavcodec.so`, `libavfilter.so`, `libavformat.so`, `libavutil.so`, `libavdevice.so`, `libswresample.so`, `libswscale.so`, `libc++_shared.so`, plus Android/system libraries. These entries demonstrate dynamic dependencies but do not prove which codec features or static libraries were built in.
- Audit remains **NOT CLEARED**: collect the upstream AAR's POM and license, all bundled FFmpeg/native build configuration and corresponding-source materials before any distribution. No blanket license assertion is made for the FFmpeg binaries without inspecting their actual build configuration.

## Upstream license provenance — 2026-11-15
- Maven Central POM for `dev.jdtech.mpv:libmpv:1.0.0` labels the **wrapper project** MIT; this does not license the bundled native binaries as MIT. See https://central.sonatype.com/artifact/dev.jdtech.mpv/libmpv.
- Upstream source repository https://github.com/jarnedemeulemeester/libmpv-android explicitly states its bundled native binaries are **GPL-3.0-or-later**, because mpv links against GPL FFmpeg and other components. Its README documents mpv 0.42.0, FFmpeg 9.0.2, the component license table, and native build scripts. Versions may move on the upstream main branch; confirm the published 1.0.0 artifact against the corresponding release revision before treating them as exact binary provenance.
- GPL obligations must be reviewed for the combined Android application, not just a standalone library notice. Before distributing an APK, prepare a GPL-compatible distribution strategy and complete corresponding source/build scripts for the exact distributed binaries, plus applicable license texts and attribution. Seek qualified legal review if the application's own source must remain proprietary.
- **HOLD remains in force**: upstream README/buildscripts existence is useful provenance but is not itself proof that a complete corresponding-source bundle for the exact 1.0.0 AAR and NexTV APK has been prepared or offered.

## Consolidated final source-side licensing audit — 2026-11-15
- Pin upstream release **v1.0.0** (not mutable upstream main): mpv **0.41.0** and FFmpeg **8.1** per release notes. Earlier main-branch versions in prior audit prose are **not** the dependency's exact release versions.
- See `MPV_NATIVE_THIRD_PARTY_NOTICES.md` for component inventory, provenance and precise remaining compliance evidence.
- Distribution and APK creation remain blocked pending complete corresponding-source package, license texts, combined-app GPL compliance decision, and explicit user approval. This is a documented hold, not a claim of legal clearance.
