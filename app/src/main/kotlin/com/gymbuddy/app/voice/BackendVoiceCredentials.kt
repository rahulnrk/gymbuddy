package com.gymbuddy.app.voice

import com.gymbuddy.app.voice.ElevenLabsConstants.BACKEND_TOKEN_PATH
import com.gymbuddy.domain.voice.Clock
import com.gymbuddy.domain.voice.VoiceError

/**
 * Production path. The gymbuddy backend holds the ElevenLabs key and
 * returns a short-lived token. The backend does not exist yet.
 */
class BackendVoiceCredentials(
    @Suppress("unused") private val baseUrl: String,
    @Suppress("unused") private val clock: Clock,
) : VoiceCredentials {
    override val keyPresent: Boolean = false

    override suspend fun issue(scope: VoiceScope): VoiceToken {
        // TODO(backend): POST $baseUrl$BACKEND_TOKEN_PATH for $scope;
        // return VoiceToken(value, expiresAtMs). Never log the token.
        throw VoiceError.TtsUnavailable()
    }
}
