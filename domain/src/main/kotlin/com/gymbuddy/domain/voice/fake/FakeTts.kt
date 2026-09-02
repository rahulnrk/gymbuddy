package com.gymbuddy.domain.voice.fake

import com.gymbuddy.domain.voice.Clock
import com.gymbuddy.domain.voice.TtsHandle
import com.gymbuddy.domain.voice.TtsProvider
import com.gymbuddy.domain.voice.VoiceError

class FakeTtsHandle(
    override val id: String,
    private val clock: Clock,
    private val owner: FakeTts,
    override val startedAtMs: Long,
) : TtsHandle {
    override var cancelled: Boolean = false
        private set
    override var speaking: Boolean = true
        internal set
    override var cancelledAtMs: Long? = null
        private set
    var ended: Boolean = false
        internal set

    override fun cancel() {
        if (!speaking && ended) return
        (clock as? FakeClock)?.advance(owner.cancelDelayMs)
        if (owner.interruptFails) throw VoiceError.InterruptionFailure()
        cancelled = true
        speaking = false
        cancelledAtMs = clock.now()
        owner.onCancelled(this)
    }

    fun complete() {
        speaking = false
        ended = true
        owner.onEnded(this)
    }
}

class FakeTts(
    private val clock: Clock,
    var unavailable: Boolean = false,
    var cancelDelayMs: Long = 0,
    var interruptFails: Boolean = false,
) : TtsProvider {
    override var speaking: Boolean = false
        private set
    var lastHandle: FakeTtsHandle? = null
        private set
    var lastText: String? = null
        private set
    private var seq = 0

    override fun speak(text: String, lang: String): TtsHandle {
        if (unavailable) throw VoiceError.TtsUnavailable()
        lastHandle?.let { if (it.speaking) it.cancel() }
        val handle = FakeTtsHandle(
            id = "tts-${++seq}",
            clock = clock,
            owner = this,
            startedAtMs = clock.now(),
        )
        lastHandle = handle
        lastText = text
        speaking = true
        return handle
    }

    override fun stop() {
        lastHandle?.let { h ->
            if (h.speaking) h.cancel()
        }
        speaking = false
    }

    internal fun onCancelled(handle: FakeTtsHandle) {
        if (lastHandle === handle) speaking = false
    }

    internal fun onEnded(handle: FakeTtsHandle) {
        if (lastHandle === handle) speaking = false
    }
}
