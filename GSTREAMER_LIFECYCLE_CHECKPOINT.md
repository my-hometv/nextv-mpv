# GStreamer playback lifecycle checkpoint

The native GStreamer SDK 1.28.8 compile and Gradle merge passed in GitHub Actions.

The standalone GstreamerPlaybackView now polls the native bus on the Android main looper at 250 ms intervals while playback is active. It reports state transitions via a listener, distinguishes waiting from pipeline PLAYING, and stops polling after EOS/error or release. Replaying a channel resets polling and the last status. The native adapter tracks pipeline state changes and maintains buffering/error/EOS precedence.

This is a standalone integration building block, **not** evidence of actual decoded video frames. The native PLAYING state is not first-frame confirmation. MainActivity is intentionally not switched over; device validation, Android GStreamer Java initialization, HTTP headers, decoder selection, and actual playback tests remain pending. No APK should be released based on this checkpoint alone.
