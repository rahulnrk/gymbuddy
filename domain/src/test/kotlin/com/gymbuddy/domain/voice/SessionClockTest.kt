package com.gymbuddy.domain.voice

import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.engine.PlannedExercise
import com.gymbuddy.domain.engine.ProjectedSet
import com.gymbuddy.domain.engine.WorkoutPlan
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
import com.gymbuddy.domain.voice.VoiceInteractionConstants.DUCK_HOLD_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_MAX_SILENCE_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_MIN_GAP_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_SET_END_SLACK_MS
import com.gymbuddy.domain.voice.fake.FakeTopicScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionClockTest {
    @Test
    fun liftingHasNoPhaseEnd() {
        val prediction = predictSession(
            snapshot = snapshot(WorkoutSessionState.SET_ACTIVE),
            nowElapsedMs = 20_000L,
            phaseStartedAtElapsedMs = 5_000L,
            predictedRestSec = 90,
        )
        assertEquals(PredictedPhase.LIFTING, prediction.phase)
        assertNull(prediction.predictedPhaseEndElapsedMs)
        assertEquals(15_000L, prediction.phaseElapsedMs)
        assertEquals("ex-1", prediction.exerciseId)
    }

    @Test
    fun restingPredictsPhaseEndFromRestSec() {
        val prediction = predictSession(
            snapshot = snapshot(WorkoutSessionState.RESTING, completed = 1),
            nowElapsedMs = 40_000L,
            phaseStartedAtElapsedMs = 30_000L,
            predictedRestSec = 90,
        )
        assertEquals(PredictedPhase.RESTING, prediction.phase)
        assertEquals(120_000L, prediction.predictedPhaseEndElapsedMs)
        assertEquals(1, prediction.predictedSetNumber)
    }
}

class WindowSchedulerTest {
    @Test
    fun neverOpensDuringPredictedLifting() {
        val lifting = SessionPrediction(
            exerciseId = "ex-1",
            predictedSetNumber = 1,
            phase = PredictedPhase.LIFTING,
            phaseElapsedMs = 240_000L,
            predictedRestSec = 90,
            predictedPhaseEndElapsedMs = null,
        )
        val held = nextScheduledWindow(lifting, nowElapsedMs = 240_000L, lastWindowAtMs = 0L)
        assertIs<ScheduledWindow.Hold>(held)
        assertTrue("mid-set" in held.reason)
    }

    @Test
    fun opensAtPredictedSetEnd() {
        val rest = SessionPrediction(
            exerciseId = "ex-1",
            predictedSetNumber = 1,
            phase = PredictedPhase.RESTING,
            phaseElapsedMs = 1_000L,
            predictedRestSec = 90,
            predictedPhaseEndElapsedMs = 91_000L,
        )
        val open = nextScheduledWindow(rest, nowElapsedMs = 31_000L, lastWindowAtMs = 0L)
        assertIs<ScheduledWindow.Open>(open)
        assertEquals("predicted set end", open.reason)
        assertTrue(rest.phaseElapsedMs <= WINDOW_SET_END_SLACK_MS)
    }

    @Test
    fun minGapBlocksStackedWindows() {
        val rest = SessionPrediction(
            exerciseId = "ex-1",
            predictedSetNumber = 1,
            phase = PredictedPhase.RESTING,
            phaseElapsedMs = 500L,
            predictedRestSec = 90,
            predictedPhaseEndElapsedMs = 90_500L,
        )
        val held = nextScheduledWindow(
            rest,
            nowElapsedMs = 20_000L,
            lastWindowAtMs = 20_000L - (WINDOW_MIN_GAP_MS / 2),
        )
        assertIs<ScheduledWindow.Hold>(held)
        assertTrue("minimum gap" in held.reason)
    }

    @Test
    fun maxSilenceOpensWhenNotLifting() {
        val between = SessionPrediction(
            exerciseId = "ex-1",
            predictedSetNumber = null,
            phase = PredictedPhase.BETWEEN_EXERCISES,
            phaseElapsedMs = WINDOW_MAX_SILENCE_MS,
            predictedRestSec = 90,
            predictedPhaseEndElapsedMs = null,
        )
        val open = nextScheduledWindow(
            between,
            nowElapsedMs = WINDOW_MAX_SILENCE_MS + 1,
            lastWindowAtMs = 0L,
        )
        assertIs<ScheduledWindow.Open>(open)
        assertTrue("abandoned" in open.reason)
    }

