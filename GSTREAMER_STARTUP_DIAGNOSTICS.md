# GStreamer startup diagnostic milestone

The standalone GStreamer SurfaceView reports slow first-video-buffer arrival once per stream (default 10 seconds) through onSlowStartup. The threshold is configurable and is diagnostic only: it never interrupts playback or triggers an automatic retry. Each channel resets its startup timer and callbacks are cleared on release. The obsolete undefined generation reference in release was removed.

The player remains isolated from MainActivity. These callbacks do not establish on-screen rendering or device playback compatibility. CI compilation and real Android TV tests are still required.
