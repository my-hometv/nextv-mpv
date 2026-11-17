# MyView — consolidated full-repository audit, 17 November 2026

**Scope**: `my-hometv/nextv-mpv`, feature branch `myview-cinematic-home`. Current application source is based on commit `13058950dbca60202e357523557340d4fa992024` (Home focus correction). This is a repository-wide inventory and targeted critical-path source review, not a formal proof of every line in the ~11,000-line `MainActivity.kt`, an instrumented Android run, or a physical-TV certification.

## Verified GitHub evidence
- The previous branding commit `2c9fb34a2797f5a571f057a2754dee245821e7cc` passed `MyView Source Verification (NO APK)` (run 44071250038) and `MyView Approved Branding Integrity (NO APK)` (run 44071250196).
- Asset integrity check demands the original owner's exact `tv_banner.png` and `nextv_launcher.png` bytes and image dimensions; both passed at the baseline SHA. Subsequent Kotlin-only Home focus correction does not change assets.
- The correction commit has its **own** source verification run 44072283795. Confirm its result directly on GitHub. Never infer PASS from the older successful run.
- APK generation and publication intentionally remain on hold.

## Audited paths and findings
| Priority | Area | Direct inspection / result | Release interpretation |
| --- | --- | --- | --- |
| P1 CORRECTED | Initial Home focus | `showHome()` attempted to focus `homeContent.getChildAt(2)` as a LinearLayout. Child #2 is the WATCH NOW section TextView. It therefore silently returned null. Focus now explicitly targets focusable `continueBanner` when attached. | Source correction committed. Physical focus journey and new CI run needed. |
| VERIFIED | Artwork parity | Manifest uses `nextv_launcher` for the app icon and `tv_banner` for TV banner; startup, Home hero and channel info reference `tv_banner`. Asset hashes/dimensions passed the dedicated GitHub check. | Pixel appearance still requires a TV. |
| VERIFIED | Independent app installation | Gradle `applicationId = com.softppj.myview`, app label `MyView`. | Different identity from the existing NexTV; no package overwrite. |
| VERIFIED | Playback source policies | Media3 + a single LibVLC 3.5.1 dependency, native cleanup worker, isolated session generation, watchdog, prebuffer and recovery policy implementations/tests present. | JVM compile/tests do not establish absence of device stalls, black screens or native codec crashes. |
| VERIFIED | Remote routing | `dispatchKeyEvent` handles DPAD, OK/BACK, numeric and CH+/CH- paging within channel list; long-press action leads to channel move/favorite/hide/lock. | Physical remote variants and focus behavior unverified. |
| VERIFIED | Saved channel order | `ChannelOrderPolicy` move/apply used from position dialog; regression coverage exists. | Check persistence after refresh and application restart on TV. |
| VERIFIED | EPG and media | Bounded decompressed XMLTV input, cached programmes, logo resolution, Now/Next and media library entry points present. | EPG load/offset and actual TV colors/placement unverified. |
| VERIFIED | MultiView | Access policy blocks unlicensed, locked and hidden channels and decoder startup staggers; unit tests present. | 2/4-decoder hardware capacity and memory saturation unverified. |
| VERIFIED | Licence lease | `SignedLicenseCache` verifies RSA-SHA256 signature, device/token binding, expiry, clock rollback and offline ceiling. `beginLicensedSession` uses signed cached lease. No signing private key is in inspected cache code. | Does not prove resistance to APK tampering, replay outside tested assumptions, or licensing-server compromise. No licensing changes made. |
| VERIFIED | Backup safety | `BackupSafety` excludes license/token/PIN-like settings, 2 MB input bound; UI warns that exported playlists may embed provider credentials. | User must keep manually exported backup private. |
| REVIEW | Android network policy | `android:usesCleartextTraffic=true` globally, likely necessary for HTTP IPTV provider streams. | Restrict where feasible without breaking existing .ts HTTP channels; not a safe blind removal. |
| REVIEW | Third-party licensing | LibVLC dependency is present in a public repository; package/release compliance must be verified separately. | Do not infer a legal distribution grant from successful compilation. |
| REVIEW | Workflow release safety | `myview-source-verification.yml` never builds APK; `myview-full-app.yml` requires manual dispatch on `myview-full-app`. A legacy `myview-branding-build.yml` accepts a special commit-marker push on that other branch and can assemble/upload an APK. | Before release, simplify APK workflows and remove obsolete marker-trigger paths. No APK triggered by the audited feature branch. |
| P0 HARDWARE | HDR10/SDR and MPEG-TS | Code config exists, but hardware tone mapping, 1080i deinterlacing and decoder-specific fidelity not reproducible by JVM tests. | Real SuryaTV HDR, SDR 1080p and .ts on target TV required. |
| P0 HARDWARE | Fast remote/channel changes | Recovery and generation guards tested headlessly. | 50+ switches, short network drops and no native leaks/ANRs on device required. |
| P1 HARDWARE | MultiView/EPG/navigation/branding | UI and policies exist. | Complete 30-scenario physical-TV acceptance matrix before release. |

## Tests and interpretation
Previous same-SHA baseline CI ran `:app:verifySingleLibVlc :app:compileDebugKotlin :app:testDebugUnitTest` successfully. The artwork integrity workflow separately passed. The Home focus change needs its own CI result. No hardware test occurred in this audit, and no new APK was built.

## Release decision
**HOLD production APK.** Keep `myview-cinematic-home` as a separate review branch until the focus-fix source run passes, relevant feature PR is reviewed, and a separately authorized TV test build is exercised. The original stable NexTV repository, licensed server and private source data were not altered.

## Source references
- `app/src/main/java/com/softppj/nextv/MainActivity.kt`
- `app/src/main/java/com/softppj/nextv/{SignedLicenseCache,BackupSafety,LivePrebufferPolicy,ProviderFailurePolicy,BackgroundProbeCoordinator,NativeVlcCleanupQueue,IsolatedVlcServiceClient,ChannelOrderPolicy,MultiViewAccessPolicy,ClockOverlayPolicy,M3uParser,BoundedEpgInput,HomeTvDiagnostics}.kt`
- `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- `.github/workflows/{myview-source-verification,myview-branding-integrity,myview-full-app,myview-branding-build,manual-apk,stable-prerelease}.yml`
- `MYVIEW_ANDROID_TV_RELEASE_GATE.md`