    @Test
    fun planUsedWhenNeitherPoolHasEnoughRests() {
        val plan = 90
        var history = RestHistory()
        history = recordRest(history, "ex-1", 60)
        assertIs<RestChoice.Planned>(trustedRest(history, "ex-1", plan))
        history = recordRest(history, "ex-1", 61)
        assertIs<RestChoice.Planned>(trustedRest(history, "ex-1", plan))
    }

    @Test
    fun threeSetExerciseFeedsUserPoolNotPerExerciseMedian() {
        var history = RestHistory()
        repeat(3) { history = recordCompletedSet(history, "bench") }
        history = recordRest(history, "bench", 90)
        history = recordRest(history, "bench", 70)
        assertEquals(3, completedSetsForExercise(history, "bench"))
        assertIs<RestChoice.Planned>(trustedRest(history, "bench", 120))

        history = recordRest(history, "squat", 80)
        val user = assertIs<RestChoice.Observed>(trustedRest(history, "bench", 120))
        assertFalse(user.perExercise)
        assertEquals(3, user.sampleCount)
        assertEquals(80, user.sec)
    }

    @Test
    fun oneLongRestDoesNotMoveASampledMedian() {
        val history = listOf(90, 90, 240).fold(RestHistory()) { acc, sec ->
            recordRest(acc, "ex-1", sec)
        }
        val observed = assertIs<RestChoice.Observed>(trustedRest(history, "ex-1", 90))
        assertTrue(observed.perExercise)
        assertEquals(3, observed.sampleCount)
        assertEquals(90, observed.sec)
    }

    @Test
    fun perUserMedianWhenExerciseHasTooFewRests() {
        var history = RestHistory()
        history = recordRest(history, "ex-a", 70)
        history = recordRest(history, "ex-b", 80)
        history = recordRest(history, "ex-c", 90)
        val observed = assertIs<RestChoice.Observed>(trustedRest(history, "ex-a", 120))
        assertFalse(observed.perExercise)
        assertEquals(80, observed.sec)
    }
}

class DuckHoldAndEmergencyTest {
    @Test
    fun duckReleaseAndWindowCloseShareTheQuietHold() {
        val open = onCoachFinished(windowFor(UtteranceKind.CLOSED_ANSWER), elapsedMs = 0L)
        val lastSpeech = 10_000L
        val extended = onSpeechDetected(open, elapsedMs = lastSpeech)
        val closeAt = lastSpeech + DUCK_HOLD_MS
        val done = assertIs<Window.CoachInitiated>(extended)
        assertEquals(closeAt, done.coachFinishedAtElapsedMs!! + done.maxMs)

        val justBefore = closeAt - 1
        assertFalse(onTimeout(extended, justBefore, userSpeaking = false, lastUserSpeechAtMs = lastSpeech) is Window.None)
        assertTrue(stillDucked(extended, userSpeaking = false, lastUserSpeechAtMs = lastSpeech, nowMs = justBefore))

        val closed = onTimeout(extended, closeAt, userSpeaking = false, lastUserSpeechAtMs = lastSpeech)
        assertIs<Window.None>(closed)
        assertFalse(stillDucked(closed, userSpeaking = false, lastUserSpeechAtMs = lastSpeech, nowMs = closeAt))
    }

    @Test
    fun duckHoldsThreeSecondsAfterUserStopsTalking() {
        val none = Window.None
        assertFalse(stillDucked(none, userSpeaking = false, lastUserSpeechAtMs = null, nowMs = 10_000L))
        assertTrue(stillDucked(none, userSpeaking = true, lastUserSpeechAtMs = 10_000L, nowMs = 10_500L))
        assertTrue(
            stillDucked(none, userSpeaking = false, lastUserSpeechAtMs = 10_000L, nowMs = 10_000L + DUCK_HOLD_MS - 1),
        )
        assertFalse(
            stillDucked(none, userSpeaking = false, lastUserSpeechAtMs = 10_000L, nowMs = 10_000L + DUCK_HOLD_MS),
        )
        val open = windowFor(UtteranceKind.CLOSED_ANSWER)
        assertTrue(stillDucked(open, userSpeaking = false, lastUserSpeechAtMs = 0L, nowMs = 100_000L))
    }

