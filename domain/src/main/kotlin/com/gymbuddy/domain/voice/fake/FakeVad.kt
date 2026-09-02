package com.gymbuddy.domain.voice.fake

import com.gymbuddy.domain.voice.VadListener
import com.gymbuddy.domain.voice.VadObservation
import com.gymbuddy.domain.voice.VadProvider
import com.gymbuddy.domain.voice.VoiceError

class FakeVad(
    var unavailable: Boolean = false,
) : VadProvider {
    override var started: Boolean = false
        private set
    var last: VadObservation = VadObservation(speech = false)
        private set
    val history: MutableList<VadObservation> = mutableListOf()
    private var listener: VadListener? = null

    override fun start() {
        if (unavailable) throw VoiceError.VadUnavailable()
        started = true
        last = last.copy(speech = false)
    }

    override fun stop() {
        started = false
        last = VadObservation(speech = false)
    }

    override fun setListener(listener: VadListener?) {
        this.listener = listener
    }

    fun inject(observation: VadObservation): VadObservation = observe(observation)

    fun observe(observation: VadObservation): VadObservation {
        if (unavailable) throw VoiceError.VadUnavailable()
        last = observation
        history += observation
        listener?.onObservation(observation)
        return observation
    }
}
