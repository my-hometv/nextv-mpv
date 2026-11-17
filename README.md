# MyView — complete Android TV application

This branch (`myview-full-app`) is the authoritative **complete MyView application** maintained in `my-hometv/nextv-mpv`. It retains the original full IPTV feature set and the approved MyView enhancements.

## Which branch is which?

| Branch | Purpose |
| --- | --- |
| `myview-full-app` | Complete MyView app; authoritative source and manual full-app APK workflow |
| `myview-public-build` | Earlier reduced public test build; **not** the full MyView application |
| `main` | Legacy MPV/GStreamer experiment; **not** the full MyView application |

Do not mistake a successful test or APK from a legacy branch for verification of this complete application. Always inspect the branch and commit SHA of an Actions run.

## Application identity and runtime

- App label: **MyView**
- Independent Android application ID: `com.softppj.myview`
- Existing Kotlin source namespace: `com.softppj.nextv` (deliberately retained for compatibility)
- Playback: Media3 with single LibVLC 3.5.1 dependency and isolated service
- Existing licensing data, settings keys, playlist structures and account contracts are preserved
- TV launcher artwork is supplied from `app/src/main/res/drawable/`

## Verification, packaging, and release policy

- `.github/workflows/myview-source-verification.yml` runs Python security/source-contract tests, Kotlin compilation, Android unit tests, a single-LibVLC check, and Android Lint **without generating an APK**.
- `.github/workflows/myview-full-app.yml` is a separate **manual-only** build workflow for this branch; APK creation requires explicit owner authorization.
- A green source workflow verifies its exact commit and tested checks, not all TV runtime behavior.
- Physical-TV testing remains separately deferred per project instructions.
- The repository may be public: do not add embedded playlists, tokens, signing keys, or personal credentials.

## Security and compatibility

License activation uses the HTTPS license service and a signed, device/token-bound lease. HTTP IPTV sources remain supported where users configure them; do not globally forbid cleartext traffic without accounting for compatibility. Do not rename persistent preference keys, package namespace, license URLs or interfaces solely to change on-screen branding.
