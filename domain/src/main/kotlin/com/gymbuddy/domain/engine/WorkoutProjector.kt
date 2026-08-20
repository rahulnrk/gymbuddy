package com.gymbuddy.domain.engine

import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.SetId
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.events.WorkoutEvent
import com.gymbuddy.domain.state.WorkoutTransitionContract
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure fold. No I/O, no LLM, no Android. Same events → same snapshot.
 */
object WorkoutProjector {

    fun fold(
        events: List<WorkoutEvent>,
        initial: WorkoutSessionSnapshot = WorkoutSessionSnapshot(),
        plan: WorkoutPlan? = initial.plan,
    ): WorkoutSessionSnapshot {
        var state = initial.copy(plan = plan ?: initial.plan)
        for (e in events) {
            when (val r = apply(state, e)) {
                is ApplyResult.Ok -> state = r.snapshot
                is ApplyResult.Duplicate -> { /* idempotent no-op */ }
                is ApplyResult.Rejected -> {
                    // Illegal committed event: do not repair. Stop applying this event.
                }
            }
        }
        return state
    }

    fun apply(state: WorkoutSessionSnapshot, event: WorkoutEvent): ApplyResult {
        if (event.eventId in state.appliedEventIds) return ApplyResult.Duplicate
        val legal = WorkoutTransitionContract.isLegal(state.phase, event.type) ||
            event.type in nonPhaseEvents ||
            (event.type == CommittedEventType.SESSION_REHYDRATED && state.phase != WorkoutSessionState.IDLE) ||
            (event.type == CommittedEventType.SET_CORRECTED && state.phase != WorkoutSessionState.IDLE) ||
            (event.type == CommittedEventType.SET_DELETED && state.phase != WorkoutSessionState.IDLE) ||
            (event.type == CommittedEventType.WEIGHT_CHANGED && state.phase != WorkoutSessionState.IDLE) ||
            (event.type == CommittedEventType.PLANNED_SETS_CHANGED && state.phase != WorkoutSessionState.IDLE) ||
            (event.type == CommittedEventType.SUBSTITUTION_APPLIED && state.phase != WorkoutSessionState.IDLE)
        if (!legal) return ApplyResult.Rejected("illegal ${state.phase} + ${event.type}")
        val nextPhase = phaseAfter(state, event)
        val payloadState = applyPayload(state, event)
        val paused = if (event.type == CommittedEventType.WORKOUT_PAUSED) {
            payloadState.copy(phase = WorkoutSessionState.PAUSED, prePauseState = state.phase)
        } else {
            payloadState.copy(phase = nextPhase)
        }
        return ApplyResult.Ok(
            paused.copy(
                lastEventId = event.eventId,
                localEpoch = state.localEpoch + 1,
                appliedEventIds = state.appliedEventIds + event.eventId,
                userId = event.userId,
                sessionId = event.sessionId ?: state.sessionId,
            ),
        )
    }

    private val nonPhaseEvents = setOf(
        CommittedEventType.USER_QUESTION,
        CommittedEventType.USER_FEEDBACK,
        CommittedEventType.USER_NOTE_CREATED,
        CommittedEventType.COACHING_RESPONSE,
        CommittedEventType.LANGUAGE_DETECTED,
        CommittedEventType.BARGE_IN,
        CommittedEventType.TURN_ENDED,
        CommittedEventType.AUDIO_FOCUS_CHANGED,
        CommittedEventType.CLARIFICATION_REQUESTED,
        CommittedEventType.INTENT_REJECTED,
    )

    private fun phaseAfter(state: WorkoutSessionSnapshot, event: WorkoutEvent): WorkoutSessionState {
        if (event.type in nonPhaseEvents) {
            if (event.type == CommittedEventType.INTENT_REJECTED &&
                state.phase == WorkoutSessionState.WORKOUT_STARTING
            ) {
                return WorkoutSessionState.ERROR_RECOVERY
            }
            return state.phase
        }
        if (event.type == CommittedEventType.SESSION_REHYDRATED) {
            return if (state.prePauseState != null) WorkoutSessionState.PAUSED else state.phase
        }
        if (event.type == CommittedEventType.WORKOUT_RESUMED) {
            return state.prePauseState ?: WorkoutSessionState.READY_FOR_EXERCISE
        }
        if (event.type == CommittedEventType.SET_COMPLETED) {
            val afterCount = state.liveSetsOnCurrent().size + 1
            val planned = state.currentExercise?.plannedSets ?: Int.MAX_VALUE
            return if (afterCount >= planned) WorkoutSessionState.EXERCISE_COMPLETE
            else WorkoutSessionState.SET_COMPLETED
        }
        if (event.type == CommittedEventType.EXERCISE_SKIPPED) {
            return WorkoutSessionState.EXERCISE_COMPLETE
        }
        val dest = WorkoutTransitionContract.destinations(state.phase, event.type)
        if (event.type == CommittedEventType.EXERCISE_STARTED) {
            return WorkoutSessionState.EXERCISE_ACTIVE
        }
        return dest.firstOrNull() ?: state.phase
    }

