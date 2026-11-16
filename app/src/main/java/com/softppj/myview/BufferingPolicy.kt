package com.softppj.myview

/** Guard for prolonged rebuffering; never switches sources or providers. */
internal object BufferingPolicy {
    fun expired(stateIsBuffering: Boolean, playRequested: Boolean,
        bufferingSinceMs: Long, nowMs: Long, timeoutMs: Long = 30000L): Boolean =
        stateIsBuffering && playRequested && bufferingSinceMs >= 0 &&
            nowMs >= bufferingSinceMs && nowMs - bufferingSinceMs >= timeoutMs
}
