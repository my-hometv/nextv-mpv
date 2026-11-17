# MyView — Final development-side source audit
**Date:** 17 November 2026  
**Repository:** `my-hometv/nextv-mpv` (`public` at the time of audit)  
**Branch:** `myview-full-app`  
**Verified application/code commit:** `2e46f6905e1958e76f1a3fb5e212759d34ebcd22`  
**GitHub Actions:** [Source-only run 44079855990](https://github.com/my-hometv/nextv-mpv/actions/runs/44079855990) — **SUCCESS**.

## Scope
Reviewed the source/workflow tree, key integration sections of the ~11,000-line `MainActivity.kt`, Native/Media3 teardown, EPG callbacks/reminders, channel recovery/selection, backup safety, licensing cache, app identity, GitHub workflow triggers, and the active automated test gates. This is a critical-path source audit and **not** a literal proof that every possible code path is defect-free.

The requested development-only pending list deliberately excludes physical Android TV tests. No APK was built, installed, published, or released.

## Corrections made and committed in this final audit
1. **EPG zero-delay loop**: when a programme reminder was due while the app was foregrounded but licence verification was incomplete, immediate rescheduling could spin the UI event loop. Added `EpgGuidePolicy.nextReminderDelayMs()`, use a bounded 60-second retry when due but undeliverable, and guard scheduling while EPG is disabled. Re-enabling EPG re-arms saved reminders. Added unit regression tests. Commit `b0223cc7d9f4436a74523803d14cce2ac914516b`.
2. **Public-repository security documentation**: `SECURITY.md` incorrectly asserted the repository was private. Updated the policy to accurately reflect current public visibility and reiterate no embedded secrets, signed-license protection, HTTPS licensing, protected backups, and owner-approved manual APK releases.
3. **Test coverage and CI**: source verification now automatically runs `python3 -m unittest discover -s tools -p 'test_*.py' -v` and `python3 -m unittest discover -s tests -p 'test_*.py' -v` before Kotlin compilation and Android JVM unit tests. Repository tool/test changes trigger this gate. Commit `2e46f6905e1958e76f1a3fb5e212759d34ebcd22`.

## Verified checks at the exact code commit
| Check | Result |
|---|---|
| MyView artwork and startup/release source contracts | PASS |
| Python native provenance/security/release-gate tests | PASS |
| Kotlin compilation and Android JVM unit tests | PASS |
| Single LibVLC dependency constraint | PASS |
| Source verification produces no APK | PASS |
| GitHub Actions run conclusion | SUCCESS |

## Reviewed, without speculative changes
- `com.softppj.myview` is an independent application ID; licensing remains a signed, device/token-bound lease.
- Media3 playback, LibVLC 3.5.1, native generation guards, detached LibVLC release worker, prebuffer ownership checks, and remote event routing remain integrated.
- The app uses `android:usesCleartextTraffic=true` for user-supplied HTTP IPTV streams. Licensing requests remain HTTPS; changing global cleartext policy could break MPEG-TS playback.
- EPG parsing uses a decompressed-input bound, with stale load-generation cancellation.
- Profiles, channel ordering, favorites, hidden channels, playlist switching and portable backup entry points remain present, with existing policy tests.
- The former push-triggered branding APK workflow is disabled. `myview-full-app.yml` is manual-only; the active MyView source workflow does **not** assemble an APK. Some legacy NexTV workflows remain separately available; they must not be manually dispatched without approval.
- Repository currently public. The GitHub Releases list was empty at the time of review; historical Actions artifact inventory was not available from this audit.

## Development-side disposition
**No remaining confirmed source-code defect or outstanding source CI failure was identified after the corrections above.**

This is a clean development checkpoint, *not* a guarantee of fault-free native decoding, HDR tone mapping, remote hardware compatibility, or performance on every television. The existing draft PR for a physical-TV acceptance evidence collector is intentionally **not** counted in the user's development-side pending list.

**Release rule:** No further APK action unless explicitly authorized by the owner.
