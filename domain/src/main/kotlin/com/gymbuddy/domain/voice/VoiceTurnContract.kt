package com.gymbuddy.domain.voice

import com.gymbuddy.domain.VoiceTurnState
import com.gymbuddy.domain.VoiceTurnState.AI_SPEAKING
import com.gymbuddy.domain.VoiceTurnState.INTERRUPTED
import com.gymbuddy.domain.VoiceTurnState.LISTENING
import com.gymbuddy.domain.VoiceTurnState.PROCESSING
import com.gymbuddy.domain.VoiceTurnState.SILENT
import com.gymbuddy.domain.VoiceTurnState.STOPPED
import com.gymbuddy.domain.VoiceTurnState.USER_SPEAKING

/**
 * Legal VoiceTurnState transitions. Orthogonal to WorkoutSessionState.
 *
 * SILENT is idle. LISTENING is VAD-armed. INTERRUPTED is barge-in in flight.
 */
object VoiceTurnContract {
    val legal: Set<Pair<VoiceTurnState, VoiceTurnState>> = setOf(
        SILENT to LISTENING,
        SILENT to USER_SPEAKING,
        SILENT to AI_SPEAKING,
        SILENT to STOPPED,
        LISTENING to USER_SPEAKING,
        LISTENING to AI_SPEAKING,
        LISTENING to SILENT,
        LISTENING to STOPPED,
        USER_SPEAKING to PROCESSING,
        USER_SPEAKING to STOPPED,
        PROCESSING to SILENT,
        PROCESSING to LISTENING,
        PROCESSING to AI_SPEAKING,
        PROCESSING to USER_SPEAKING,
        PROCESSING to STOPPED,
        AI_SPEAKING to SILENT,
        AI_SPEAKING to LISTENING,
        AI_SPEAKING to INTERRUPTED,
        AI_SPEAKING to USER_SPEAKING,
        AI_SPEAKING to STOPPED,
        INTERRUPTED to USER_SPEAKING,
        INTERRUPTED to LISTENING,
        INTERRUPTED to STOPPED,
        STOPPED to LISTENING,
        STOPPED to SILENT,
    )

    fun isLegal(from: VoiceTurnState, to: VoiceTurnState): Boolean =
        from == to || (from to to) in legal
}
