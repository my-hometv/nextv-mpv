# GStreamer video-buffer arrival instrumentation

This checkpoint adds a GStreamer pad probe to the selected video sink, tracking whether an actual video buffer reached the sink. Status 5 means **video buffer arrived at the sink**, not that a frame was displayed. Status 4 remains the pipeline PLAYING state; errors, EOS and buffering retain precedence. Buffer arrival is reset for each new stream. The pad probe is removed and its pad reference released during teardown.

The instrumentation is native-only and does not change MainActivity routing. On-device rendered-frame confirmation, GStreamer Java bootstrap and decoder diagnostics are still required before claiming video playback success.
