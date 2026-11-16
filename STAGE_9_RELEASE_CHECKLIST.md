# Stage 9 — Release readiness and Android TV acceptance checklist

All Stage 1–9 work is consolidated on `development`. The Stage 9 baseline commit `b5505fd` passed the consolidated compile and JVM test workflow on 10 November 2026. Subsequent audit fixes must receive their own validation run. No APK or pre-release is authorized by this checklist.

## Automated validation (required)

- [x] Run `.github/workflows/manual-unit-tests.yml` on `development` at `b5505fd` (workflow run 42822470615).
- [x] Confirm `:app:compileDebugKotlin` passes on `b5505fd`.
- [x] Confirm all 65 JVM unit tests pass on `b5505fd` (0 failures, 0 errors, 0 skipped).
- [x] Verify no unintended change to `main`; PR #1 remains unmerged at audit start.

## Physical Android TV acceptance (required before release)

- [ ] Verify remote DPAD/OK/BACK, channel up/down, number entry and dialog focus on Android 9 and Android 10+ where available.
- [ ] Enable detailed remote diagnostics briefly, reproduce remote mappings, then turn diagnostics OFF.
- [ ] Test cold launch, safe startup recovery, HOME/background and return; confirm no background audio and no playback before licensing.
- [ ] Test licensed and unlicensed startup, expired/rejected license, network outage, revalidation, and surrender. No playback may bypass the license gate.
- [ ] Verify locked playlists, locked groups, locked channels and parental PIN remain enforced on direct play, resume, profile switching and startup.
- [ ] Check Media3, LibVLC, MPV fallback, Web TV, YouTube and live/VOD playback; verify stop/release and no frozen playback on navigation.
- [ ] Verify M3U fallback URLs, HTTP headers and injected-header rejection with representative playlists.
- [ ] Test viewer creation/switch/delete, favorites, recent history and per-viewer channel sorting.
- [ ] Export and restore settings on a test device. Verify license keys/tokens, PIN and other sensitive values are not transferred or overwritten.
- [ ] Check 10k+ channel navigation responsiveness, cache eviction, diagnostics privacy and prolonged playback stability.
- [ ] Verify Android TV launcher visibility, app icon, remote-only navigation and screen scaling.

## Release controls

- Do not trigger `manual-apk.yml` without explicit approval.
- Do not merge `development` or `stage-9-preparation` into `main` without explicit approval.
- Do not claim Android TV hardware acceptance based on Robolectric/JVM tests alone.
- All changes are already on `development`. Run the consolidated workflow again after audit fixes and verify the exact tested SHA before any approved APK build.
- Device testing and third-party codec/library distribution-license review are mandatory before approving a pre-release.
- Do not merge `development` into `main` without explicit owner approval.
