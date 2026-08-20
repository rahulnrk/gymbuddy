package com.gymbuddy.domain.voice

import com.gymbuddy.domain.AudioRoute
import com.gymbuddy.domain.VoiceTurnState
import com.gymbuddy.domain.voice.fake.FakeAudioFocus
import com.gymbuddy.domain.voice.fake.FakeLid
import com.gymbuddy.domain.voice.fake.FakeStt
import com.gymbuddy.domain.voice.fake.FakeTts
import com.gymbuddy.domain.voice.fake.FakeVad

/**
 * Voice turn-taking kernel. Orthogonal to WorkoutSessionState.
 *
 * Transports speech. Does not classify utterances, call an LLM, append
 * WorkoutEvents, or decide auto-commit.
 */
class VoiceTurnMachine(
    val clock: FakeClock,
    val vad: FakeVad,
    val stt: FakeStt,
    val tts: FakeTts,
    val lid: FakeLid,
    val focus: FakeAudioFocus,
    var audioRoute: AudioRoute = AudioRoute.HEADSET,
) {
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
    private var ttsHandle: TtsHandle? = null

    fun start() {
        vad.start()
        stt.startRecognition()
        go(VoiceTurnState.LISTENING)
    }

    fun stop() {
        tts.stop()
        stt.abort()
        vad.stop()
        restoreAndRelease()
        go(VoiceTurnState.STOPPED)
    }

    fun onSpeechStart(echoFromTts: Boolean = false, overlapping: Boolean = false, talkerCount: Int = 1) {
        val obs = vad.inject(
            VadObservation(
                speech = true,
                talkerCount = talkerCount,
                overlapping = overlapping,
                echoFromTts = echoFromTts,
                atMs = clock.now(),
            ),
        )
        lastEvidence = lastEvidence.copy(
            talkerCount = obs.talkerCount,
            overlappingVad = obs.overlapping,
            echoSuspected = obs.echoFromTts,
            audioRoute = audioRoute,
        )
        if (obs.echoFromTts && state == VoiceTurnState.AI_SPEAKING) {
            return
        }
        if (state == VoiceTurnState.AI_SPEAKING) {
            bargeIn()
            return
        }
        duck("user_speech")
        go(VoiceTurnState.USER_SPEAKING)
        if (!stt.recognizing) stt.startRecognition()
    }

    fun onSpeechEnd() {
        vad.inject(
            VadObservation(
                speech = false,
                talkerCount = lastEvidence.talkerCount,
                overlapping = lastEvidence.overlappingVad,
                atMs = clock.now(),
            ),
        )
        if (state == VoiceTurnState.USER_SPEAKING) {
            go(VoiceTurnState.PROCESSING)
        }
    }

    fun onPartial(text: String, lang: String? = null): SttChunk {
        val chunk = stt.emitPartial(text, lang)
        lastEvidence = lastEvidence.copy(
            transcript = chunk.text,
            isFinal = false,
            confidence = chunk.confidence,
            language = chunk.lang,
            recordedAtMs = chunk.recordedAtMs,
            audioRoute = audioRoute,
        )
        return chunk
    }

    fun onFinal(
        text: String,
        confidence: Double? = 0.9,
        durationMs: Double? = 800.0,
        lang: String? = null,
        mixed: Boolean? = null,
    ): SttChunk {
        val lid = runCatching { this.lid.detect(text) }.getOrElse { e ->
            if (e is VoiceError.LidUnavailable) {
                lastError = e
                throw e
            }
            throw e
        }
        val chunk = stt.emitFinal(
            text = text,
            confidence = confidence,
            durationMs = durationMs,
            lang = lang ?: lid.lang,
            mixed = mixed ?: lid.mixed,
        )
        lastEvidence = VoiceTurnEvidence(
            transcript = chunk.text,
            isFinal = true,
            confidence = chunk.confidence,
            durationMs = chunk.durationMs,
            sttFinalHash = chunk.sttFinalHash,
            language = chunk.lang,
            mixed = chunk.mixed,
            talkerCount = lastEvidence.talkerCount,
            overlappingVad = lastEvidence.overlappingVad,
            audioRoute = audioRoute,
            echoSuspected = lastEvidence.echoSuspected,
            recordedAtMs = chunk.recordedAtMs,
        )
        if (state == VoiceTurnState.USER_SPEAKING) go(VoiceTurnState.PROCESSING)
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
        (ttsHandle as? com.gymbuddy.domain.voice.fake.FakeTtsHandle)?.complete()
        restoreDuck()
        go(if (vad.started) VoiceTurnState.LISTENING else VoiceTurnState.SILENT)
    }

    /**
     * Speech onset while AI is speaking. Cancels TTS on the fake clock.
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
        if (!stt.recognizing) stt.startRecognition()
    }

    fun acceptUserTurn() {
        if (state == VoiceTurnState.INTERRUPTED) go(VoiceTurnState.USER_SPEAKING)
    }

    private fun duck(reason: String) {
        restoreDuck()
        duckHandle = focus.requestDuck(reason)
    }

    private fun restoreDuck() {
        duckHandle?.let { focus.restore(it) }
        duckHandle = null
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
