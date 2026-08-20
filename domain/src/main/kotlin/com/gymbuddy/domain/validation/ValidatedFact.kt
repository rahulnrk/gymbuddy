package com.gymbuddy.domain.validation

import com.gymbuddy.domain.CommandKind
import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.FeedbackKind
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.QuestionTopic
import com.gymbuddy.domain.SetId
import com.gymbuddy.domain.WeightUnit

/**
 * PR 1 output. Facts the validator may accept. Not committed WorkoutEvents.
 *
 * This sealed type is the boundary that stops PR 1 from becoming a mini
 * state machine: there is no branch for SET_STARTED, REST_*, SESSION_*,
 * WORKOUT_STARTED / RESUMED, or an official setNumber to assign.
 *
 * Official set numbers, rest, and WorkoutSessionState are PR 2.
 */
sealed class ValidatedFact {

    /**
     * A performed-set *fact*. Engine (PR 2) assigns setId and setNumber
     * when appending SET_COMPLETED. [setNumberHint] is speech only.
     */
    data class SetCompleted(
        val reps: Int?,
        val weightKg: Double?,
        val inheritWeight: Boolean,
        val loggingMode: LoggingMode,
        val setNumberHint: SetNumberHint? = null,
        val batchId: String? = null,
        val rpe: Double? = null,
        val rir: Int? = null,
        val effortNote: String? = null,
        val exerciseIdFromContext: String? = null,
    ) : ValidatedFact() {
        init {
            require(reps == null || reps >= 0)
            require(weightKg == null || weightKg > 0.0)
        }
    }

    /**
     * Correction of an **existing** set. [setId] is resolved from FourContexts.
     * Cannot represent a new physical set.
     */
    data class SetCorrected(
        val setId: SetId,
        val fields: CorrectionFields,
        val previous: PreviousSetSnapshot,
    ) : ValidatedFact() {
        init {
            require(setId.value.isNotBlank())
            require(previous.setId == setId) {
                "previous snapshot must be the same physical set"
            }
        }
    }

    /** Soft-delete of an existing set. Not a replacement SET_COMPLETED. */
    data class SetDeleted(
        val setId: SetId,
        val previous: PreviousSetSnapshot,
        val reason: String? = null,
    ) : ValidatedFact() {
        init {
            require(previous.setId == setId)
        }
    }

    data class WeightChanged(
        val weightKg: Double,
        val weightUnit: WeightUnit? = null,
    ) : ValidatedFact() {
        init {
            require(weightKg > 0.0)
        }
    }

    data class UserQuestion(
        val questionOriginal: String,
        val topic: QuestionTopic? = null,
    ) : ValidatedFact()

    data class UserFeedback(
        val kind: FeedbackKind,
        val intensity: Double? = null,
    ) : ValidatedFact()

    data class UserNote(
        val textOriginal: String,
        val scope: String? = null,
    ) : ValidatedFact()

    /** Classification only. PR 2 commits EXERCISE_SKIPPED / PLANNED_SETS_CHANGED. */
    data class Command(
        val kind: CommandKind,
    ) : ValidatedFact()

    data class SubstitutionPropose(
        val toExerciseNameRaw: String? = null,
        val toExerciseId: String? = null,
        val fromExerciseId: String? = null,
    ) : ValidatedFact()
}

data class CorrectionFields(
    val reps: Int? = null,
    val weightKg: Double? = null,
    val weightUnit: WeightUnit? = null,
)

/**
 * Audit snapshot copied from FourContexts. [alreadyAssignedSetNumber] is
 * what the engine already stored, not a number the validator assigns.
 */
data class PreviousSetSnapshot(
    val setId: SetId,
    val reps: Int?,
    val weightKg: Double?,
    val alreadyAssignedSetNumber: Int? = null,
)

sealed class SetNumberHint {
    data class Index(val value: Int) : SetNumberHint() {
        init {
            require(value >= 1)
        }
    }

    data class Ordinal(val value: HintOrdinal) : SetNumberHint()
}

enum class HintOrdinal { FIRST, SECOND, THIRD, LAST, THIS }

sealed class ValidationDecision {
    data class Accept(
        val facts: List<ValidatedFact>,
        val source: EventSource,
    ) : ValidationDecision()

    data class Clarify(
        val prompt: String,
        val options: List<String> = emptyList(),
    ) : ValidationDecision()

    data class Reject(val reason: String) : ValidationDecision()

    data class Drop(val reason: String) : ValidationDecision()
}
