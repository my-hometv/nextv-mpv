# MyView — public Android TV playback test

This is a **clean-room public test application**, NOT the full private NexTV-derived MyView application.

It provides a standalone Android TV launcher, a Media3 stream test for authorized
HTTPS DASH/HLS/media URLs, and a WebView test for authorized provider webpages.
It does **not** contain the private NexTV playlist, licensing, favorites, LibVLC,
engine fallback or recovery systems. It does not bypass DRM or guarantee Widevine
playback. Use this APK to evaluate Android TV playback compatibility.

Package: `com.softppj.myview`. Build: GitHub Actions workflow
`MyView public test APK`, artifact `MyView.apk`.

No private source or credentials are copied here. The full private application
remains at `my-hometv/nextv` branch `dash-standalone`.
