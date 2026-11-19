# MyView source audit — consolidated defect register
Baseline: `myview-full-app` at `0e6e38837531665cf6036cc526c5d36369fb0666` (2026-11-19).

Verified correction commit: `3c0532a542312c14b3fc076d4bdf258591118f3d`.
Source-only workflow: https://github.com/my-hometv/nextv-mpv/actions/runs/44496001556 — **success** (Kotlin compilation, unit tests, Python security/integration tests, Android Lint, and no-APK assertion).

## Scope and evidence
This is a living **source-level integration register**, not proof that every
Android TV, network, decoder, or remote interaction has been exercised. Review
encompasses code paths/policies for playback ownership, fallback, playlists,
EPG, search, hidden/locked content, favorites, household profiles, backup,
signed license, background feature toggles, artwork, and MultiView.
The source-only GitHub workflow runs Kotlin compilation, unit tests,
Python security/integration contracts, Android Lint, and a no-APK check.
There is no physical-TV or Android Activity instrumentation verdict here.

| ID | Priority | Confirmed code-level defect | Planned code path | Status |
|---|---|---|---|---|
| MV-INT-01 | P1 | Media-details buttons used stale index after playlist change | MainActivity.mediaDetails | Source-verified — run 44496001556 |
| MV-INT-02 | P1 | In-player channel drawer held stale numeric indices | MainActivity.showOverlayList | Source-verified — run 44496001556 |
| MV-INT-03 | P1 | Search reparsed playlists/locks for each candidate | MainActivity.searchEligible/searchableMediaIndices | Source-verified — run 44496001556 |
| MV-INT-04 | P2 | Separate media search omitted temporary failure exclusion | MainActivity.searchableMediaIndices | Source-verified — run 44496001556 |
| MV-INT-05 | P2 | Search result Back button lost originating category | MainActivity.mediaRows/channelCollection | Source-verified — run 44496001556 |
| MV-INT-06 | P2 | Media-details favorite/progress actions could outlive deleted source | MainActivity.mediaDetails | Source-verified — run 44496001556 |
| MV-INT-07 | P2 | Hardware BACK ignored the search/category return destination | MainActivity.onBackPressed | Source-verified — run 44496001556 |
| MV-INT-08 | P1 | Scheduled EPG auto-tune could fall back to an unrelated channel sharing a URL; reminder retained an old index | MainActivity.checkEpgTune | Source-verified — run 44496001556 |

| MV-INT-09 | P1 | PIN callback could retune a different channel after refresh | MainActivity.playIndex/resumeAuthorizedChannel | Source-verified — run 44500817304 |
| MV-INT-10 | P1 | Media category and artist buttons captured stale channel indexes | MainActivity.mediaLibrary/mediaMusic/mediaSeries/youtubeLibrary | Source-verified — run 44500817304 |
| MV-INT-11 | P2 | URL-hash resume history combined unrelated movie/song entries | MediaProgressPolicy and MainActivity.mediaKey | Source-verified — run 44500817304 |

## Cross-feature review matrix
- **Playlist, channel history, hidden/lock, source deletion:** reviewed
  transition eligibility, last-channel policy and generation invalidation;
  earlier identity regressions remain under tests.
- **Playback (Media3/LibVLC), native teardown, decoder ownership, recovery:**
  inspected ownership and supporting policy/test modules; no new defect
  claimed in this pass. Real device tests still required.
- **EPG merging, scheduling, cancellation:** inspected EPG source policies
  and their existing tests; device performance and live XMLTV feeds unverified.
- **Search, library and YouTube:** inspected Activity wiring and media search
  policy, including Malayalam, metadata, EPG titles and inaccessible entries.
- **Profiles, backup and license:** reviewed relevant boundary policies and
  automation coverage; no alteration to activation or key material.
- **MultiView, prebuffer and background toggles:** inspected policy boundaries;
  actual device/decoder resource behavior unverified.
- **Remote navigation and rendering:** source navigation paths reviewed;
  key-repeat/focus behavior requires Android TV testing.

## Verification discipline
Only label an item source-verified after successful CI against the relevant code commit; documentation-only updates do not alter tested source.
Do not build or publish APKs as part of this audit. Never infer physical-device
success from a source-only result. Future findings should be appended here with
reproducible source evidence and automated test when possible.

## Follow-up audit 2026-11-19
The additional PIN, media-category and per-item VOD resume-history corrections passed source-only CI run 44500817304. Migration from the original URL-only progress key is attempted only for unambiguous media URLs; ambiguous shared URLs cannot be safely attributed to a specific historical item and are intentionally not copied.

## Final integration follow-up — 2026-11-19
| ID | Priority | Confirmed defect or risk | Correction | Status |
|---|---|---|---|---|
| MV-INT-12 | P1 | Simultaneous due EPG reminders were removed but only the last was presented | EpgDuePolicy and checkEpgTune grouped reminder chooser; latest automatic tune wins | Source-verified — run 44503475312 |
| MV-INT-13 | P2 | Seek/Pause dialogs held stale player references after channel switch | Playback generation and player-ownership guard | Source-verified — run 44503475312 |
| MV-INT-14 | P2 | Clearing one shared-URL media progress entry removed another entry's ambiguous legacy history | Preserve ambiguous legacy record with a migration-block marker | Source-verified — run 44503475312 |

This follow-up retains the original engine and licensing design. Source-only CI passed on code commit `7a5ac3a31b9e6e2f1e2c386d33c497d89002c089`: https://github.com/my-hometv/nextv-mpv/actions/runs/44503475312. No APK was generated. Physical-TV playback remains unverified.
