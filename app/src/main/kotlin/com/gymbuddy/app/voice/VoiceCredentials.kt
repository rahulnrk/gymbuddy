package com.gymbuddy.app.voice

enum class VoiceScope { STT, TTS }

data class VoiceToken(val value: String, val expiresAtMs: Long)

interface VoiceCredentials {
    /** Whether a key/token source is configured. Never the key itself. */
    val keyPresent: Boolean
    suspend fun issue(scope: VoiceScope): VoiceToken
}

fun redactToken(value: String): String =
    if (value.isBlank()) "(empty)" else "(present)"
