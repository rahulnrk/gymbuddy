package com.gymbuddy.app.voice

/**
 * Exact lines on the First Sound screen. Timings are reported, never judged.
 */
object SpeakStatus {
    const val CONNECTING = "Connecting…"
    const val CONNECTED = "Connected. Waiting for audio…"
    const val NO_API_KEY =
        "Failed: No API key. Add elevenlabs.api.key to local.properties and rebuild."
    const val KEY_REJECTED = "Failed: Key rejected by ElevenLabs."
    const val RATE_LIMITED = "Failed: Out of credits or rate limited."
    const val NO_AUDIO = "Failed: Connected but no audio arrived."

    fun firstAudio(elapsedMs: Long) = "First audio at $elapsedMs ms. Playing."
    fun finished(elapsedMs: Long) = "Finished after $elapsedMs ms."
    fun cancelled(elapsedMs: Long) = "Cancelled after $elapsedMs ms."
    fun dropped(elapsedMs: Long) = "Failed: Connection dropped after $elapsedMs ms."
    fun audioOutput(detail: String) = "Failed: Audio output failed: $detail."
}

class AudioOutputFailedException(val detail: String) : RuntimeException(detail)

enum class SpeakFailureKind {
    NO_API_KEY,
    KEY_REJECTED,
    RATE_LIMITED,
    DROPPED,
    NO_AUDIO,
    AUDIO_OUTPUT,
}

fun SpeakFailureKind.line(elapsedMs: Long, detail: String = ""): String = when (this) {
    SpeakFailureKind.NO_API_KEY -> SpeakStatus.NO_API_KEY
    SpeakFailureKind.KEY_REJECTED -> SpeakStatus.KEY_REJECTED
    SpeakFailureKind.RATE_LIMITED -> SpeakStatus.RATE_LIMITED
    SpeakFailureKind.DROPPED -> SpeakStatus.dropped(elapsedMs)
    SpeakFailureKind.NO_AUDIO -> SpeakStatus.NO_AUDIO
    SpeakFailureKind.AUDIO_OUTPUT -> SpeakStatus.audioOutput(detail)
}

data class SpeakTrace(
    val atMs: Long,
    val line: String,
)

class SpeakDiagnostics {
    val traces = mutableListOf<SpeakTrace>()
    var lastError: String? = null
        private set
    var keyPresent: Boolean = false
    var audioRoute: String = "unknown"
    var duckLeadMs: Long = ElevenLabsConstants.DUCK_LEAD_MS
    var duckHoldMs: Long = ElevenLabsConstants.DUCK_HOLD_MS
    var lastHeard: String = ""
    var hotword: String = "none"
    var vadSpeech: Boolean = false

    fun reset() {
        traces.clear()
        lastError = null
        lastHeard = ""
        hotword = "none"
        vadSpeech = false
    }

    fun record(elapsedMs: Long, line: String) {
        traces += SpeakTrace(elapsedMs, line)
        if (line.startsWith("Failed:")) lastError = line
    }

    fun clipboardText(): String = buildString {
        appendLine("model: ${ElevenLabsConstants.MODEL_ID}")
        appendLine("voice: ${ElevenLabsConstants.VOICE_ID}")
        appendLine("outputFormat: ${ElevenLabsConstants.OUTPUT_FORMAT}")
        appendLine("sampleRateHz: ${ElevenLabsConstants.SAMPLE_RATE_HZ}")
        appendLine("audioRoute: $audioRoute")
        appendLine("keyPresent: $keyPresent")
        appendLine("duckLeadMs: $duckLeadMs")
        appendLine("duckHoldMs: $duckHoldMs")
        appendLine("sttModel: ${ElevenLabsConstants.STT_MODEL_ID}")
        appendLine("lastHeard: ${lastHeard.ifBlank { "none" }}")
        appendLine("hotword: $hotword")
        appendLine("vadSpeech: $vadSpeech")
        appendLine("transitions:")
        traces.forEach { appendLine("  ${it.atMs} ms  ${it.line}") }
        appendLine("lastError: ${lastError ?: "none"}")
    }
}

fun interface SpeakObserver {
    fun onLine(line: String)
}
