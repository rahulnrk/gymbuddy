package com.gymbuddy.domain.voice

import com.gymbuddy.domain.AudioRoute
import com.gymbuddy.domain.VoiceTurnState

/**
 * Voice turn-taking kernel. Orthogonal to WorkoutSessionState.
 *
 * Transports speech. Does not classify utterances, call an LLM, append
 * WorkoutEvents, or decide auto-commit. The listen gate owns the STT
 * session; this machine transports what the gate opened.
 *
 * Inbound audio events arrive through [VadListener] / [SttListener].
 * Tests may also call [onSpeechStart] / [onPartial] / [onFinal] directly.
 */
class VoiceTurnMachine(
    val clock: Clock,
    val vad: VadProvider,
    val stt: SttProvider,
    val tts: TtsProvider,
    val lid: LidProvider,
    val focus: AudioFocusController,
    var audioRoute: AudioRoute = AudioRoute.HEADSET,
) : VadListener, SttListener {
    var state: VoiceTurnState = VoiceTurnState.SILENT
        private set
    val trail: MutableList<VoiceTurnState> = mutableListOf(VoiceTurnState.SILENT)
    var lastBargeInLatencyMs: Long? = null
        private set
    var lastEvidence: VoiceTurnEvidence = VoiceTurnEvidence()
        private set
    var lastError: VoiceError? = null
        private set
    private var duckHandle: DuckHandle? = null
    private var duckReason: String? = null
    private var ttsHandle: TtsHandle? = null

    init {
        vad.setListener(this)
        stt.setListener(this)
    }

    fun start() {
        vad.start()
        go(VoiceTurnState.LISTENING)
    }

    fun stop() {
        tts.stop()
        if (stt.recognizing) stt.abort()
        vad.stop()
        restoreAndRelease()
        go(VoiceTurnState.STOPPED)
    }

    override fun onObservation(observation: VadObservation) {
        if (observation.speech) {
            onSpeechStart(
                echoFromTts = observation.echoFromTts,
                overlapping = observation.overlapping,
                talkerCount = observation.talkerCount,
                echoFromMusic = observation.echoFromMusic,
                usable = observation.usable,
            )
        } else {
            onSpeechEnd()
        }
    }

    fun onSpeechStart(
        echoFromTts: Boolean = false,
        overlapping: Boolean = false,
        talkerCount: Int = 1,
        echoFromMusic: Boolean = false,
        usable: Boolean? = null,
    ) {
        lastEvidence = lastEvidence.copy(
            talkerCount = talkerCount,
            overlappingVad = overlapping,
            echoSuspected = echoFromTts,
            audioRoute = audioRoute,
        )
        if ((echoFromTts || echoFromMusic) && state == VoiceTurnState.AI_SPEAKING) {
            return
        }
        if (state == VoiceTurnState.AI_SPEAKING) {
            bargeIn()
            return
        }
        duck("user_speech")
        go(VoiceTurnState.USER_SPEAKING)
    }

    fun onSpeechEnd() {
        if (state == VoiceTurnState.USER_SPEAKING) {
            go(VoiceTurnState.PROCESSING)
        }
    }

    override fun onChunk(chunk: SttChunk) {
        lastEvidence = lastEvidence.copy(
            transcript = chunk.text,
            isFinal = chunk.isFinal,
            confidence = chunk.confidence,
            durationMs = chunk.durationMs,
            sttFinalHash = chunk.sttFinalHash,
            language = chunk.lang,
            mixed = chunk.mixed,
            recordedAtMs = chunk.recordedAtMs,
            audioRoute = audioRoute,
        )
        if (chunk.isFinal && state == VoiceTurnState.USER_SPEAKING) {
            go(VoiceTurnState.PROCESSING)
        }
    }

    fun onPartial(text: String, lang: String? = null): SttChunk {
        val chunk = SttChunk(
            text = text,
            isFinal = false,
            lang = lang,
            recordedAtMs = clock.now(),
        )
        onChunk(chunk)
        return chunk
    }

    fun onFinal(
        text: String,
        confidence: Double? = 0.9,
        durationMs: Double? = 800.0,
        lang: String? = null,
        mixed: Boolean? = null,
    ): SttChunk {
        val detected = runCatching { this.lid.detect(text) }.getOrElse { e ->
            if (e is VoiceError.LidUnavailable) {
                lastError = e
                throw e
            }
            throw e
        }
        val chunk = SttChunk(
            text = text,
            isFinal = true,
            lang = lang ?: detected.lang,
            confidence = confidence,
            durationMs = durationMs,
            sttFinalHash = SttFinalHash.of(text),
            recordedAtMs = clock.now(),
            mixed = mixed ?: detected.mixed,
        )
        onChunk(chunk)
        return chunk
    }

    fun speak(text: String, lang: String = "en"): TtsHandle {
        duck("ai_speech")
        val handle = tts.speak(text, lang)
        ttsHandle = handle
        go(VoiceTurnState.AI_SPEAKING)
        return handle
    }

    fun onTtsEnded() {
        restoreDuck()
        go(if (vad.started) VoiceTurnState.LISTENING else VoiceTurnState.SILENT)
    }

    /**
     * Speech onset while AI is speaking. Cancels TTS on the clock.
     * Latency is cancel time minus onset, never wall-clock.
     */
    fun bargeIn() {
        if (state != VoiceTurnState.AI_SPEAKING) {
            if (state == VoiceTurnState.LISTENING || state == VoiceTurnState.SILENT) {
                duck("user_speech")
                go(VoiceTurnState.USER_SPEAKING)
            }
            return
        }
        val onset = clock.now()
        go(VoiceTurnState.INTERRUPTED)
        ttsHandle?.cancel()
        lastBargeInLatencyMs = clock.now() - onset
        restoreDuck()
        duck("user_speech")
        go(VoiceTurnState.USER_SPEAKING)
    }

    fun acceptUserTurn() {
        if (state == VoiceTurnState.INTERRUPTED) go(VoiceTurnState.USER_SPEAKING)
    }

    private fun duck(reason: String) {
        if (duckHandle != null && duckReason == reason) return
        restoreDuck()
        duckHandle = focus.requestDuck(reason)
        duckReason = reason
    }

    private fun restoreDuck() {
        duckHandle?.let { focus.restore(it) }
        duckHandle = null
        duckReason = null
    }

    private fun restoreAndRelease() {
        restoreDuck()
        focus.release()
    }

    private fun go(next: VoiceTurnState) {
        require(VoiceTurnContract.isLegal(state, next)) {
            "illegal voice transition $state → $next"
        }
        state = next
        trail += next
    }
}
