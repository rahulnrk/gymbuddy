package com.gymbuddy.app.voice

import com.gymbuddy.app.BuildConfig
import com.gymbuddy.domain.voice.Clock

object VoiceCredentialsFactory {
    fun create(clock: Clock): VoiceCredentials =
        BackendVoiceCredentials(BuildConfig.VOICE_BACKEND_URL, clock)
}
