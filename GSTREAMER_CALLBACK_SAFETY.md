# GStreamer callback reentrancy safety

The Android GStreamer status poll now uses a playback-generation token. A callback may synchronously call play() for a new channel or release() the view. In either case, the previous poll returns immediately instead of dispatching stale status callbacks or scheduling another poll for the previous channel. Both new playback and release invalidate the old generation.

This is a standalone lifecycle safety fix, not a MainActivity engine switch. First-video-buffer timing remains an estimate rather than on-screen presentation timing.
