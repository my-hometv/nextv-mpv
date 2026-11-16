# Native playback progress semantics

The isolated service now counts *advancing* native playback time and position values, rather than every callback. Duplicate timestamps and repeated position events no longer keep a frozen stream artificially healthy. Counters and last observed values reset on each session, on stop, and on failed initialization.

For streams without usable time/position updates, the counter remains zero. The Activity deliberately does not classify these as stalled solely from an absent counter: a future frame-level signal is required to reliably detect frozen live streams. Native Playing remains a player event, not proof of visible video.

The current flavor model still packages one LibVLC release per APK. The requested three-version single APK remains blocked by overlapping Java/JNI/native library symbols. No build or device test was performed.
