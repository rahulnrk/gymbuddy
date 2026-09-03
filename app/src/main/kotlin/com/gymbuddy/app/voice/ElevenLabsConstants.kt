package com.gymbuddy.app.voice

/**
 * ElevenLabs TTS numbers and ids. Every const has a row in
 * DECISIONS_AND_THRESHOLDS.md.
 */
object ElevenLabsConstants {
    const val MODEL_ID = "eleven_flash_v2_5"
    const val OUTPUT_FORMAT = "pcm_24000"
    const val SAMPLE_RATE_HZ = 24_000
    const val VOICE_ID = "pNInz6obpgDQGcFmaJgB"
    const val WS_HOST = "wss://api.elevenlabs.io"
    const val RECONNECTS = 1
    /** Music ducks this many ms before playback starts. Gym listen: 1.8 s. */
    const val DUCK_LEAD_MS = 1_800L
    const val STT_MODEL_ID = "scribe_v2_realtime"
    const val STT_FORMAT = "pcm_16000"
    const val STT_SAMPLE_RATE_HZ = 16_000
    const val DEBUG_TOKEN_TTL_MS = 3_600_000L
    const val FIRST_SOUND_LINE = "Nice. That's one more than last time."
    const val BACKEND_TOKEN_PATH = "/v1/voice/token"
    const val REVIEWED = "2026-09-02"

    fun streamUrl(voiceId: String = VOICE_ID, language: String? = null): String {
        val lang = language?.takeIf { it.isNotBlank() && it != "und" }
            ?.let { "&language_code=$it" } ?: ""
        return "$WS_HOST/v1/text-to-speech/$voiceId/stream-input" +
            "?model_id=$MODEL_ID&output_format=$OUTPUT_FORMAT$lang"
    }

    fun sttUrl(): String =
        "$WS_HOST/v1/speech-to-text/realtime" +
            "?model_id=$STT_MODEL_ID" +
            "&audio_format=$STT_FORMAT" +
            "&commit_strategy=vad" +
            "&filter_background_audio=true" +
            "&keyterms=coach" +
            "&keyterms=hey%20coach"
}
