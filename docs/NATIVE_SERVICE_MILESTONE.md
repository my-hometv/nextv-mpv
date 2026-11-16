# Native playback service milestone

The isolated service now initializes and plays through the **one** LibVLC version included by the selected Gradle product flavor. It accepts a real Android `Surface`, constructs `LibVLC`/`MediaPlayer`, attaches native video output, starts playback, and cleans up on stop/release. The Binder API reports readiness only when initialization succeeds. A successful start means the native player accepted the request, **not** that a network stream decoded or rendered.

This is a necessary intermediate milestone, **not** the requested single APK with three versions. `vlc351`, `vlc377` and `vlc400eap29` remain mutually exclusive flavors. Native JNI namespace relocation and multi-version packaging are outstanding.

Verification required before release:
1. Compile all three variants; API differences in LibVLC 4.0 EAP may require version-specific adapters.
2. Connect the playback UI's video surface to the session controller and verify actual rendering.
3. Test stop/switch/rebind and Surface recreation on Android TV.
4. Package all three native runtimes together only after independently renamed JNI and native dependencies are available.
5. Run all JVM and instrumentation tests and the full release audit.

Do not advertise this milestone as a verified single-APK implementation.

## Follow-up: native player event ownership

The isolated service now tracks native Playing, Stopped, EndReached and EncounteredError events against the exact player object and request generation. Stale native callbacks cannot update a replacement session. This internal state does not yet flow to the Android TV UI and is not evidence of decoded frames. The Activity continues to use its existing direct LibVLC player until the isolated service is integrated into the actual playback route. Surface lifecycle must follow Android `SurfaceHolder.Callback`: attach only after `surfaceCreated`, stop/detach before `surfaceDestroyed` returns.
