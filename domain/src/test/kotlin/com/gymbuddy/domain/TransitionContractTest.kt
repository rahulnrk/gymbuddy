package com.gymbuddy.domain

import com.gymbuddy.domain.state.OverlayContract
import com.gymbuddy.domain.state.SessionStartContract
import com.gymbuddy.domain.state.SetActiveEntryContract
import com.gymbuddy.domain.state.WorkoutTransitionContract
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransitionContractTest {
    @Test
    fun workoutStartedFromIdle_isIllegal() {
        assertFalse(
            WorkoutTransitionContract.isLegal(
                WorkoutSessionState.IDLE,
                CommittedEventType.WORKOUT_STARTED,
            )
        )
    }

    @Test
    fun sessionInitializedFromIdle_isLegal() {
        assertTrue(
            WorkoutTransitionContract.isLegal(
                WorkoutSessionState.IDLE,
                CommittedEventType.SESSION_INITIALIZED,
            )
        )
    }

    @Test
    fun setStartedFromExerciseActive_entersSetActive() {
        val dest = WorkoutTransitionContract.destinations(
            WorkoutSessionState.EXERCISE_ACTIVE,
            CommittedEventType.SET_STARTED,
        )
        assertTrue(WorkoutSessionState.SET_ACTIVE in dest)
    }

    @Test
    fun forbiddenExamples_allIllegal() {
        WorkoutTransitionContract.forbiddenExamples.forEach { (from, event) ->
            assertFalse(
                WorkoutTransitionContract.isLegal(from, event),
                "$from + $event must be illegal",
            )
        }
    }

    @Test
    fun setStartedIsEngineOnly() {
        assertTrue(SetActiveEntryContract.SET_STARTED_REQUIRED)
        assertFalse(SetActiveEntryContract.LLM_MAY_EMIT)
        assertTrue(SessionStartContract.FIRST_SET_TIMER_SEC == 8)
    }

    @Test
    fun setActiveForbidsAiSpeechUnlessSafetyHigh() {
        assertTrue(
            OverlayContract.aiSpeakingForbidden(
                WorkoutSessionState.SET_ACTIVE,
                safetySeverityHighOrAbove = false,
            )
        )
        assertFalse(
            OverlayContract.aiSpeakingForbidden(
                WorkoutSessionState.SET_ACTIVE,
                safetySeverityHighOrAbove = true,
            )
        )
        assertFalse(
            OverlayContract.aiSpeakingForbidden(
                WorkoutSessionState.RESTING,
                safetySeverityHighOrAbove = false,
            )
        )
    }
}
