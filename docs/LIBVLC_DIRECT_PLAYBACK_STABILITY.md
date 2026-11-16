# LibVLC 3.5.1 playback confirmation and freeze recovery

The direct LibVLC player no longer treats `Playing` alone as proof of a healthy stream. It waits for advancing media time or position before persisting a working stream, clearing the startup watchdog, and recording healthy playback. Duplicate or stationary timeline events no longer reset the 30-second stall detector.

The startup watchdog is 15 seconds rather than 6 seconds, reducing false failures on slower live IPTV streams. Playback recovery remains guarded by the active player and screen identity.

**Validation required:** Compile against LibVLC 3.5.1, run unit tests, then verify live HLS, MPEG-TS, VOD, pause/resume, and long-running Android TV playback. No CI or APK was triggered.
