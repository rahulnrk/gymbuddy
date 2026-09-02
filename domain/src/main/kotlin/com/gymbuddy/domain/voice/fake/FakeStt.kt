package com.gymbuddy.domain.voice.fake

import com.gymbuddy.domain.voice.Clock
import com.gymbuddy.domain.voice.SttChunk
import com.gymbuddy.domain.voice.SttFinalHash
import com.gymbuddy.domain.voice.SttListener
import com.gymbuddy.domain.voice.SttProvider
import com.gymbuddy.domain.voice.VoiceError

class FakeStt(
    private val clock: Clock,
    override var supportsStreaming: Boolean = true,
    override var supportsLanguageId: Boolean = true,
    override var supportsOffline: Boolean = false,
    var unavailable: Boolean = false,
) : SttProvider {
    override var recognizing: Boolean = false
        private set
    var startRecognitionCalls: Int = 0
        private set
    var abortCalls: Int = 0
        private set
    val chunks: MutableList<SttChunk> = mutableListOf()
    val languages: MutableList<String> = mutableListOf()
    private var listener: SttListener? = null

    override fun startRecognition(languages: List<String>) {
        if (unavailable) throw VoiceError.SttUnavailable()
        startRecognitionCalls += 1
        recognizing = true
        this.languages.clear()
        this.languages += languages
    }

    override fun abort() {
        abortCalls += 1
        recognizing = false
    }

    override fun setListener(listener: SttListener?) {
        this.listener = listener
    }

    fun emitPartial(text: String, lang: String? = null, confidence: Double? = 0.4): SttChunk =
        pushPartial(text, lang, confidence)

    fun pushPartial(text: String, lang: String? = null, confidence: Double? = 0.4): SttChunk {
        if (unavailable) throw VoiceError.SttUnavailable()
        check(recognizing) { "STT is not recognizing" }
        val chunk = SttChunk(
            text = text,
            isFinal = false,
            lang = lang,
            confidence = confidence,
            recordedAtMs = clock.now(),
        )
        chunks += chunk
        listener?.onChunk(chunk)
        return chunk
    }

    fun emitFinal(
        text: String,
        confidence: Double? = 0.9,
        durationMs: Double? = 800.0,
        lang: String? = "en",
        mixed: Boolean = false,
    ): SttChunk = pushFinal(text, confidence, durationMs, lang, mixed)

    fun pushFinal(
        text: String,
        confidence: Double? = 0.9,
        durationMs: Double? = 800.0,
        lang: String? = "en",
        mixed: Boolean = false,
    ): SttChunk {
        if (unavailable) throw VoiceError.SttUnavailable()
        check(recognizing) { "STT is not recognizing" }
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
        listener?.onChunk(chunk)
        return chunk
    }

    fun emitMalformed(detail: String = "empty"): Nothing {
        throw VoiceError.MalformedSttResponse(detail)
    }

    fun lastFinal(): SttChunk? = chunks.lastOrNull { it.isFinal }
    fun lastPartial(): SttChunk? = chunks.lastOrNull { !it.isFinal }
}
