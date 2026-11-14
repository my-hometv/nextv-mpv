# GStreamer first-video-buffer latency instrumentation

The standalone Kotlin playback view now reports the elapsed monotonic time between invoking native playback and first observing status 5 (a video buffer reaching the selected sink). The callback is emitted at most once per stream, reset on every channel change, and cleared on release. Native status 5 can also be returned while the pipeline is still transitioning into PLAYING, as long as no buffering, EOS or error state has precedence.

This latency is an **instrumentation estimate** with up to 250 ms polling granularity, not a measurement of on-screen first-frame time. No MainActivity engine switch or APK build is included. Android TV testing remains necessary.
