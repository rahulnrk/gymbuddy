package com.gymbuddy.domain.engine

import com.gymbuddy.domain.AudioRoute
import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.CommandKind
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.events.CandidateEventEnvelope
import com.gymbuddy.domain.validation.DefaultCandidateValidator
import com.gymbuddy.domain.validation.HintOrdinal
import com.gymbuddy.domain.validation.SetNumberHint
import com.gymbuddy.domain.validation.ValidatedFact
import com.gymbuddy.domain.validation.VoiceCommitRoute
import com.gymbuddy.domain.context.ConversationalStateContext
import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.context.HistoricalStateContext
import com.gymbuddy.domain.context.UserStateContext
import com.gymbuddy.domain.context.WorkoutStateContext
import com.gymbuddy.domain.WeightUnit
import com.gymbuddy.domain.VoiceTurnState
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Pr2GoldenTest {
    private val user = "11111111-1111-4111-8111-111111111111"
    private val session = "22222222-2222-4222-8222-222222222222"
    private val workout = "33333333-3333-4333-8333-333333333333"
    private val bench = "44444444-4444-4444-8444-444444444444"
    private val incline = "44444444-4444-4444-8444-444444444445"
    private val we1 = "55555555-5555-4555-8555-555555555555"
    private val we2 = "55555555-5555-4555-8555-555555555556"

    private fun plan(planned: Int = 4) = WorkoutPlan(
        workout,
        listOf(PlannedExercise(we1, bench, planned), PlannedExercise(we2, incline, 3)),
    )

    @Test
    fun catchUpGolden_06() {
        val log = InMemoryEventLog()
        val e = WorkoutEngine(log)
        val p = plan(4)
        e.startSession(user, session, p)
        val facts = listOf(
            ValidatedFact.SetCompleted(12, 40.0, false, LoggingMode.CATCH_UP, SetNumberHint.Ordinal(HintOrdinal.FIRST), "batch-1"),
            ValidatedFact.SetCompleted(10, 40.0, true, LoggingMode.CATCH_UP, SetNumberHint.Ordinal(HintOrdinal.SECOND), "batch-1"),
            ValidatedFact.SetCompleted(8, 40.0, true, LoggingMode.CATCH_UP, SetNumberHint.Ordinal(HintOrdinal.THIS), "batch-1"),
        )
        val r = e.applyFacts(session, p, facts) as EngineResult.Committed
        val completed = r.events.filter { it.type == CommittedEventType.SET_COMPLETED }
        assertEquals(3, completed.size)
        assertEquals(listOf(1, 2, 3), completed.map { it.payload["setNumber"]!!.jsonPrimitive.int })
        assertEquals(0, r.events.count { it.type == CommittedEventType.SET_STARTED })
        assertEquals(1, r.events.count { it.type == CommittedEventType.REST_STARTED })
        val lastC = r.events.indexOfLast { it.type == CommittedEventType.SET_COMPLETED }
        val rest = r.events.indexOfFirst { it.type == CommittedEventType.REST_STARTED }
        assertTrue(rest > lastC)
        assertEquals(1, r.snapshot.remainingPlannedOnCurrent())
        assertEquals(WorkoutSessionState.RESTING, r.snapshot.phase)
    }

    @Test
    fun skipThisGolden_17() {
        val log = InMemoryEventLog()
        val e = WorkoutEngine(log)
        val p = plan()
        e.startSession(user, session, p)
        val v = DefaultCandidateValidator()
        val env = CandidateEventEnvelope.parse(
            """{"schemaVersion":1,"candidates":[{"candidateId":"77777777-7777-4777-8777-777777777777","type":"COMMAND","overallConfidence":0.85,"fieldConfidence":{"kind":0.85},"payload":{"kind":"skip"}}]}""",
        )
        val d = v.validate(
            env.candidates.single(),
            FourContexts(
                WorkoutStateContext(
                    session, workout, "t", WorkoutSessionState.RESTING, VoiceTurnState.SILENT,
                    bench, remainingSetsPlanned = 4, substitutions = emptyList(), startedAt = "t",
                ),
                HistoricalStateContext(),
                ConversationalStateContext(languageDetected = "en"),
                UserStateContext(user, emptyList(), "intermediate", emptyList(), WeightUnit.KG, "en"),
            ),
            VoiceCommitRoute(AudioRoute.HEADSET, 1, 400.0, false),
        )
        val facts = (d as com.gymbuddy.domain.validation.ValidationDecision.Accept).facts
        assertTrue(facts.single() is ValidatedFact.Command)
        assertEquals(CommandKind.SKIP, (facts.single() as ValidatedFact.Command).kind)
        val r = e.applyFacts(session, p, facts) as EngineResult.Committed
        assertTrue(r.events.any { it.type == CommittedEventType.EXERCISE_SKIPPED })
        val folded = WorkoutProjector.fold(log.all(), plan = p)
        assertEquals(incline, folded.currentExercise?.exerciseId)
        assertTrue(folded.plan!!.exercises.first { it.workoutExerciseId == we1 }.skipped)
    }

    @Test
    fun extraSetGolden_17() {
        val e = WorkoutEngine(InMemoryEventLog())
        val p = plan(3)
        e.startSession(user, session, p)
        val r = e.applyFacts(session, p, listOf(ValidatedFact.Command(CommandKind.EXTRA_SET))) as EngineResult.Committed
        assertEquals(CommittedEventType.PLANNED_SETS_CHANGED, r.events.single().type)
        assertEquals("extra_set", r.events.single().payload["reason"]!!.jsonPrimitive.content)
        assertEquals(0, r.snapshot.liveSetsOnCurrent().size)
        assertEquals(4, r.snapshot.currentExercise!!.plannedSets)
    }

    @Test
    fun sessionInitSequenceAndInvalidFromIdle() {
        val log = InMemoryEventLog()
        val e = WorkoutEngine(log)
        e.startSession(user, session, plan())
        assertEquals(
            listOf(
                CommittedEventType.SESSION_INITIALIZED,
                CommittedEventType.WORKOUT_STARTED,
                CommittedEventType.EXERCISE_STARTED,
            ),
            log.all().map { it.type }.take(3),
        )
        val idle = WorkoutEngine(InMemoryEventLog())
        val bad = EventFactory.event(
            CommittedEventType.WORKOUT_STARTED, user, session,
            EventFactory.payload("workoutId" to workout, "startedAt" to "t"),
            com.gymbuddy.domain.EventSource.SYSTEM, "t",
        )
        assertTrue(idle.tryCommitEvent(session, plan(), bad) is EngineResult.Rejected)
    }
}
