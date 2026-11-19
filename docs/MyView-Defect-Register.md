# MyView source audit — consolidated defect register
Baseline: `myview-full-app` at `0e6e38837531665cf6036cc526c5d36369fb0666` (2026-11-19).

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
| MV-INT-01 | P1 | Media-details buttons used stale index after playlist change | MainActivity.mediaDetails | Corrected; CI pending |
| MV-INT-02 | P1 | In-player channel drawer held stale numeric indices | MainActivity.showOverlayList | Corrected; CI pending |
| MV-INT-03 | P1 | Search reparsed playlists/locks for each candidate | MainActivity.searchEligible/searchableMediaIndices | Corrected; CI pending |
| MV-INT-04 | P2 | Separate media search omitted temporary failure exclusion | MainActivity.searchableMediaIndices | Corrected; CI pending |
| MV-INT-05 | P2 | Search result Back button lost originating category | MainActivity.mediaRows/channelCollection | Corrected; CI pending |
| MV-INT-06 | P2 | Media-details favorite/progress actions could outlive deleted source | MainActivity.mediaDetails | Corrected; CI pending |
| MV-INT-07 | P2 | Hardware BACK ignored the search/category return destination | MainActivity.onBackPressed | Corrected; CI pending |

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
Only label an item verified after successful **latest-commit** source-only CI.
Do not build or publish APKs as part of this audit. Never infer physical-device
success from a source-only result. Future findings should be appended here with
reproducible source evidence and automated test when possible.
