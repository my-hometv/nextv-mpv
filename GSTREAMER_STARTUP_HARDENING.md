# GStreamer native startup hardening checkpoint

Native player startup now checks that a GstBus was created and records immediate GST_STATE_CHANGE_FAILURE rather than waiting for a bus message. GstMessage ASYNC_DONE is explicitly not interpreted as first-frame rendering. The Kotlin SurfaceView enforces main-thread calls for initialize, play and release, consistent with its main-looper bus polling and Surface lifecycle.

This checkpoint is a build-time hardening stage. Successful compilation is not device playback verification. The standalone player is not yet routed from MainActivity. Android GStreamer Java bootstrap, actual frame confirmation, hardware decoder selection, authenticated headers and live-stream regression tests remain pending.
