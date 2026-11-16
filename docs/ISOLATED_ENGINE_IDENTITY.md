# Isolated engine identity across playback recovery

A remote LibVLC service does not create a local `vlcPlayer`. Earlier playback-confirmation and failure paths inferred `Media3` whenever `vlcPlayer` was null, accidentally saving a native stream as Media3 and retrying with the wrong engine.

This commit adds `isolatedActiveEngineId` to Activity session state and uses it when confirming a working stream, handling playback failure, and disabling a running engine in Settings. The identity is cleared on playback teardown and on Surface destruction for the same generation. Native polling tasks are cancelled before recovery so callbacks from the failed Surface cannot repeatedly trigger recovery.

The service still supports only the LibVLC release packaged in its Gradle flavor. This is a correctness fix, not the three-version native packaging solution. No CI workflow or APK build was run.
