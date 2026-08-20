package com.gymbuddy.domain.events

import com.gymbuddy.domain.CandidateId
import com.gymbuddy.domain.CandidateType
import com.gymbuddy.domain.CommandKind
import com.gymbuddy.domain.FeedbackKind
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.QuestionTopic
import com.gymbuddy.domain.SafetySeverity
import com.gymbuddy.domain.WeightUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Frozen CandidateEvent envelope. schemaVersion = 1.
 *
 * The LLM may only produce this JSON. It cannot produce WorkoutEvent rows
 * and cannot write to the database.
 */
@Serializable
data class CandidateEventEnvelope(
    val schemaVersion: Int,
    val candidates: List<CandidateEvent>,
) {
    init {
        require(schemaVersion == SCHEMA_VERSION) {
            "CandidateEvent schemaVersion must be $SCHEMA_VERSION"
        }
    }

    companion object {
        const val SCHEMA_VERSION = 1
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        val json: Json = Json {
            ignoreUnknownKeys = false
            explicitNulls = false
        }

        fun parse(raw: String): CandidateEventEnvelope = json.decodeFromString(raw)
    }
}

@Serializable
data class CandidateEvent(
    val candidateId: String,
    val type: CandidateType,
    val overallConfidence: Double,
    val fieldConfidence: Map<String, Double> = emptyMap(),
    val exerciseNameRaw: String? = null,
    val setNumberHint: kotlinx.serialization.json.JsonElement? = null,
    val speechSpan: SpeechSpan? = null,
    val loggingMode: LoggingMode? = null,
    val payload: JsonObject,
) {
    init {
        require(overallConfidence in 0.0..1.0)
        fieldConfidence.values.forEach { require(it in 0.0..1.0) }
    }

    fun id(): CandidateId = CandidateId(candidateId)

    /** Typed payload. Throws if JSON does not match the frozen per-type schema. */
    fun decodePayload(): CandidatePayload = when (type) {
        CandidateType.SET_COMPLETED ->
            CandidateEventEnvelope.json.decodeFromJsonElement<SetCompletedCandidate>(payload)
        CandidateType.SET_CORRECTED ->
            CandidateEventEnvelope.json.decodeFromJsonElement<SetCorrectedCandidate>(payload)
        CandidateType.SET_DELETED ->
            CandidateEventEnvelope.json.decodeFromJsonElement<SetDeletedCandidate>(payload)
        CandidateType.WEIGHT_CHANGED ->
            CandidateEventEnvelope.json.decodeFromJsonElement<WeightChangedCandidate>(payload)
        CandidateType.REPS_CHANGED ->
            CandidateEventEnvelope.json.decodeFromJsonElement<RepsChangedCandidate>(payload)
        CandidateType.EXERCISE_CHANGED ->
            CandidateEventEnvelope.json.decodeFromJsonElement<ExerciseChangedCandidate>(payload)
        CandidateType.SUBSTITUTION_PROPOSE ->
            CandidateEventEnvelope.json.decodeFromJsonElement<SubstitutionProposeCandidate>(payload)
        CandidateType.USER_NOTE ->
            CandidateEventEnvelope.json.decodeFromJsonElement<UserNoteCandidate>(payload)
        CandidateType.USER_FEEDBACK ->
            CandidateEventEnvelope.json.decodeFromJsonElement<UserFeedbackCandidate>(payload)
        CandidateType.USER_QUESTION ->
            CandidateEventEnvelope.json.decodeFromJsonElement<UserQuestionCandidate>(payload)
        CandidateType.COMMAND ->
            CandidateEventEnvelope.json.decodeFromJsonElement<CommandCandidate>(payload)
        CandidateType.SAFETY ->
            CandidateEventEnvelope.json.decodeFromJsonElement<SafetyCandidate>(payload)
    }
}

@Serializable
data class SpeechSpan(val startMs: Double, val endMs: Double)

sealed interface CandidatePayload

@Serializable
data class SetCompletedCandidate(
    val reps: Int? = null,
    val weight: Double? = null,
    val weightUnit: WeightUnit? = null,
    val rpe: Double? = null,
    val rir: Int? = null,
    val effortNote: String? = null,
) : CandidatePayload

@Serializable
data class SetCorrectedCandidate(
    val reps: Int? = null,
    val weight: Double? = null,
    val weightUnit: WeightUnit? = null,
    val target: String? = null,
) : CandidatePayload

@Serializable
data class SetDeletedCandidate(
    val reason: String? = null,
) : CandidatePayload

@Serializable
data class WeightChangedCandidate(
    val weight: Double,
    val weightUnit: WeightUnit? = null,
) : CandidatePayload

@Serializable
data class RepsChangedCandidate(
    val reps: Int? = null,
    val targetReps: String? = null,
) : CandidatePayload

@Serializable
data class ExerciseChangedCandidate(
    val fromExerciseId: String? = null,
    val toExerciseId: String? = null,
    val reason: String? = null,
) : CandidatePayload

@Serializable
data class SubstitutionProposeCandidate(
    val fromExerciseId: String? = null,
    val toExerciseId: String? = null,
    val toExerciseNameRaw: String? = null,
) : CandidatePayload

@Serializable
data class UserNoteCandidate(
    val textOriginal: String,
    val scope: String? = null,
) : CandidatePayload

@Serializable
data class UserFeedbackCandidate(
    val kind: FeedbackKind,
    val intensity: Double? = null,
) : CandidatePayload

@Serializable
data class UserQuestionCandidate(
    val questionOriginal: String,
    val topic: QuestionTopic? = null,
) : CandidatePayload

@Serializable
data class CommandCandidate(
    val kind: CommandKind,
) : CandidatePayload

@Serializable
data class SafetyCandidate(
    val severity: SafetySeverity? = null,
    val category: String? = null,
) : CandidatePayload
