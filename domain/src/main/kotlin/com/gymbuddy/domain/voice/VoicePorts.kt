package com.gymbuddy.domain.voice

import com.gymbuddy.domain.AudioRoute
import java.security.MessageDigest

/**
 * Provider ports. Real adapters live in :app. Deterministic fakes for
 * kernel tests live beside these ports, not in production constructors.
 *
 * :domain never imports a vendor SDK. Vendors are unset (Q3–Q5).
 *
 * Natural conversation is unrestricted. These ports transport speech; they
 * do not constrain the user's vocabulary to commands. They do not parse
 * meaning, validate sets, or mutate workout state.
 */
/**
 * Monotonic milliseconds. `:app` supplies wall time later.
 * Production code must not depend on the test clock.
 */
interface Clock {
    fun now(): Long
}

interface VadListener {
    fun onObservation(observation: VadObservation)
}

interface VadProvider {
    fun start()
    fun stop()
    val started: Boolean
    /** Adapter calls [VadListener] when it has an observation. Kernel implements the listener. */
    fun setListener(listener: VadListener?)
}

/**
 * One VAD observation. Fakes inject these; Android will map DSP later.
 *
 * [echoFromTts] is brief and known — we are speaking. [echoFromMusic] is
 * louder, constant, and the harder problem. [usable] is what the adapter
 * will set after measurement; `null` means unknown. Unknown-with-music is
 * unusable. There is no SNR number in domain.
 */
data class VadObservation(
    val speech: Boolean,
    val talkerCount: Int = 1,
    val overlapping: Boolean = false,
    val echoFromTts: Boolean = false,
    val echoFromMusic: Boolean = false,
    val usable: Boolean? = null,
    val atMs: Long = 0,
)

/**
 * On-device wake-word port. No vendor, no audio. The adapter is blocked
 * on measurement. [ListenDecision] decides when this may run.
 */
interface WakeWordProvider {
    fun start()
    fun stop()
    val started: Boolean
}

/**
 * One wake-word observation. Detection is a boolean. No confidence gate —
 * that number does not exist until it is measured.
 */
data class WakeWordObservation(
    val detected: Boolean,
    val atMs: Long = 0,
)

data class SttChunk(
    val text: String,
    val isFinal: Boolean,
    val lang: String? = null,
    val confidence: Double? = null,
    val durationMs: Double? = null,
    val sttFinalHash: String? = null,
    val recordedAtMs: Long? = null,
    val mixed: Boolean = false,
)

interface SttListener {
    fun onChunk(chunk: SttChunk)
}

interface SttProvider {
    val supportsStreaming: Boolean
    val supportsLanguageId: Boolean
    /** Voice logging offline is allowed only if this is true. Not required in MVP. */
    val supportsOffline: Boolean
    /**
     * True from [startRecognition] until [abort].
     * A final does not end the session — one window is one recognition session.
     */
    val recognizing: Boolean
    fun startRecognition(languages: List<String> = emptyList())
    fun abort()
    /** Adapter calls [SttListener] when a chunk arrives. Kernel implements the listener. */
    fun setListener(listener: SttListener?)
}

interface TtsHandle {
    val id: String
    fun cancel()
    val cancelled: Boolean
    val speaking: Boolean
    val startedAtMs: Long
    val cancelledAtMs: Long?
}

interface TtsProvider {
    val speaking: Boolean
    fun speak(text: String, lang: String): TtsHandle
    fun stop()
}

data class LidResult(
    val lang: String,
    val mixed: Boolean,
    val confidence: Double,
)

interface LidProvider {
    fun detect(text: String): LidResult
}

data class DuckHandle(val id: String, val granted: Boolean, val mode: String)

interface AudioFocusController {
    fun requestDuck(reason: String, intensity: String = "duck"): DuckHandle
    fun restore(handle: DuckHandle)
    fun release()
    fun capabilities(): AudioFocusCapabilities
}

data class AudioFocusCapabilities(
    val duck: Boolean,
    val pause: Boolean,
    val unsupported: Boolean,
)

/**
 * Evidence the voice layer transports to later validation.
 * [CandidateValidator] owns auto-commit (headset, talkerCount, overlap, 250 ms).
 */
data class VoiceTurnEvidence(
    val transcript: String? = null,
    val isFinal: Boolean = false,
    val confidence: Double? = null,
    val durationMs: Double? = null,
    val sttFinalHash: String? = null,
    val language: String? = null,
    val mixed: Boolean = false,
    val talkerCount: Int = 1,
    val overlappingVad: Boolean = false,
    val audioRoute: AudioRoute? = null,
    val echoSuspected: Boolean = false,
    val recordedAtMs: Long? = null,
)

sealed class VoiceError(message: String) : Exception(message) {
    class SttUnavailable : VoiceError("STT unavailable")
    class TtsUnavailable : VoiceError("TTS unavailable")
    class AudioFocusUnavailable : VoiceError("AudioFocus unavailable")
    class VadUnavailable : VoiceError("VAD unavailable")
    class WakeWordUnavailable : VoiceError("wake word unavailable")
    class LidUnavailable : VoiceError("language identification unavailable")
    class InterruptionFailure : VoiceError("TTS interruption failed")
    class MalformedSttResponse(detail: String) : VoiceError("malformed STT response: $detail")
}

/** sha256 of normalized STT final (trim + lowercase + collapsed whitespace). */
object SttFinalHash {
    fun normalize(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ")

    fun of(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(normalize(text).toByteArray(Charsets.UTF_8))
            .joinToString("") { b -> "%02x".format(b.toInt() and 0xFF) }
    }
}
