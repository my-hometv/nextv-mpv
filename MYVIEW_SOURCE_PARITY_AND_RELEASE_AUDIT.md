# MyView source parity and release verification — 17 November 2026

## Scope and reference
- Working branch: `my-hometv/nextv-mpv:myview-full-app`
- Original app source reference inspected: `my-hometv/nextv:development`
- Production licensing components and stable NexTV files are strictly out of scope for changes.
- This is a **source-level comparison**: the original stable APK binary was not available for byte-for-byte comparison. Do not declare APK parity or a 100% release before physical TV checks.

## Source-level parity findings
- The reference Android TV MainActivity source contains 401 distinct named functions; the full-app MyView branch previously contained 414. One reference-only symbol was in excluded licensing code. This is **not proof of behavioral parity**.
- Both source trees contain the feature entry points for favorites, hidden channels, playlist operations, EPG, profiles, backup/restore, picture/audio controls, remote navigation and startup/recovery.
- MyView's additional source features include 2/4-way MultiView, prebuffer handoff, adjacency preloading, a public logo directory, decoder/stream-health diagnostics and VOD-only refresh matching.
- Current MyView uses `com.softppj.myview` and Media3 + LibVLC. The original stable installation must not be overwritten.

## GitHub reference-app feature check
| Discussed reference | Desired characteristic | Source finding | Release evidence required |
| --- | --- | --- | --- |
| Stalker-style UI | Translucent channel drawer, channel logos, Now/Next EPG | Channel drawer/focus/Now/Next implemented | TV focus, responsiveness and drawer layering |
| Quiblo-style provider recovery | Separate provider busy from permanent errors | `ProviderFailurePolicy` + bounded retry tests | Real short network interruption |
| Quiblo-style frame-rate handling | Optional VOD refresh-rate match without altering Live TV | VOD-only setting, OFF by default | Verify no Live TV mode switching |
| Diagnostics | Resolution, codec, HDR transfer, bitrate, frame health | Extended Media3 diagnostics | Confirm on actual stream |

No source from third-party projects was copied verbatim; these are independent MyView implementations.

## Changes in this pass
1. Added an **opt-in** public IPTV-org `logos.json` directory for supported PNG/JPEG/WebP artwork only.
   - Original explicit channel logos and unambiguous local playlist matches remain higher priority.
   - Only exact EPG-ID or unambiguous canonical-name results are used; ambiguous matches are discarded.
   - HTTPS catalog fetch only, 20 MiB maximum, bounded streaming parser, 24-hour successful cache.
   - Failed attempts have a 15-minute cooldown. Turning the option OFF disconnects active directory requests.
   - No private playlists, channel stream URLs or credentials are uploaded to the directory.
2. Added safer bounded remote artwork decoding and image-memory limits.
3. Expanded tests for capacity refusals and permanent source/authorization failures.
4. Restricted prebuffer handoff to a standard picture preset; custom effects use normal full renderer startup.
5. Added real Media3 audio/video format, selected bitrate, frame drop/render counts and HDR transfer reporting.
6. Added sequential adjacent preparation with at most **one speculative decoder** and tests.

## Not yet proven / release blockers
- Fresh GitHub Actions `MyView Source Verification (NO APK)` pass on the **latest commit**.
- Original stable APK binary comparison: original stable APK was not supplied in file-library results.
- SuryaTV HDR10/SDR picture quality and tone mapping on the user's physical TV.
- Actual MPEG-TS, 1080i/deinterlacing, audio/video synchronization and prolonged playback.
- Network interruptions, confirmed URL recovery, rapid remote channel switching, multi-view decoder saturation.
- TV remote and dialog focus parity, favorites/hidden channels/EPG/backup/profile behavior compared with stable.
- APK release artifact integrity and independent package verification after successful source CI.

## TV test sequence (do not infer PASS from source alone)
1. Run source-only CI, record exact commit SHA and tests.
2. Explicitly dispatch `.github/workflows/myview-full-app.yml` on `myview-full-app` for testing APK **only after source CI**; never publish production APK automatically.
3. Compare original NexTV stable and MyView as separate installations. Record any regression.
4. On SuryaTV HDR10, open Playback diagnostics; record format, color transfer/space/range, bitrate and decoder counters. Compare native Standard preset and default aspect 16:9.
5. Repeat on a plain SDR 1080p and MPEG-TS channel, observe stalls/lip-sync.
6. Verify UP/DOWN preview (5 seconds), clock (time 10/day-date 5), LEFT → LEFT → LEFT, numeric keys, OK and BACK, prepared-channel promotion, alternative recovery and MultiView.
7. Block final approval for video corruption, black screen, crashes, source crossover, stuck remote or decoder leaks.

## Explicit non-scope
No changes to licensing code, license server/Worker, keys, activation data, or existing installed applications.
