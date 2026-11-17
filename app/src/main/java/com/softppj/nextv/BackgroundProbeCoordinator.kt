package com.softppj.nextv

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Adapter between the recovery selector and an asynchronous stream checker.
 * The checker must use a separate, non-rendering player/network probe; this
 * coordinator never touches the foreground player or native engine internals.
 *
 * A caller invokes dispatch() for each probe. A callback reports the outcome,
 * after which the caller may dispatch() again. Synchronous checker failures
 * count as failed probes, not crashes or successful channels.
 */
internal class BackgroundProbeCoordinator<T>(
    private val scan: BackgroundRecoveryScan<T>
) {
    private class TimedProbe<T>(val probe: BackgroundRecoveryScan.Probe<T>) {
        val engine: String get() = probe.attempt.engine
        val completed = AtomicBoolean(false)
        val timer = AtomicReference<ScheduledFuture<*>?>()
        fun cancel() {
            completed.set(true)
            timer.get()?.cancel(false)
        }
    }

    // Collections.newSetFromMap works on API 23; ConcurrentHashMap.newKeySet requires 24.
    private val timedProbes: MutableSet<TimedProbe<T>> =
        java.util.Collections.newSetFromMap(ConcurrentHashMap<TimedProbe<T>, Boolean>())

    /** Returns true only if a probe was actually dispatched. */
    fun dispatch(check: (BackgroundRecoveryScan.Probe<T>, (Boolean) -> Unit) -> Unit): Boolean {
        val probe = scan.next() ?: return false
        val callbackInvoked = AtomicBoolean(false)
        val complete: (Boolean) -> Unit = { succeeded ->
            // A native player may deliver Playing, Error and timeout callbacks
            // concurrently. Only the first callback owns this reservation.
            if (callbackInvoked.compareAndSet(false, true)) {
                scan.finish(probe, succeeded)
            }
        }
        try {
            check(probe, complete)
        } catch (error: Exception) {
            // A synchronous completion may throw from checker code afterward.
            // Never turn that exception into a second, fabricated failure.
            if (callbackInvoked.get()) throw error
            complete(false)
        }
        return true
    }

    /**
     * Completion-aware variant for hosts that schedule the next check after a result.
     * accepted=false means the callback was stale, duplicated, or superseded.
     * The host must not advance the scan on a rejected completion.
     */
    fun dispatchWithResult(
        check: (BackgroundRecoveryScan.Probe<T>, (Boolean) -> Unit) -> Unit,
        onResult: (Boolean) -> Unit
    ): Boolean {
        val probe = scan.next() ?: return false
        // Do not turn a host callback exception into a second, fabricated failure.
        // A checker can invoke completion synchronously before returning.
        val callbackInvoked = AtomicBoolean(false)
        val completion: (Boolean) -> Unit = { success ->
            // Claim the callback before invoking the host. Duplicate callbacks must
            // not advance a later reservation or notify the host a second time.
            if (callbackInvoked.compareAndSet(false, true)) {
                val accepted = scan.finish(probe, success)
                onResult(accepted)
            }
        }
        try {
            check(probe, completion)
        } catch (error: Exception) {
            if (!callbackInvoked.get()) completion(false)
            else throw error
        }
        return true
    }

    /** The host's timeout handler can expire an outstanding probe safely. */
    fun timeout(probe: BackgroundRecoveryScan.Probe<T>): Boolean {
        val accepted = scan.expire(probe)
        if (accepted) cancelTimedLease(probe)
        return accepted
    }

    private fun cancelTimedLease(probe: BackgroundRecoveryScan.Probe<T>) {
        timedProbes.forEach { lease ->
            if (lease.probe === probe) {
                lease.cancel()
                timedProbes.remove(lease)
            }
        }
    }

    /**
     * Completion-aware timeout. Only the active reservation can expire.
     * Hosts should advance only when accepted is true.
     */
    fun timeoutWithResult(
        probe: BackgroundRecoveryScan.Probe<T>,
        onResult: (Boolean) -> Unit
    ): Boolean {
        val accepted = timeout(probe)
        onResult(accepted)
        return accepted
    }

    /**
     * Dispatch a background probe with a bounded lifetime. A timeout and checker
     * callback race for one completion; neither can advance the scan twice.
     * Scheduler and non-rendering checker are provided by the host.
     */
    fun dispatchWithTimeout(
        scheduler: ScheduledExecutorService,
        timeoutMillis: Long,
        check: (BackgroundRecoveryScan.Probe<T>, (Boolean) -> Unit) -> Unit,
        onResult: (Boolean) -> Unit
    ): Boolean {
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
        val probe = scan.next() ?: return false
        val lease = TimedProbe(probe)
        timedProbes.add(lease)
        val callbackInvoked = AtomicBoolean(false)
        val completion: (Boolean) -> Unit = { success ->
            // Claim completion before publishing callback state. A late native
            // callback after timeout/cancellation must not masquerade as the
            // accepted result or hide a checker exception.
            if (lease.completed.compareAndSet(false, true)) {
                callbackInvoked.set(true)
                lease.timer.get()?.cancel(false)
                timedProbes.remove(lease)
                onResult(scan.finish(probe, success))
            }
        }
        try {
            lease.timer.set(scheduler.schedule({
                if (lease.completed.compareAndSet(false, true)) {
                    timedProbes.remove(lease)
                    onResult(scan.expire(probe))
                }
            }, timeoutMillis, TimeUnit.MILLISECONDS))
            if (lease.completed.get()) lease.timer.get()?.cancel(false)
            if (!lease.completed.get()) check(probe, completion)
        } catch (error: Exception) {
            if (!callbackInvoked.get() && lease.completed.compareAndSet(false, true)) {
                lease.timer.get()?.cancel(false)
                timedProbes.remove(lease)
                onResult(scan.finish(probe, false))
            } else if (callbackInvoked.get()) {
                throw error
            }
        }
        return true
    }

    /**
     * Reconcile runtime engine availability while background scanning continues.
     * The scan invalidates reservations from disabled engines; matching timed
     * leases are cancelled so their delayed callbacks cannot wake the host.
     * A re-enabled engine is not automatically reintroduced mid-scan.
     */
    fun retainEnabledEngines(enabledEngines: Collection<String>): Boolean {
        val allowed = enabledEngines.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val changed = scan.retainEnabledEngines(allowed)
        if (changed) {
            timedProbes.forEach { lease ->
                if (lease.engine !in allowed) {
                    lease.cancel()
                    timedProbes.remove(lease)
                }
            }
        }
        return changed
    }

    /**
     * Atomically supersede the previous discovery plan from the coordinator's
     * perspective. Hosts should use this entry point rather than calling
     * scan.start directly when timed checks may still be running.
     */
    fun restart(
        channels: List<BackgroundRecoveryScan.Channel<T>>,
        enabledEngines: List<String>,
        playingChannelId: String? = null
    ) {
        // Invalidate reservations before cancelling leases: a callback racing
        // with restart cannot confirm the old channel in the new scan.
        scan.start(channels, enabledEngines, playingChannelId)
        timedProbes.forEach { it.cancel() }
        timedProbes.clear()
    }

    /** Cancel outstanding timer tasks as well as invalidate their scan generations. */
    fun cancel() {
        // Invalidate scan reservations first so an already-running timeout or
        // native callback cannot publish a successful result after cancellation.
        scan.cancel()
        timedProbes.forEach { it.cancel() }
        timedProbes.clear()
    }
}
