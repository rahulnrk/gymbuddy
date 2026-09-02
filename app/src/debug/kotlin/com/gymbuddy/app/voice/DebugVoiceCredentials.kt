package com.gymbuddy.app.voice

import com.gymbuddy.domain.voice.Clock
import com.gymbuddy.domain.voice.VoiceError

/**
 * Debug variant only. The key is injected from local.properties at build
 * time and must never appear on the release classpath.
 */
class DebugVoiceCredentials(
    private val apiKey: String,
    private val clock: Clock,
) : VoiceCredentials {
    override val keyPresent: Boolean = apiKey.isNotBlank()

    override suspend fun issue(scope: VoiceScope): VoiceToken {
        if (!keyPresent) throw VoiceError.TtsUnavailable()
        return VoiceToken(value = apiKey, expiresAtMs = clock.now() + ElevenLabsConstants.DEBUG_TOKEN_TTL_MS)
    }
}
