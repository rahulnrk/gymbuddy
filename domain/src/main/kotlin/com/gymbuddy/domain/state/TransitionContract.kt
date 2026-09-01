package com.gymbuddy.domain.state

import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.WorkoutSessionState.ERROR_RECOVERY
import com.gymbuddy.domain.WorkoutSessionState.EXERCISE_ACTIVE
import com.gymbuddy.domain.WorkoutSessionState.EXERCISE_COMPLETE
import com.gymbuddy.domain.WorkoutSessionState.IDLE
import com.gymbuddy.domain.WorkoutSessionState.PAUSED
import com.gymbuddy.domain.WorkoutSessionState.READY_FOR_EXERCISE
import com.gymbuddy.domain.WorkoutSessionState.RESTING
import com.gymbuddy.domain.WorkoutSessionState.SET_ACTIVE
import com.gymbuddy.domain.WorkoutSessionState.SET_COMPLETED
import com.gymbuddy.domain.WorkoutSessionState.WORKOUT_COMPLETE
import com.gymbuddy.domain.WorkoutSessionState.WORKOUT_STARTING

/**
 * Formal state-machine contract for PR 2.
 * This is a transition table, not the machine. Do not add side effects here.
 */
data class Transition(
    val from: WorkoutSessionState,
    val event: CommittedEventType,
    val to: WorkoutSessionState,
)

object WorkoutTransitionContract {
    val legal: Set<Transition> = setOf(
        Transition(IDLE, CommittedEventType.SESSION_INITIALIZED, WORKOUT_STARTING),
        Transition(WORKOUT_STARTING, CommittedEventType.WORKOUT_STARTED, READY_FOR_EXERCISE),
        Transition(READY_FOR_EXERCISE, CommittedEventType.EXERCISE_STARTED, EXERCISE_ACTIVE),
        Transition(READY_FOR_EXERCISE, CommittedEventType.WORKOUT_PAUSED, PAUSED),
        Transition(EXERCISE_ACTIVE, CommittedEventType.SET_STARTED, SET_ACTIVE),
        Transition(EXERCISE_ACTIVE, CommittedEventType.SET_COMPLETED, SET_COMPLETED),
        Transition(EXERCISE_ACTIVE, CommittedEventType.WORKOUT_PAUSED, PAUSED),
        Transition(EXERCISE_ACTIVE, CommittedEventType.EXERCISE_SKIPPED, EXERCISE_COMPLETE),
        Transition(EXERCISE_ACTIVE, CommittedEventType.EXERCISE_DEFERRED, READY_FOR_EXERCISE),
        Transition(READY_FOR_EXERCISE, CommittedEventType.EXERCISE_DEFERRED, READY_FOR_EXERCISE),
        Transition(SET_ACTIVE, CommittedEventType.SET_COMPLETED, SET_COMPLETED),
        Transition(SET_COMPLETED, CommittedEventType.SET_COMPLETED, SET_COMPLETED),
        Transition(SET_ACTIVE, CommittedEventType.WORKOUT_PAUSED, PAUSED),
        Transition(SET_ACTIVE, CommittedEventType.EXERCISE_SKIPPED, EXERCISE_COMPLETE),
        Transition(SET_COMPLETED, CommittedEventType.REST_STARTED, RESTING),
        Transition(RESTING, CommittedEventType.REST_ENDED, RESTING),
        Transition(RESTING, CommittedEventType.SET_STARTED, SET_ACTIVE),
        Transition(RESTING, CommittedEventType.SET_COMPLETED, SET_COMPLETED),
        Transition(RESTING, CommittedEventType.EXERCISE_SKIPPED, EXERCISE_COMPLETE),
        Transition(RESTING, CommittedEventType.EXERCISE_DEFERRED, READY_FOR_EXERCISE),
        Transition(EXERCISE_COMPLETE, CommittedEventType.EXERCISE_DEFERRED, READY_FOR_EXERCISE),
        Transition(RESTING, CommittedEventType.WORKOUT_PAUSED, PAUSED),
        Transition(EXERCISE_COMPLETE, CommittedEventType.EXERCISE_STARTED, READY_FOR_EXERCISE),
        Transition(EXERCISE_COMPLETE, CommittedEventType.EXERCISE_STARTED, EXERCISE_ACTIVE),
        Transition(EXERCISE_COMPLETE, CommittedEventType.WORKOUT_COMPLETED, WORKOUT_COMPLETE),
        Transition(WORKOUT_STARTING, CommittedEventType.INTENT_REJECTED, ERROR_RECOVERY),
        Transition(ERROR_RECOVERY, CommittedEventType.WORKOUT_PAUSED, PAUSED),
        Transition(PAUSED, CommittedEventType.WORKOUT_RESUMED, READY_FOR_EXERCISE),
        Transition(PAUSED, CommittedEventType.WORKOUT_RESUMED, EXERCISE_ACTIVE),
        Transition(PAUSED, CommittedEventType.WORKOUT_RESUMED, SET_ACTIVE),
        Transition(PAUSED, CommittedEventType.WORKOUT_RESUMED, RESTING),
        Transition(PAUSED, CommittedEventType.WORKOUT_COMPLETED, WORKOUT_COMPLETE),
    )

    fun isLegal(from: WorkoutSessionState, event: CommittedEventType): Boolean =
        legal.any { it.from == from && it.event == event }

    fun destinations(from: WorkoutSessionState, event: CommittedEventType): Set<WorkoutSessionState> =
        legal.filter { it.from == from && it.event == event }.map { it.to }.toSet()

    /** Closed illegal cases that PR 2 tests must reject. */
    val forbiddenExamples: List<Pair<WorkoutSessionState, CommittedEventType>> = listOf(
        IDLE to CommittedEventType.WORKOUT_STARTED,
        IDLE to CommittedEventType.SET_COMPLETED,
        READY_FOR_EXERCISE to CommittedEventType.WORKOUT_STARTED,
        SET_ACTIVE to CommittedEventType.SET_STARTED,
        SET_ACTIVE to CommittedEventType.EXERCISE_DEFERRED,
        WORKOUT_COMPLETE to CommittedEventType.SET_COMPLETED,
        PAUSED to CommittedEventType.WORKOUT_STARTED,
        EXERCISE_ACTIVE to CommittedEventType.WORKOUT_STARTED,
    )
}

object SessionStartContract {
    const val SEQUENCE = "IDLE --SESSION_INITIALIZED--> WORKOUT_STARTING --WORKOUT_STARTED--> READY_FOR_EXERCISE"
    const val CRASH = "non-terminal session --> SESSION_REHYDRATED (never WORKOUT_STARTED)"
    const val RESUME = "PAUSED --WORKOUT_RESUMED--> prePauseState"
    const val FIRST_SET_TIMER_SEC = 8
}

object SetActiveEntryContract {
    const val SET_STARTED_REQUIRED = true
    const val PRODUCER = "engine"
    const val LLM_MAY_EMIT = false
}

object OverlayContract {
    fun aiSpeakingForbidden(state: WorkoutSessionState, safetySeverityHighOrAbove: Boolean): Boolean {
        return state == SET_ACTIVE && !safetySeverityHighOrAbove
    }
}