    private fun applyPayload(state: WorkoutSessionSnapshot, event: WorkoutEvent): WorkoutSessionSnapshot {
        val p = event.payload
        return when (event.type) {
            CommittedEventType.SESSION_INITIALIZED -> state.copy(
                sessionId = p.str("sessionId") ?: event.sessionId,
                workoutId = p.str("workoutId") ?: state.workoutId,
                userId = event.userId,
            )
            CommittedEventType.WORKOUT_STARTED -> state.copy(
                workoutId = p.str("workoutId") ?: state.workoutId,
            )
            CommittedEventType.EXERCISE_STARTED -> {
                val we = p.str("workoutExerciseId")
                val idx = state.plan?.exercises?.indexOfFirst { it.workoutExerciseId == we } ?: state.currentExerciseIndex
                state.copy(currentExerciseIndex = idx.coerceAtLeast(0), rest = null)
            }
            CommittedEventType.SET_STARTED -> state.copy(
                rest = null,
            )
            CommittedEventType.SET_COMPLETED -> {
                val set = ProjectedSet(
                    setId = SetId(p.str("setId") ?: event.eventId),
                    workoutExerciseId = p.str("workoutExerciseId") ?: state.currentExercise?.workoutExerciseId.orEmpty(),
                    exerciseId = p.str("exerciseId") ?: state.currentExercise?.exerciseId.orEmpty(),
                    setNumber = p.int("setNumber") ?: state.nextOfficialSetNumber(),
                    weightKg = p.double("weightKg"),
                    reps = p.int("reps"),
                    loggingMode = p.str("loggingMode")?.let { runCatching { LoggingMode.valueOf(it.uppercase()) }.getOrNull() }
                        ?: LoggingMode.LIVE,
                    source = event.source,
                    batchId = event.batchId ?: p.str("batchId"),
                )
                state.copy(
                    completedSets = state.completedSets + set,
                    workingWeightKg = set.weightKg ?: state.workingWeightKg,
                    rest = null,
                )
            }
            CommittedEventType.SET_CORRECTED -> {
                val id = p.str("setId") ?: return state
                state.copy(
                    completedSets = state.completedSets.map {
                        if (it.setId.value == id && !it.deleted) {
                            val fields = event.payload["fields"] as? JsonObject
                            it.copy(
                                reps = fields?.int("reps") ?: it.reps,
                                weightKg = fields?.double("weightKg") ?: it.weightKg,
                                lastCorrectionEventId = event.eventId,
                            )
                        } else it
                    },
                )
            }
            CommittedEventType.SET_DELETED -> {
                val id = p.str("setId") ?: return state
                state.copy(
                    completedSets = state.completedSets.map {
                        if (it.setId.value == id) it.copy(deleted = true) else it
                    },
                )
            }
            CommittedEventType.REST_STARTED -> state.copy(
                rest = RestState(
                    afterSetId = SetId(p.str("afterSetId") ?: ""),
                    targetSec = p.int("targetSec") ?: 90,
                ),
            )
            CommittedEventType.REST_ENDED -> state.copy(rest = null)
            CommittedEventType.WORKOUT_PAUSED -> state.copy(
                prePauseState = state.phase.takeIf { it != WorkoutSessionState.PAUSED } ?: state.prePauseState,
                restRemainingSec = p.double("restRemainingSec") ?: state.restRemainingSec,
            )
            CommittedEventType.WORKOUT_RESUMED -> state.copy(
                prePauseState = null,
                restRemainingSec = p.double("restRemainingSec") ?: state.restRemainingSec,
            )
            CommittedEventType.PLANNED_SETS_CHANGED -> {
                val we = p.str("workoutExerciseId")
                val planned = p.int("plannedSets") ?: return state
                val plan = state.plan ?: return state
                state.copy(
                    plan = plan.copy(
                        exercises = plan.exercises.map {
                            if (it.workoutExerciseId == we) it.copy(plannedSets = planned) else it
                        },
                    ),
                )
            }
            CommittedEventType.EXERCISE_SKIPPED -> {
                val we = p.str("workoutExerciseId") ?: state.currentExercise?.workoutExerciseId
                val plan = state.plan ?: return state
                state.copy(
                    plan = plan.copy(
                        exercises = plan.exercises.map {
                            if (it.workoutExerciseId == we) it.copy(skipped = true) else it
                        },
                    ),
                    rest = null,
                )
            }
            CommittedEventType.SUBSTITUTION_APPLIED -> {
                val we = p.str("workoutExerciseId")
                val to = p.str("toExerciseId") ?: return state
                val from = p.str("fromExerciseId")
                val plan = state.plan ?: return state
                state.copy(
                    plan = plan.copy(
                        exercises = plan.exercises.map {
                            if (it.workoutExerciseId == we) {
                                it.copy(exerciseId = to, substitutedFromExerciseId = from)
                            } else it
                        },
                    ),
                )
            }
            CommittedEventType.WEIGHT_CHANGED -> state.copy(
                workingWeightKg = p.double("weightKg") ?: state.workingWeightKg,
            )
            else -> state
        }
    }

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String) = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.double(key: String) = this[key]?.jsonPrimitive?.doubleOrNull
}

sealed class ApplyResult {
    data class Ok(val snapshot: WorkoutSessionSnapshot) : ApplyResult()
    data object Duplicate : ApplyResult()
    data class Rejected(val reason: String) : ApplyResult()
}
