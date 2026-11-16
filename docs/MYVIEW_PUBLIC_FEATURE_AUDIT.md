# MyView public branch — source feature audit (16 Nov 2026)

Scope: `my-hometv/nextv-mpv`, branch `myview-public-build` only.
No private NexTV source, configuration, assets, playlists or credentials were examined or copied for this audit.

## Integrated in the public app

- Standalone Android ID `com.softppj.myview` and Android TV launcher.
- Media3 player for user-entered HTTPS HLS/DASH/MP4; provider HTTPS webpage test.
- Local-only user-supplied M3U parser, bounded at 2,000 HTTPS entries.
- DPAD-focusable channel selection, number-key preview and channel switching.
- Up to five local user playlists, per-playlist resume, per-playlist favorite sets.
- Translucent side channel drawer over active video, XMLTV HTTPS URL import
  with bounded data and now/next labels (user-provided sources only).
- Bounded *same-URL* retry; distinguishes transient network/provider-busy
  errors from authorization, DRM, decoder and source errors.
- Conservative sustained fast-forward playback-clock guard, toggle ON by
  default. Not a true audio/video lip-sync detector.
- Optional direct-file VOD refresh-rate matching, OFF by default; chooses
  display modes at the current resolution and restores the previous preference
  on exit. Does not change live TV refresh rate.
- Optional on-screen Media3 playback diagnostics: resolution, format, FPS,
  playback state, speed, buffer events, retry count and error category.
- JVM regression coverage for M3U parsing, retry classification, clock guard
  and refresh mode selection.
- Push-triggered compile-and-test-only workflow, manual-only APK workflow.

## Not yet implemented / not verified

1. Live drawer, numeric preview, playlist memory, group filtering, search,
   XMLTV now/next, named favorites, hidden channels, and limited lists are coded.
   Android TV focus behavior, real-world guide matches and usability still
   require device testing.
2. Basic bounded channel-logo loading is coded; full EPG time-grid,
   provider metadata, advanced artwork/caching and visual polish remain incomplete.
3. Full LEFT → LEFT → LEFT navigation and some D-pad focus edge cases remain
   incomplete; named favorite lists and group filtering are now coded.
4. Hidden-channel review and user-triggered settings backup/restore are coded,
   but backup migration and complete feature-by-feature restore tests remain.
5. Automatic alternative URL/engine discovery and validated health scoring.
   Deliberately not added until safe source-pinning and tests exist.
6. Audio/video *lip-sync measurement* and direct timestamp correction.
   Clock-progression recovery is not a substitute.
7. Per-URL observed startup percentages (never predicted probabilities),
   frame counter quality, accurate buffering durations, decoder names and
   exportable playback debug reports are incomplete or need device validation.
8. Native LibVLC/GStreamer engines, their format support and fallback tests.
9. HDR/SDR color-path verification, SuryaTV image clarity verification and
   actual Android TV playback performance (reserved for device testing).
10. Profiles, comprehensive playlist ordering, backup/restore,
    and other full-app features remain incomplete.
11. Private licensing / activation systems and private provider assets:
    must not be published in this public repository.
12. Every new integration commit must pass compilation and unit tests before
    claims of build readiness or any manual APK creation.

## Release gates

- Public verification workflow must show SUCCESS at the latest source SHA.
- Verify that private URLs/keys are not bundled and that the APK contains the
  separate application ID.
- APK packaging remains manual, never automatic on push.
- Device playback and picture-quality acceptance remain with the user.

## Important limits

- Public MyView is a separately authored experimental Media3 application. It is
  not a binary or feature-equivalent copy of the private NexTV product.
- Login, credential, and licensing material are never imported from private repositories.
- A real lip-sync analyzer, multi-engine decoder support, and full EPG grid are
  not implemented. Device testing is essential for the user's picture quality needs.
- Settings exports can contain sensitive playlist URLs and must be kept private.

## Post-audit reliability batch

- Last-watched channel is now committed only on Media3 READY for the selected channel.
- Prolonged 30-second buffering triggers bounded same-source recovery when playback is requested.
- Lifecycle pauses and resumes active playback rather than leaving it running in background.
- User-initiated diagnostics export excludes URLs, playlist text and provider tokens.
- XMLTV importer rejects DTD/ENTITY declarations and timestamps must parse completely.
- Final build and device verification still required for the newest code.

## Final-source hardening stage (public only)

- Removed repeated clock-timer scheduling from the periodic health tick.
- Paged the Live TV drawer to keep every channel accessible without 2,000 buttons.
- XMLTV URL imports accept bounded, optionally gzip-compressed XMLTV.
- Manual-only APK job now uses the known-working Android SDK setup and runs tests first.
- These are source-level changes; TV display, HDR color and format behavior still need device validation.

## Visibility and restore consistency audit

- Playback selection now refuses hidden channels, including direct list/guide actions.
- 'Watch last visible channel' resumes a visible entry, not the old hidden index.
- Switching playlists resets search, group, list pagination and guide page, preventing stale cross-playlist selection.
- Backup restoration clears stale settings only after the entire import passes validation; it then atomically writes the validated replacement.
- Full feature parity and real HDR/SDR and A/V sync correction still require separate engineering and on-device verification.
