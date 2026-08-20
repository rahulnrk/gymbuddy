package com.gymbuddy.domain.loop

import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
import com.gymbuddy.domain.events.CandidateEventEnvelope
import com.gymbuddy.domain.events.WorkoutEvent
import com.gymbuddy.domain.extract.ExtractionResult
import com.gymbuddy.domain.safety.SafetyResult
import com.gymbuddy.domain.safety.SafetySpeechDirective
import com.gymbuddy.domain.validation.ValidationDecision

sealed class TurnError {
    data class Voice(val message: String) : TurnError()
    data class Safety(val message: String) : TurnError()
    data class Extraction(val message: String) : TurnError()
    data class Validation(val message: String) : TurnError()
    data class Engine(val message: String) : TurnError()
    data class Persistence(val message: String) : TurnError()
    data class Context(val message: String) : TurnError()
    data class Recommendation(val message: String) : TurnError()
    data class Speech(val message: String) : TurnError()
}

data class TurnResult(
    val transcript: String,
    val safety: SafetyResult,
    val safetySpeech: SafetySpeechDirective,
    val extraction: ExtractionResult,
    val validation: ValidationDecision?,
    val committed: List<WorkoutEvent>,
    val snapshot: WorkoutSessionSnapshot,
    val contexts: FourContexts,
    val recommendation: Recommendation?,
    val speak: SpeakDecision,
    val spokenText: String?,
    val coachingPersisted: Boolean,
    val workoutPersisted: Boolean,
    val duplicateSuppressed: Boolean = false,
    val error: TurnError? = null,
    val queuedSpeechActs: List<SpeechActId> = emptyList(),
    /** Claims supporting [spokenText]. Empty when the line asserts nothing. */
    val claims: List<Claim> = emptyList(),
    /** Non-empty means a claim failed to recompute and the line was stripped. */
    val claimViolations: List<String> = emptyList(),
) {
    val candidates: CandidateEventEnvelope get() = extraction.envelope

    /** Fact ids actually cited by this turn's spoken line. */
    val citedFactIds: List<String> get() = claims.flatMap { c -> c.evidence.map { it.id } }.distinct()
}