    @Test
    fun emergencyOpensMidLiftIntoPainTriage() {
        val lifting = predictSession(
            snapshot = snapshot(WorkoutSessionState.SET_ACTIVE),
            nowElapsedMs = 8_000L,
            phaseStartedAtElapsedMs = 0L,
            predictedRestSec = 90,
        )
        assertEquals(PredictedPhase.LIFTING, lifting.phase)
        val scheduled = nextScheduledWindow(lifting, nowElapsedMs = 8_000L, lastWindowAtMs = null)
        assertIs<ScheduledWindow.Hold>(scheduled)

        val emergency = openEmergency()
        assertEquals(EntryPoint.EMERGENCY, emergency.window.via)
        assertEquals(EmergencyRoute.PAIN_TRIAGE, emergency.route)
        assertEquals(null, emergency.interview.spot)
        assertTrue(shouldDuck(emergency.window))
        val cloud = listenDecision(
            VadObservation(speech = false),
            wake = null,
            emergency.window,
        )
        val open = assertIs<ListenDecision.OpenCloud>(cloud)
        assertEquals(CloudReason.EMERGENCY, open.via)
    }
}

class TopicScopeTest {
    @Test
    fun outOfScopeUsesTheExactLine() {
        val fake = FakeTopicScope(TopicKind.OUT_OF_SCOPE)
        assertEquals(TopicKind.OUT_OF_SCOPE, fake.classify("what is the capital of France"))
        assertEquals(TopicCopy.OUT_OF_SCOPE, topicReply(fake.classify("anything")))
        assertEquals(
            "I'm your gym coach — I can help with training, food and injuries.",
            TopicCopy.OUT_OF_SCOPE,
        )
    }

    @Test
    fun inScopeDoesNotSpeakTheBoundaryLine() {
        val fake = FakeTopicScope(TopicKind.IN_SCOPE)
        assertEquals(TopicKind.IN_SCOPE, fake.classify("my shoulder is killing me"))
        assertNull(topicReply(fake.classify("my shoulder is killing me")))
    }

    @Test
    fun productionVoiceMainHasNoKeywordClassifier() {
        val cwd = java.io.File("").absoluteFile
        val voice = listOf(
            java.io.File(cwd, "src/main/kotlin/com/gymbuddy/domain/voice"),
            java.io.File(cwd, "domain/src/main/kotlin/com/gymbuddy/domain/voice"),
        ).first { it.isDirectory }
        val production = voice.walkTopDown()
            .filter { it.extension == "kt" }
            .filter { !it.path.replace('\\', '/').contains("/voice/fake/") }
            .joinToString("\n") { it.readText() }
        assertFalse("contains(\"shoulder\")" in production)
        assertFalse("startsWith(\"what is\")" in production)
    }
}

private fun snapshot(phase: WorkoutSessionState, completed: Int = 0): WorkoutSessionSnapshot {
    val we = "we-1"
    val sets = (1..completed).map { n ->
        ProjectedSet(
            setId = com.gymbuddy.domain.SetId("set-$n"),
            workoutExerciseId = we,
            exerciseId = "ex-1",
            setNumber = n,
            weightKg = 80.0,
            reps = 8,
            loggingMode = com.gymbuddy.domain.LoggingMode.LIVE,
        )
    }
    return WorkoutSessionSnapshot(
        phase = phase,
        plan = WorkoutPlan(
            workoutId = "w1",
            exercises = listOf(
                PlannedExercise(
                    workoutExerciseId = we,
                    exerciseId = "ex-1",
                    plannedSets = 4,
                    restTargetSec = 90,
                ),
            ),
        ),
        completedSets = sets,
    )
}
