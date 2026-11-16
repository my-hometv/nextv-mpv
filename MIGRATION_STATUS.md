# NexTV migration status

## Imported to `development`
- Android Gradle project and manifest, with package `com.softppj.nextv`.
- MainActivity, playback helpers, M3U parser, YouTube resolver, licence cache, and unit-test source.
- Temporary launcher/banner vector assets inherited from the source project.

## Deliberately not imported
- GitHub Actions workflows that assemble or publish APKs.
- HomeTV Cloudflare Workers, secrets, or licensing database.
- Inherited default IPTV playlist feeds.

## Known limitations / security review required
- Original HomeTV licensing endpoint, signed licence verification key, and default IPTV feed settings were restored at the owner's request. The shared backend must be checked for NexTV compatibility without changing its existing behavior.
- Android cleartext HTTP is disabled globally; IPTV providers that require HTTP may not work until a narrowly scoped policy is designed.
- The inherited MainActivity is under source audit for privacy, network calls, error handling, log redaction, and source branding. Blocking device and licensing checks remain open.
- The launcher and banner vectors are temporary and are **not** the approved generated NexTV logo. Replace them with the approved artwork once its file is available.
- The target SDK was changed to 35 but compatibility and UI behavior have not been verified.
- CI currently provisions Robolectric SDK jars during manual unit-test runs; dependency cache and reproducibility should be reviewed before publishing.
- Compile and JVM unit tests passed on `development` at `b5505fd` (65/65, run 42822470615); audit fixes after this SHA require retesting. Android TV device tests, APK assembly, and publishing have not been performed.

Original HomeTV repositories remain unchanged. Do not build an APK until the owner explicitly authorizes it.

## Playback priority (owner instruction)

Preserve the existing licensing features and concentrate on the previous LibVLC regression where the updated player reported playback but no video appeared. Current source already attaches a SurfaceView, logs LibVLC Vout events, and monitors decoded/displayed frame statistics. The cause is not confirmed. Next verify decoder compatibility, surface lifecycle, startup watchdog timing, and Media3 fallback on the actual TV. No APK build authorized.
