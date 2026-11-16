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

1. Channel drawer, number-key preview, multiple local playlists, per-playlist
   last-channel memory, a single favorites set per playlist, and XMLTV now/next
   importer have now been added to the public code. Their newest integration
   still needs a full CI pass and TV validation.
2. Full channel-logo rendering, complete EPG grid, provider metadata and
   extended visual polish remain incomplete.
3. Full LEFT → LEFT → LEFT navigation, D-pad focus edge cases and multiple
   independently named favorite lists remain incomplete.
4. Hidden channel review, per-channel visibility controls and complete
   app data backup/restore remain incomplete.
5. Automatic alternative URL/engine discovery and validated health scoring.
   Deliberately not added until safe source-pinning and tests exist.
6. Audio/video *lip-sync measurement* and direct timestamp correction.
   Clock-progression recovery is not a substitute.
7. Stream-health video frame counters, accurate buffering durations,
   codec decoder names and exportable debug report.
8. Native LibVLC/GStreamer engines, their format support and fallback tests.
9. HDR/SDR color-path verification, SuryaTV image clarity verification and
   actual Android TV playback performance (reserved for device testing).
10. Profiles, comprehensive playlist ordering, backup/restore,
    and other full-app features remain incomplete.
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
