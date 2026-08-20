package com.gymbuddy.domain.voice

/**
 * Deterministic clock for barge-in tests. Never reads wall time.
 */
class FakeClock(var nowMs: Long = 0L) {
    fun now(): Long = nowMs
    fun advance(ms: Long) {
        require(ms >= 0)
        nowMs += ms
    }
}
