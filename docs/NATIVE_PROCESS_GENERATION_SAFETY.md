# Native request ownership across Activity recreation

The initial native request allocator used an instance-local high-water mark. Recreating an Activity could reset that mark while the native service still remembered a later request, making new playback requests appear stale.

This revision uses an atomic process-wide high-water mark. Every Activity allocator instance draws from the same strictly increasing sequence; duplicate clocks and concurrent requests are handled with compare-and-set. The UI playback counter remains separate for cancellation of callbacks.

Tests now cover Activity recreation, clock regression, concurrent allocation, and stale stop rejection. **This does not implement native LibVLC symbol relocation.** Independently compiled, correctly relocated Java/JNI/native artifacts and their real playback validation are still required for the three-version unified APK.
