package com.gymbuddy.domain.extract

import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.events.CandidateEventEnvelope
import com.gymbuddy.domain.safety.SafetyResult
import com.gymbuddy.domain.safety.SafetySpeechDirective
import com.gymbuddy.domain.voice.VoiceTurnEvidence

/**
 * Product §9 multi-label intents. A turn may have several at once.
 * Not a user-facing command vocabulary.
 */
enum class IntentLabel {
    LOGGING,
    QUESTION,
    COMMAND,
    CORRECTION,
    CASUAL,
    COACHING_DISCUSSION,
    SAFETY,
    FEEDBACK,
}

data class ExtractionRequest(
    val transcript: String,
    val evidence: VoiceTurnEvidence = VoiceTurnEvidence(),
    val contexts: FourContexts? = null,
    val language: String? = null,
)

/**
 * Internal structured interpretation. Does not authorize workout mutation.
 * [envelope] is never a [com.gymbuddy.domain.events.WorkoutEvent].
 */
data class ExtractionResult(
    val safety: SafetyResult,
    val speech: SafetySpeechDirective,
    val intents: Set<IntentLabel>,
    val envelope: CandidateEventEnvelope,
    val llmRequired: Boolean = false,
    val classifiedBeforeExtract: Boolean = true,
) {
    val hasMutationCandidate: Boolean
        get() = envelope.candidates.any {
            it.type.name !in setOf("USER_QUESTION", "USER_FEEDBACK", "USER_NOTE", "SAFETY")
        }
}
