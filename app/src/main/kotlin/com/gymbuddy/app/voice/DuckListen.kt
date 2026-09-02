package com.gymbuddy.app.voice

/**
 * Mic for First Sound. Tests supply a fake.
 * Production captures with AudioRecord (no audio focus) and streams to Scribe.
 * SpeechRecognizer is not used — it pauses music.
 */
interface DuckListen {
    fun start()
    fun stop()
    val userSpeaking: Boolean
    var listener: DuckListenListener?
}

interface DuckListenListener {
    fun onVad(speech: Boolean)
    fun onPartial(text: String)
    fun onFinal(text: String)
    fun onError(message: String)
}
