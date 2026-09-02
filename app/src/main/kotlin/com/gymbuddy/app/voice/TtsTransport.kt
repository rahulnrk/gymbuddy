package com.gymbuddy.app.voice

/**
 * Byte pipe for TTS. Tests supply a scripted fake. No vendor types.
 */
interface TtsTransport {
    fun open(url: String, headers: Map<String, String>, listener: Listener)
    fun send(text: String)
    fun close()

    interface Listener {
        fun onOpen()
        fun onMessage(text: String)
        fun onFailure(error: Throwable, httpCode: Int? = null)
        fun onClosed()
    }
}

interface TtsPlayback {
    /** Throws [AudioOutputFailedException] if the track cannot start. */
    fun start(sampleRateHz: Int)
    fun write(pcm: ByteArray)
    /** Wait until written PCM has played. Cancel must not call this. */
    fun drain()
    fun stopAndFlush()
    val playing: Boolean
}

interface TtsDucker {
    fun duck()
    fun restore()
}

object NoOpDucker : TtsDucker {
    override fun duck() = Unit
    override fun restore() = Unit
}
