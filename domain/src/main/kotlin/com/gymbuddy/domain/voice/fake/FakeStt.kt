package com.gymbuddy.domain.voice.fake

import com.gymbuddy.domain.voice.FakeClock
import com.gymbuddy.domain.voice.SttChunk
import com.gymbuddy.domain.voice.SttFinalHash
import com.gymbuddy.domain.voice.SttProvider
import com.gymbuddy.domain.voice.VoiceError

class FakeStt(
    private val clock: FakeClock,
    override var supportsStreaming: Boolean = true,
    override var supportsLanguageId: Boolean = true,
    override var supportsOffline: Boolean = false,
    var unavailable: Boolean = false,
) : SttProvider {
    var recognizing: Boolean = false
        private set
    val chunks: MutableList<SttChunk> = mutableListOf()
    val languages: MutableList<String> = mutableListOf()

    override fun startRecognition(languages: List<String>) {
        if (unavailable) throw VoiceError.SttUnavailable()
        recognizing = true
        this.languages.clear()
        this.languages += languages
    }

    override fun abort() {
        recognizing = false
    }

    fun emitPartial(text: String, lang: String? = null, confidence: Double? = 0.4): SttChunk {
        if (unavailable) throw VoiceError.SttUnavailable()
        if (!recognizing) startRecognition()
        val chunk = SttChunk(
            text = text,
            isFinal = false,
            lang = lang,
            confidence = confidence,
            recordedAtMs = clock.now(),
        )
        chunks += chunk
        return chunk
    }

    fun emitFinal(
        text: String,
        confidence: Double? = 0.9,
        durationMs: Double? = 800.0,
        lang: String? = "en",
        mixed: Boolean = false,
    ): SttChunk {
        if (unavailable) throw VoiceError.SttUnavailable()
        if (!recognizing) startRecognition()
        val chunk = SttChunk(
            text = text,
            isFinal = true,
            lang = lang,
            confidence = confidence,
            durationMs = durationMs,
            sttFinalHash = SttFinalHash.of(text),
            recordedAtMs = clock.now(),
            mixed = mixed,
        )
        chunks += chunk
        recognizing = false
        return chunk
    }

    fun emitMalformed(detail: String = "empty"): Nothing {
        throw VoiceError.MalformedSttResponse(detail)
    }

    fun lastFinal(): SttChunk? = chunks.lastOrNull { it.isFinal }
    fun lastPartial(): SttChunk? = chunks.lastOrNull { !it.isFinal }
}
