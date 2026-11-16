# Native Surface recovery integration

The one-shot NativeSurfaceRecoveryGate is now used by the actual MainActivity SurfaceHolder callbacks rather than existing as unconnected test scaffolding. A single gate owns both asynchronous startup failures and Surface destruction, preventing two independent recovery paths from firing for the same generation.

On Surface destruction, the old native session is stopped, the watchdog is cancelled, and recovery is scheduled only when that Surface was attached and recovery has not already been requested. Old Surface callbacks cannot restart the invalidated generation.

The recovery handler still checks the Activity playback generation and active screen before acting. This commit does not include native multi-version LibVLC relocation, compilation, or TV validation.
