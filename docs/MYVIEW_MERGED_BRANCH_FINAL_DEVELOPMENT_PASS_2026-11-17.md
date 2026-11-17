# MyView — merged-branch consolidated source correction pass (17 November 2026)

Branch: `myview-full-app`. Applied after merge `19f4f98b4df01e0e6b4a4d11f0705694dad303fe`.

## Confirmed changes
- Isolated LibVLC controller: terminal release now has a detach phase. Its state is marked terminal under a short monitor hold, and the captured stop/registry cleanup runs on the existing serial isolated-service worker, avoiding native Binder shutdown on the Activity event thread. The legacy synchronous `release()` method is preserved for compatible callers and tests.
- MultiView: decoder stop/release loops catch all native failures, including linkage errors, so one failing decoder does not prevent cleaning up the others.
- Portable backups: exclude `last_working_url` and `last_working_channel_identity` alongside other device/session startup state, without removing user-configured playlists, favorites or other portable settings.
- Unit tests added for blocked terminal stop not retaining the controller lock, double-release prevention, and backup exclusions.
- Source contract gate additionally verifies isolated VLC teardown stays detached.

## Reviewed, no speculative behavior changes
- Licensing: signed RSA lease, device/token binding and offline expiry checks remain unchanged. Activation endpoint is HTTPS.
- Network: manifest retains cleartext permission because user-configured IPTV streams may use HTTP. Limiting this globally would break known MPEG-TS sources; retain source-specific transport rules and do not send license requests over HTTP.
- Native/Media3 teardown: Media3 player API must remain on its configured application looper; shifting `ExoPlayer.release()` to an arbitrary worker would introduce wrong-thread access. The isolated LibVLC shutdown was the actionable safe adjustment.
- Channel navigation, EPG feature-toggle generation cancellation, playlist ordering, profiles and manual APK safeguards were already corrected or covered by the merged baseline. No unrelated behavior was rewritten.
- The current LibVLC baseline is a single 3.5.1 dependency; legal redistribution compliance remains a release consideration, not a reason to alter playback now.

## Verification requirement
Run `:app:verifySingleLibVlc :app:compileDebugKotlin :app:testDebugUnitTest`, artwork and source contract validators, and the no-APK check at this exact post-correction commit. Do not claim successful verification until GitHub's run is complete. No APK build was requested or initiated.

## Scope limitation
This is a targeted, repository-wide source inventory and detailed critical-path correction pass, not proof that every branch in the 11,000-line Activity is defect-free. Physical hardware acceptance is excluded from the development-pending list by user request.
