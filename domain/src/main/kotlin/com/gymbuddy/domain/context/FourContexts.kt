package com.gymbuddy.domain.context

import com.gymbuddy.domain.EpistemicStance
import com.gymbuddy.domain.VoiceTurnState
import com.gymbuddy.domain.WeightUnit
import com.gymbuddy.domain.WorkoutSessionState
import kotlinx.serialization.Serializable

/** PR 0 FactRef. [EpistemicFact] is the fact model; this alias is the spec name. */
typealias FactRef = EpistemicFact

@Serializable
data class FourContexts(
    val workout: WorkoutStateContext,
    val historical: HistoricalStateContext,
    val conversational: ConversationalStateContext,
    val user: UserStateContext,
    /** Assembled FactRefs. Current vs historical remain distinct ids/provenance. */
    val facts: List<EpistemicFact> = emptyList(),
)

@Serializable
data class WorkoutStateContext(
    val sessionId: String,
    val workoutId: String,
    val workoutName: String,
    val sessionState: WorkoutSessionState,
    val voiceState: VoiceTurnState,
    val currentExerciseId: String? = null,
    val currentExerciseName: String? = null,
    val currentSetNumber: Int? = null,
    val completedSets: List<CompletedSetSnapshot> = emptyList(),
    val remainingSetsPlanned: Int,
    val targetReps: String? = null,
    val currentWeightKg: Double? = null,
    val restStartedAt: String? = null,
    val restTargetSec: Double? = null,
    val restRemainingSec: Double? = null,
    val substitutions: List<SubstitutionSnapshot> = emptyList(),
    val startedAt: String,
    /**
     * Last completed set of the *previous* exercise in this session, if any.
     * Used by PR 1 to apply the 120s cross-exercise correction rule.
     * Not a license to invent history.
     */
    val previousExerciseLastSet: CompletedSetSnapshot? = null,
    val currentExerciseStartedAt: String? = null,
)

@Serializable
data class CompletedSetSnapshot(
    val setId: String,
    val setNumber: Int,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val rpe: Double? = null,
    val rir: Int? = null,
    val occurredAt: String? = null,
    val exerciseId: String? = null,
)

@Serializable
data class SubstitutionSnapshot(
    val fromExerciseId: String,
    val toExerciseId: String,
)

@Serializable
data class EpistemicFact(
    val id: String,
    val kind: String,
    val stance: EpistemicStance,
    val value: kotlinx.serialization.json.JsonElement? = null,
    val sourceRowIds: List<String>,
    val speechAllowed: Boolean,
) {
    init {
        if (stance == EpistemicStance.NONE) {
            require(sourceRowIds.isEmpty() || !speechAllowed) {
                "stance=none cannot assert a number"
            }
        }
        if (stance == EpistemicStance.KNOWN) {
            require(sourceRowIds.isNotEmpty()) { "known facts require sourceRowIds" }
        }
        if (stance == EpistemicStance.ESTIMATED) {
            require(sourceRowIds.size >= 2) { "estimated facts require ≥2 source rows" }
        }
    }
}

@Serializable
data class HistoricalStateContext(
    val previousSessionsForExercise: List<PreviousSessionSnapshot> = emptyList(),
    val lastWorkoutSummary: LastWorkoutSummary? = null,
    val knownPrs: List<PrSnapshot> = emptyList(),
    val aggregates: List<EpistemicFact> = emptyList(),
    val recentNotesForExercise: List<NoteSnapshot> = emptyList(),
)

@Serializable
data class PreviousSessionSnapshot(
    val occurredOn: String,
    val sets: List<CompletedSetSnapshot>,
    val notes: List<String> = emptyList(),
    val stance: EpistemicStance = EpistemicStance.KNOWN,
)

@Serializable
data class LastWorkoutSummary(
    val date: String,
    val name: String,
    val volumeKg: Double,
    val stance: EpistemicStance = EpistemicStance.KNOWN,
)

@Serializable
data class PrSnapshot(
    val exerciseId: String,
    val kind: String,
    val value: Double,
    val date: String,
    val stance: EpistemicStance = EpistemicStance.KNOWN,
)

@Serializable
data class NoteSnapshot(
    val text: String,
    val createdAt: String,
    val stance: EpistemicStance = EpistemicStance.KNOWN,
)

@Serializable
data class ConversationalStateContext(
    val currentTopic: String? = null,
    val lastUserUtterance: UtteranceSnapshot? = null,
    val lastAiUtterance: UtteranceSnapshot? = null,
    val pendingClarification: PendingClarification? = null,
    val pendingCommand: PendingCommand? = null,
    val queuedSpeechActs: List<String> = emptyList(),
    val recentCorrections: List<CorrectionSnapshot> = emptyList(),
    val languageDetected: String,
    val recentSttFinals: List<SttFinalRecord> = emptyList(),
)

@Serializable
data class SttFinalRecord(
    val hash: String,
    val recordedAtEpochMs: Long,
)

@Serializable
data class UtteranceSnapshot(val text: String, val lang: String, val at: String)

@Serializable
data class PendingClarification(
    val question: String,
    val options: List<String> = emptyList(),
    val relatedEventHint: String? = null,
)

@Serializable
data class PendingCommand(
    val kind: String,
    val workoutExerciseId: String,
)

@Serializable
data class CorrectionSnapshot(
    val setId: String,
    val fields: List<String>,
    val at: String,
)

@Serializable
data class UserStateContext(
    val userId: String,
    val goals: List<String>,
    val experience: String,
    val equipment: List<String>,
    val units: WeightUnit,
    val primaryLanguage: String,
    val additionalLanguages: List<String> = emptyList(),
    val preferences: List<PreferenceSnapshot> = emptyList(),
    val limitations: List<String> = emptyList(),
)

@Serializable
data class PreferenceSnapshot(
    val key: String,
    val value: String,
    val userEditable: Boolean = true,
)
