package com.gymbuddy.domain.engine

import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.SetId
import com.gymbuddy.domain.WorkoutSessionState

data class PlannedExercise(
    val workoutExerciseId: String,
    val exerciseId: String,
    val plannedSets: Int,
    val restTargetSec: Int = 90,
    val skipped: Boolean = false,
    val skippedReason: String? = null,
    val deferralCount: Int = 0,
    val deferralReason: String? = null,
    val substitutedFromExerciseId: String? = null,
)

enum class ExerciseOutcome {
    COMPLETED,
    SKIPPED,
    DEFERRED,
    PENDING,
}

fun exerciseOutcome(snapshot: WorkoutSessionSnapshot, workoutExerciseId: String): ExerciseOutcome {
    val ex = snapshot.plan?.exercises?.firstOrNull { it.workoutExerciseId == workoutExerciseId }
        ?: return ExerciseOutcome.PENDING
    val done = snapshot.completedSets.any { it.workoutExerciseId == workoutExerciseId && !it.deleted }
    if (done && !ex.skipped) return ExerciseOutcome.COMPLETED
    if (ex.skipped) return ExerciseOutcome.SKIPPED
    if (snapshot.phase == WorkoutSessionState.WORKOUT_COMPLETE && ex.deferralCount > 0) {
        return ExerciseOutcome.SKIPPED
    }
    if (ex.deferralCount > 0) return ExerciseOutcome.DEFERRED
    return ExerciseOutcome.PENDING
}

data class WorkoutPlan(
    val workoutId: String,
    val exercises: List<PlannedExercise>,
)

data class ProjectedSet(
    val setId: SetId,
    val workoutExerciseId: String,
    val exerciseId: String,
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?,
    val loggingMode: LoggingMode,
    val deleted: Boolean = false,
    val lastCorrectionEventId: String? = null,
    val source: EventSource = EventSource.VOICE,
    val batchId: String? = null,
)

data class RestState(
    val afterSetId: SetId,
    val targetSec: Int,
    val reasonPending: String? = null,
)

/**
 * Projected workout truth. Not conversation. Replay = fold(events).
 */
data class WorkoutSessionSnapshot(
    val phase: WorkoutSessionState = WorkoutSessionState.IDLE,
    val userId: String? = null,
    val sessionId: String? = null,
    val workoutId: String? = null,
    val plan: WorkoutPlan? = null,
    val currentExerciseIndex: Int = 0,
    val completedSets: List<ProjectedSet> = emptyList(),
    val rest: RestState? = null,
    val prePauseState: WorkoutSessionState? = null,
    val restRemainingSec: Double? = null,
    val lastEventId: String? = null,
    val localEpoch: Int = 0,
    val appliedEventIds: Set<String> = emptySet(),
    val workingWeightKg: Double? = null,
) {
    val currentExercise: PlannedExercise?
        get() = plan?.exercises?.getOrNull(currentExerciseIndex)

    fun liveSetsOnCurrent(): List<ProjectedSet> {
        val we = currentExercise?.workoutExerciseId ?: return emptyList()
        return completedSets.filter { it.workoutExerciseId == we && !it.deleted }
    }

    fun remainingPlannedOnCurrent(): Int {
        val planned = currentExercise?.plannedSets ?: 0
        return (planned - liveSetsOnCurrent().size).coerceAtLeast(0)
    }

    fun nextOfficialSetNumber(): Int =
        (liveSetsOnCurrent().maxOfOrNull { it.setNumber } ?: 0) + 1

    fun moreExercisesAfterCurrent(): Boolean {
        val plan = plan ?: return false
        return plan.exercises.drop(currentExerciseIndex + 1).any { !it.skipped }
    }
}
