package com.gymbuddy.domain.voice.fake

import com.gymbuddy.domain.voice.AudioFocusCapabilities
import com.gymbuddy.domain.voice.AudioFocusController
import com.gymbuddy.domain.voice.DuckHandle
import com.gymbuddy.domain.voice.VoiceError

class FakeAudioFocus(
    var duckSupported: Boolean = true,
    var unavailable: Boolean = false,
) : AudioFocusController {
    var musicPlaying: Boolean = true
    var ducked: Boolean = false
        private set
    var lastReason: String? = null
        private set
    val log: MutableList<String> = mutableListOf()
    private var seq = 0
    private var active: DuckHandle? = null

    override fun requestDuck(reason: String, intensity: String): DuckHandle {
        if (unavailable) throw VoiceError.AudioFocusUnavailable()
        lastReason = reason
        if (!duckSupported) {
            val h = DuckHandle(id = "focus-${++seq}", granted = false, mode = "none")
            log += "unsupported"
            log += "denied"
            active = h
            return h
        }
        ducked = true
        val h = DuckHandle(id = "focus-${++seq}", granted = true, mode = if (intensity == "pause_if_must") "paused" else "ducked")
        log += "duck:$reason"
        active = h
        return h
    }

    override fun restore(handle: DuckHandle) {
        if (unavailable) throw VoiceError.AudioFocusUnavailable()
        if (handle.granted) {
            ducked = false
            log += "restore"
        }
        if (active?.id == handle.id) active = null
    }

    override fun release() {
        ducked = false
        active = null
        log += "release"
    }

    override fun capabilities(): AudioFocusCapabilities =
        AudioFocusCapabilities(
            duck = duckSupported && !unavailable,
            pause = false,
            unsupported = !duckSupported || unavailable,
        )
}
