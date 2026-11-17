package com.softppj.nextv

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Serial cleanup for detached LibVLC native resources.
 *
 * UI-owned Surfaces must be detached BEFORE enqueueing a native stop. The queue
 * never reads or mutates Activity playback state: each closure owns the old
 * player and core captured at the moment they were superseded.
 */
internal class NativeVlcCleanupQueue(
    private val executor:ExecutorService=Executors.newSingleThreadExecutor { r->
        Thread(r,"MyView-VLC-cleanup").apply{isDaemon=true}
    },
    private val onError:(Throwable)->Unit = {}
) {
    private var closed=false

    @Synchronized fun submit(cleanup:()->Unit):Boolean {
        if(closed)return false
        return try {
            executor.execute {
                try { cleanup() }
                catch(e:Throwable) { try { onError(e) } catch (_:Throwable) {} }
            }
            true
        }catch(_:RejectedExecutionException){false}
    }

    /** Accepts no further work; already queued native releases are drained. */
    @Synchronized fun shutdown(){
        if(closed)return
        closed=true
        executor.shutdown()
    }
}
