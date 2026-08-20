package com.gymbuddy.domain.validation

import com.gymbuddy.domain.AudioRoute
import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.events.CandidateEvent
import com.gymbuddy.domain.events.CandidateEventEnvelope

/**
 * Pure validator. Does not write the database. Does not transition
 * [com.gymbuddy.domain.WorkoutSessionState].
 *
 * PR 1 implements this interface. PR 0 freezes the signature and
 * [ValidatedFact] so an implementing agent cannot invent a different gate.
 *
 * CandidateValidator may resolve references from FourContexts (setId, working
 * weight, current exercise, voice route) but must never transition
 * WorkoutSessionState.
 */
interface CandidateValidator {
    fun validate(
        candidate: CandidateEvent,
        snapshot: FourContexts,
        route: VoiceCommitRoute,
    ): ValidationDecision

    /**
     * One Gym Mode turn that may contain several candidates (catch-up).
     * May thread spoken weight across the turn. Must not touch WorkoutSessionState.
     */
    fun validateTurn(
        candidates: List<CandidateEvent>,
        snapshot: FourContexts,
        route: VoiceCommitRoute,
    ): ValidationDecision
}

data class VoiceCommitRoute(
    val audioRoute: AudioRoute,
    val talkerCount: Int,
    val sttDurationMs: Double,
    val overlappingVad: Boolean,
    val sttFinalHash: String? = null,
    val recordedAtEpochMs: Long = 0L,
)

/**
 * Schema gate only (PR 0). Session-invariant validation is PR 1.
 *
 * `"ten"` as reps fails here because [com.gymbuddy.domain.events.SetCompletedCandidate.reps] is Int.
 */
object CandidateSchemaGate {
    fun inspect(raw: String): SchemaGateResult {
        return try {
            val envelope = CandidateEventEnvelope.parse(raw)
            val decoded = envelope.candidates.map { it to runCatching { it.decodePayload() } }
            val failures = decoded.mapNotNull { (c, r) ->
                r.exceptionOrNull()?.let { c.type to it.message.orEmpty() }
            }
            if (failures.isEmpty()) SchemaGateResult.Valid(envelope)
            else SchemaGateResult.Invalid("payload_decode_failed: $failures")
        } catch (e: Exception) {
            SchemaGateResult.Invalid(e.message ?: "schema_invalid")
        }
    }
}

sealed class SchemaGateResult {
    data class Valid(val envelope: CandidateEventEnvelope) : SchemaGateResult()
    data class Invalid(val reason: String) : SchemaGateResult()
}
