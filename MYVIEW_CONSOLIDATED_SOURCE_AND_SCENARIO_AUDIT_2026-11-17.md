# MyView — consolidated source and scenario audit (17 November 2026)

## Scope and release rule
Branch: `myview-full-app`. Code baseline: `d23501b3c27595aae25eedbf92829e4a030c6118`.
This pass does not assemble or publish an APK. Original NexTV repository, license server,
activation keys, private playlists and original installed application are unchanged.

## Source audit: specific risks and corrections

| Severity | Inspection result | Correction / current engineering state |
| --- | --- | --- |
| P0 | Direct LibVLC native stop/release could run synchronously in UI teardown | NativeVlcCleanupQueue now executes *detached* old player/core cleanup on a serial worker. Surface detach remains on its UI owning thread |
| P0 | An isolated LibVLC Binder stop could hold the playback controller lock and UI | detachActiveStop and generation-specific detachStop capture the old session before submitting IPC to a separate isolated-service worker |
| P0 | Binder readiness, startup and playback-state polling occurred on UI thread | Packaged identity is checked locally; registry readiness/start and playback polling are scheduled on an isolated-service worker, callbacks checked against channel/surface/playback generation |
| P0 | Native service could hang indefinitely during startup | A bounded 12-second startup timeout invalidates the request and initiates existing recovery; late callbacks are rejected |
| P0 | Rapid remote keys during optional probing could discard work or block on native teardown | RemotePreemptionPolicy and generation guards preserve the action; old background native resources are independently captured and disposed |
| P1 | Background scans contended with foreground startup/buffering | Optional decoder probes are disabled for any foreground session, limited to one decoder while idle, and resume only after an idle delay and generation recheck |
| P1 | Playlist refresh could resurrect an older channel selection | Current live channel identity is captured at refresh application time; source+URL matching prohibits cross-playlist switches |
| P1 | MultiView starts multiple decoders in the same UI event | Decoder prepare/play is staggered and checks dialog/lifecycle/license ownership before running |
| P1 | Native cleanup tasks queued during Activity shutdown could be abandoned | Deferred probes are tracked and drained during shutdown; worker queues reject post-shutdown operations |

## Realistic automated action journeys (not device emulation)
- `MyViewActionScenarioTest`: repeated remote inputs while starting, foreground
  decoder exclusivity, live channel change during playlist refresh, cross-provider
  URL collision, saved channel recovery after teardown, prebuffer access checks,
  HTTP 429/provider capacity retries, hidden/locked MultiView filtering,
  custom channel ordering after provider reordering, backup secret exclusions,
  adaptive quality and VOD-only refresh matching.
- `NativeVlcCleanupQueueTest`: native stop blocked while a remote action runs,
  ordered A→B→C replacement cleanup, exception isolation, queued cleanup during
  shutdown.
- `IsolatedVlcSessionControllerTest`: old-generation stop cannot reclaim a newer
  session, blocked native stop cannot hold controller lock, stale Surface stop.
- `NativeSurfaceRecoveryGateTest`: late native result after recovery request
  cannot reattach a timed-out Surface.
- Existing repository regression tests cover URL recovery, playback generations,
  SignedLicenseCache, EPG bounds, M3U parsing, backup safety, Remote event policy,
  picture-quality policy, hidden channels, MultiView access, and more.

## Media and feature integration reviewed
- HLS, DASH, Smooth Streaming and MPEG-TS paths are configured through Media3
  source/extractor factories; LibVLC 3.5.1 remains the secondary native engine.
  This is source compatibility, not evidence that every stream plays.
- Picture Standard preserves native Media3 rendering, with optional preset
  effects; Best Available retains adaptation instead of forcing peak bitrate.
- HDR transfer reporting is diagnostic. Hardware HDR→SDR tone mapping, 1080i
  deinterlacing, colors and frame pacing cannot be declared correct from JVM tests.
- EPG XMLTV input is bounded and fetched off the main thread. Favorites, hidden
  channels, viewer profiles and backup/restore entry points remain in MainActivity.
- SignedLicenseCache validates an RSA signature, device and token binding,
  expiration, offline ceiling and clock rollback. This pass did not change
  licensing or entitlement enforcement.
- BackupSafety excludes license/token/password/authorization-like settings.

## Remaining evidence and limitations
This is an audited set of critical integration paths, not a formal proof that every
line of a ~11,000-line Activity and every dependency is correct. No JVM test can
faithfully reproduce device-specific MediaCodec, native LibVLC, Android Binder
kernel behavior, TV remote firmware, display HDR tone mapping or memory exhaustion.

Media3 Player APIs, including release, remain on their designated application
looper to avoid introducing a wrong-thread exception. Native UI Surface detach
also remains on the UI thread. The stock Media3 release timeout applies.

Source-only CI on the latest application code is the last automated gate.
An APK release remains on hold until explicitly approved by the owner.

## Acceptance interpretation
Mark a scenario **automated PASS** only when its test is executed in a successful
source-verification run for the same source SHA. Do not translate that into
**hardware PASS** or promise a zero-crash application.
