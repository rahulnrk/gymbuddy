package com.gymbuddy.domain.voice.fake

import com.gymbuddy.domain.voice.Clock

/**
 * Deterministic clock for barge-in tests. Never reads wall time.
 */
class FakeClock(var nowMs: Long = 0L) : Clock {
    override fun now(): Long = nowMs
    fun advance(ms: Long) {
        require(ms >= 0)
        nowMs += ms
    }
}
