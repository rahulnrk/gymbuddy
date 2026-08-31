package com.gymbuddy.domain.loop

import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.SafetyAction
import com.gymbuddy.domain.SafetySeverity
import com.gymbuddy.domain.SetId
import com.gymbuddy.domain.VoiceTurnState
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.context.AssemblyRequest
import com.gymbuddy.domain.context.ConversationalStateContext
import com.gymbuddy.domain.context.FourContextAssembler
import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.context.HistoricalStateContext
import com.gymbuddy.domain.context.UserStateContext
import com.gymbuddy.domain.context.WorkoutStateContext
import com.gymbuddy.domain.engine.PlannedExercise
import com.gymbuddy.domain.engine.ProjectedSet
import com.gymbuddy.domain.engine.WorkoutPlan
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
import com.gymbuddy.domain.events.WorkoutEvent
import com.gymbuddy.domain.safety.SafetyResult
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShouldSpeakTest {
    private val we = "55555555-5555-4555-8555-555555555555"
    private val ex = "44444444-4444-4444-8444-444444444444"

    private fun snap(phase: WorkoutSessionState, sets: List<ProjectedSet>) = WorkoutSessionSnapshot(
        phase = phase,
        sessionId = "s",
        userId = "u",
        workoutId = "w",
        plan = WorkoutPlan("w", listOf(PlannedExercise(we, ex, 4))),
        completedSets = sets,
        workingWeightKg = sets.lastOrNull()?.weightKg,
    )

    private fun ctx(phase: WorkoutSessionState) = FourContexts(
        WorkoutStateContext("s", "w", "t", phase, VoiceTurnState.SILENT, remainingSetsPlanned = 3, substitutions = emptyList(), startedAt = "t"),
        HistoricalStateContext(),
        ConversationalStateContext(languageDetected = "en"),
        UserStateContext("u", emptyList(), "", emptyList(), com.gymbuddy.domain.WeightUnit.KG, "en"),
    )

    private fun assembled(snapshot: WorkoutSessionSnapshot) =
        FourContextAssembler.assemble(AssemblyRequest(snapshot = snapshot))

    private fun set(n: Int, reps: Int, w: Double, id: String) = ProjectedSet(
        SetId(id), we, ex, n, w, reps, LoggingMode.LIVE,
    )

    private fun ev(type: CommittedEventType) = WorkoutEvent(
        "e-$type", "s", "u", "t", "t", EventSource.VOICE, type = type, payload = buildJsonObject {},
    )

    @Test
    fun sameRepsMustNotSpeak() {
        val s = snap(
            WorkoutSessionState.RESTING,
            listOf(set(1, 10, 60.0, "a"), set(2, 10, 60.0, "b")),
        )
        val d = ShouldSpeak.decide(
            ctx(WorkoutSessionState.RESTING), s, listOf(ev(CommittedEventType.SET_COMPLETED)),
            SafetyResult(false), false, false, null,
        )
        assertFalse(d.speak)
        assertEquals(SilenceReason.NO_USEFUL_DELTA, (d as SpeakDecision.Silence).reason)
    }

    @Test
    fun plusOneRepMustSpeak() {
        val s = snap(
            WorkoutSessionState.RESTING,
            listOf(set(1, 9, 60.0, "a"), set(2, 10, 60.0, "b")),
        )
        val d = ShouldSpeak.decide(
            assembled(s), s, listOf(ev(CommittedEventType.SET_COMPLETED)),
            SafetyResult(false), false, false, null,
        )
        assertTrue(d.speak)
        assertEquals(SpeechActId.USEFUL_DELTA, (d as SpeakDecision.Speak).actId)
    }

    @Test
    fun questionDuringSetDeferred() {
        val s = snap(WorkoutSessionState.SET_ACTIVE, emptyList())
        val d = ShouldSpeak.decide(
            ctx(WorkoutSessionState.SET_ACTIVE), s, emptyList(),
            SafetyResult(false), questionThisTurn = true, clarificationThisTurn = false, null,
        )
        assertFalse(d.speak)
        assertEquals(SilenceReason.SET_ACTIVE, (d as SpeakDecision.Silence).reason)
        assertEquals(listOf(SpeechActId.ANSWER_QUESTION), d.queued)
    }

    @Test
    fun safetyHighOverridesSetActive() {
        val s = snap(WorkoutSessionState.SET_ACTIVE, emptyList())
        val d = ShouldSpeak.decide(
            ctx(WorkoutSessionState.SET_ACTIVE), s, emptyList(),
            SafetyResult(true, "chest_pain", SafetySeverity.CRITICAL, com.gymbuddy.domain.SafetyAction.STOP_RECOMMEND, pause = true),
            false, false, null,
        )
        assertTrue(d.speak)
        assertEquals(SpeechActId.SAFETY, (d as SpeakDecision.Speak).actId)
    }

    @Test
    fun firstSetSpeaks() {
        val s = snap(WorkoutSessionState.RESTING, listOf(set(1, 10, 60.0, "a")))
        val d = ShouldSpeak.decide(
            ctx(WorkoutSessionState.RESTING), s, listOf(ev(CommittedEventType.SET_COMPLETED)),
            SafetyResult(false), false, false, null,
        )
        assertEquals(SpeechActId.FIRST_SET_OF_EXERCISE, (d as SpeakDecision.Speak).actId)
    }
}
