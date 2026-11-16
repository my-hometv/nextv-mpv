# MyView — standalone public Android TV development

This public branch contains independent MyView Android TV code. It uses no bundled
private playlist, provider credentials, activation token, signing key, or proprietary assets.
The app uses the unique Android application ID `com.softppj.myview` so it can coexist
with other TV applications.

## Current features

This remains a standalone, public-source MyView test application, not a complete
port of the private NexTV project. The following features are implemented in
public source but some require on-device TV verification.

- Media3 HTTPS HLS/DASH/MP4 playback from URLs entered on the device
- Up to five user-provided local M3U playlists, named favorites, hidden channels
- DPAD navigation, numeric channel preview, paged overlay drawer, channel logos
- User-provided XMLTV (plain or gzip), now/next EPG and paginated guide
- Local settings backup/restore and redacted playback diagnostics export
- DPAD-focusable channel list and channel-up/channel-down remote shortcuts
- Limited same-URL retry after eligible playback failures; no automatic URL substitutions
- Provider webpage compatibility test using a restricted HTTPS WebView
- Unit tests for playlist parsing, safety and limits

This is a standalone development build, not yet a complete full-featured IPTV product.
It does not include household profiles, production EPG, licensing, protected-content
DRM bypass, private playlists, or additional playback engines.

## Verification and APK policy

Pushes to `myview-public-build` run `.github/workflows/myview-verify.yml`.
The verification workflow compiles Android Kotlin and runs local JVM tests;
it never assembles or uploads an APK. The manually dispatched APK workflow
uses the same verified SDK initialization and runs unit tests before packaging.

The separate `.github/workflows/myview-public-apk.yml` is **manual-only** and
should be used after compilation passes and the user approves APK preparation.

For local checks with JDK 17 and Android SDK 36 installed:

```bash
gradle :app:compileDebugKotlin :app:testDebugUnitTest
```

Only add provider-authorized streams on your own device. Do not commit playlist
contents, account details, private credentials or signing keys to this public project.
