package com.gymbuddy.domain.voice

import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot

/**
 * Where the user *should* be. A prediction, not a fact.
 * Spoken facts arrive only when they talk in a window.
 */
enum class PredictedPhase {
    LIFTING,
    RESTING,
    BETWEEN_EXERCISES,
    IDLE,
}

data class SessionPrediction(
    val exerciseId: String?,
    val predictedSetNumber: Int?,
    val phase: PredictedPhase,
    val phaseElapsedMs: Long,
    val predictedRestSec: Int?,
    val predictedPhaseEndElapsedMs: Long?,
)

fun predictSession(
    snapshot: WorkoutSessionSnapshot,
    nowElapsedMs: Long,
    phaseStartedAtElapsedMs: Long,
    predictedRestSec: Int?,
): SessionPrediction {
    val phase = predictedPhase(snapshot.phase)
    val elapsed = (nowElapsedMs - phaseStartedAtElapsedMs).coerceAtLeast(0L)
    val restSec = predictedRestSec ?: snapshot.currentExercise?.restTargetSec
    val end = when (phase) {
        PredictedPhase.RESTING -> restSec?.let { phaseStartedAtElapsedMs + it * 1_000L }
        PredictedPhase.LIFTING, PredictedPhase.BETWEEN_EXERCISES, PredictedPhase.IDLE -> null
    }
    val setNumber = when (phase) {
        PredictedPhase.LIFTING -> snapshot.nextOfficialSetNumber()
        PredictedPhase.RESTING -> snapshot.liveSetsOnCurrent().maxOfOrNull { it.setNumber }
        else -> null
    }
    return SessionPrediction(
        exerciseId = snapshot.currentExercise?.exerciseId,
        predictedSetNumber = setNumber,
        phase = phase,
        phaseElapsedMs = elapsed,
        predictedRestSec = restSec,
        predictedPhaseEndElapsedMs = end,
    )
}

fun predictedPhase(session: WorkoutSessionState): PredictedPhase = when (session) {
    WorkoutSessionState.SET_ACTIVE -> PredictedPhase.LIFTING
    WorkoutSessionState.RESTING, WorkoutSessionState.SET_COMPLETED -> PredictedPhase.RESTING
    WorkoutSessionState.EXERCISE_ACTIVE,
    WorkoutSessionState.READY_FOR_EXERCISE,
    WorkoutSessionState.EXERCISE_COMPLETE,
    -> PredictedPhase.BETWEEN_EXERCISES
    WorkoutSessionState.IDLE,
    WorkoutSessionState.WORKOUT_STARTING,
    WorkoutSessionState.PAUSED,
    WorkoutSessionState.WORKOUT_COMPLETE,
    WorkoutSessionState.ERROR_RECOVERY,
    -> PredictedPhase.IDLE
}
