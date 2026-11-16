# MyView public branch — source feature audit (16 Nov 2026)

Scope: `my-hometv/nextv-mpv`, branch `myview-public-build` only.
No private NexTV source, configuration, assets, playlists or credentials were examined or copied for this audit.

## Integrated in the public app

- Standalone Android ID `com.softppj.myview` and Android TV launcher.
- Media3 player for user-entered HTTPS HLS/DASH/MP4; provider HTTPS webpage test.
- Local-only user-supplied M3U parser, bounded at 2,000 HTTPS entries.
- DPAD-focusable channel selection and CHANNEL_UP / CHANNEL_DOWN switching.
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

1. Full transparent Live TV side drawer with logos and now/next EPG.
2. Production EPG ingestion, guide, provider metadata, artwork and logos.
3. Full LEFT-to-navigation, numeric channel preview and universal focus policy.
4. Multiple playlists, per-playlist last-channel memory and favorites lists.
5. Automatic alternative URL/engine discovery and validated health scoring.
   Deliberately not added until safe source-pinning and tests exist.
6. Audio/video *lip-sync measurement* and direct timestamp correction.
   Clock-progression recovery is not a substitute.
7. Stream-health video frame counters, accurate buffering durations,
   codec decoder names and exportable debug report.
8. Native LibVLC/GStreamer engines, their format support and fallback tests.
9. HDR/SDR color-path verification, SuryaTV image clarity verification and
   actual Android TV playback performance (reserved for device testing).
10. Profiles, hidden-channel review, playlist ordering, backup/restore,
    and other full-app features.
11. Private licensing / activation systems and private provider assets:
    must not be published in this public repository.
12. Compilation of the newest MainActivity integration commit must pass
    before making any claim of build readiness or creating an APK.

## Release gates

- Public verification workflow must show SUCCESS at the latest source SHA.
- Verify that private URLs/keys are not bundled and that the APK contains the
  separate application ID.
- APK packaging remains manual, never automatic on push.
- Device playback and picture-quality acceptance remain with the user.
