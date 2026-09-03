package com.gymbuddy.domain.voice.fake

import com.gymbuddy.domain.voice.VoiceError
import com.gymbuddy.domain.voice.WakeWordObservation
import com.gymbuddy.domain.voice.WakeWordProvider

/** Test fake for [com.gymbuddy.domain.voice.WakeWordProvider]. Dormant for v1; see that port's KDoc. */
class FakeWakeWord(
    var unavailable: Boolean = false,
) : WakeWordProvider {
    override var started: Boolean = false
        private set
    var last: WakeWordObservation = WakeWordObservation(detected = false)
        private set
    val history: MutableList<WakeWordObservation> = mutableListOf()

    override fun start() {
        if (unavailable) throw VoiceError.WakeWordUnavailable()
        started = true
        last = WakeWordObservation(detected = false)
    }

    override fun stop() {
        started = false
        last = WakeWordObservation(detected = false)
    }

    fun inject(observation: WakeWordObservation): WakeWordObservation {
        if (unavailable) throw VoiceError.WakeWordUnavailable()
        last = observation
        history += observation
        return observation
    }
}
