package com.gymbuddy.domain.context

import com.gymbuddy.domain.VoiceTurnState
import com.gymbuddy.domain.WeightUnit
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
import com.gymbuddy.domain.events.WorkoutEvent

/**
 * Structured inputs for [FourContextAssembler].
 *
 * History, profile, and conversation must already be stored records.
 * The assembler does not parse speech or query Room.
 */
data class AssemblyRequest(
    val snapshot: WorkoutSessionSnapshot,
    val sessionEvents: List<WorkoutEvent> = emptyList(),
    val history: HistoricalStore = HistoricalStore(),
    val profile: UserProfileStore = UserProfileStore(),
    val conversation: ConversationalStore = ConversationalStore(),
    val exerciseNames: Map<String, String> = emptyMap(),
    val workoutName: String = "",
    val voiceState: VoiceTurnState = VoiceTurnState.SILENT,
    val targetReps: String? = null,
)

/**
 * Previously committed workout rows for the current exercise (or substitution family).
 * Absence must be passed through as empty — never filled from current session weight.
 */
data class HistoricalStore(
    val previousSessionsForExercise: List<PreviousSessionSnapshot> = emptyList(),
    val lastWorkoutSummary: LastWorkoutSummary? = null,
    val knownPrs: List<PrSnapshot> = emptyList(),
    val recentNotesForExercise: List<NoteSnapshot> = emptyList(),
    val uncertainFacts: List<EpistemicFact> = emptyList(),
)

/** Structured user row. Empty fields stay empty; nothing is psychologically inferred. */
data class UserProfileStore(
    val userId: String = "",
    val goals: List<String> = emptyList(),
    val experience: String = "",
    val equipment: List<String> = emptyList(),
    val units: WeightUnit = WeightUnit.KG,
    val primaryLanguage: String = "",
    val additionalLanguages: List<String> = emptyList(),
    val preferences: List<PreferenceSnapshot> = emptyList(),
    val limitations: List<String> = emptyList(),
)

/**
 * Structured conversational records already available.
 * Not a transcript dump. Not an LLM summary.
 */
data class ConversationalStore(
    val currentTopic: String? = null,
    val lastUserUtterance: UtteranceSnapshot? = null,
    val lastAiUtterance: UtteranceSnapshot? = null,
    val pendingClarification: PendingClarification? = null,
    val pendingCommand: PendingCommand? = null,
    val queuedSpeechActs: List<String> = emptyList(),
    val languageDetected: String? = null,
    val recentSttFinals: List<SttFinalRecord> = emptyList(),
    val recentCorrections: List<CorrectionSnapshot> = emptyList(),
)
