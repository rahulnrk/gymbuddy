package com.gymbuddy.app.voice

import com.gymbuddy.app.audio.AudioProbe

/** Ducks other apps (music) before the coach talks. Restore when the line ends. */
class AudioFocusDucker(
    private val probe: AudioProbe,
) : TtsDucker {
    @Volatile
    private var ducked: Boolean = false

    override fun duck() {
        if (ducked) return
        probe.requestDuckFocus()
        ducked = true
    }

    override fun restore() {
        if (!ducked) return
        probe.abandonFocus()
        ducked = false
    }
}
