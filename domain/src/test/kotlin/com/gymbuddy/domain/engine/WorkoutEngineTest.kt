package com.gymbuddy.domain.engine

import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.SetId
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.events.CandidateEventEnvelope
import com.gymbuddy.domain.validation.DefaultCandidateValidator
import com.gymbuddy.domain.validation.HintOrdinal
import com.gymbuddy.domain.validation.SetNumberHint
import com.gymbuddy.domain.validation.ValidatedFact
import com.gymbuddy.domain.validation.VoiceCommitRoute
import com.gymbuddy.domain.AudioRoute
import com.gymbuddy.domain.CommandKind
import com.gymbuddy.domain.context.CompletedSetSnapshot
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

class WorkoutEngineTest {
    private val user = "11111111-1111-4111-8111-111111111111"
    private val session = "22222222-2222-4222-8222-222222222222"
    private val workout = "33333333-3333-4333-8333-333333333333"
    private val bench = "44444444-4444-4444-8444-444444444444"
    private val incline = "44444444-4444-4444-8444-444444444445"
    private val we1 = "55555555-5555-4555-8555-555555555555"
    private val we2 = "55555555-5555-4555-8555-555555555556"

    private fun plan(planned: Int = 4) = WorkoutPlan(
        workout,
        listOf(
            PlannedExercise(we1, bench, planned),
            PlannedExercise(we2, incline, 3),
        ),
    )

    private fun engine() = WorkoutEngine(InMemoryEventLog())

    private fun started(planned: Int = 4): Pair<WorkoutEngine, WorkoutPlan> {
        val e = engine()
        val p = plan(planned)
        val r = e.startSession(user, session, p)
        assertTrue(r is EngineResult.Committed, r.toString())
        return e to p
    }

    @Test
    fun sessionInitThenWorkoutStartedThenExercise() {
        val (e, p) = started()
        val s = e.snapshot(session, p)
        assertEquals(WorkoutSessionState.EXERCISE_ACTIVE, s.phase)
        val types = (e as WorkoutEngine).let {
            (it.snapshot(session, p))
            (e.snapshot(session, p))
        }
        val log = InMemoryEventLog()
        val e2 = WorkoutEngine(log)
        e2.startSession(user, session, p)
        val types2 = log.all().map { it.type }
        assertEquals(
            listOf(
                CommittedEventType.SESSION_INITIALIZED,
                CommittedEventType.WORKOUT_STARTED,
                CommittedEventType.EXERCISE_STARTED,
            ),
            types2,
        )
        assertTrue(s.phase != WorkoutSessionState.IDLE)
    }

    @Test
    fun workoutStartedFromIdleRejected() {
        val log = InMemoryEventLog()
        val e = WorkoutEngine(log)
        val ev = EventFactory.event(
            CommittedEventType.WORKOUT_STARTED, user, session,
            EventFactory.payload("workoutId" to workout, "startedAt" to "2026-08-20T16:00:00Z"),
            EventSource.SYSTEM, "2026-08-20T16:00:00Z",
        )
        val r = e.tryCommitEvent(session, plan(), ev)
        assertTrue(r is EngineResult.Rejected)
    }

    @Test
    fun liveSetsNumberedAndRestAfterWhenPlannedRemain() {
        val (e, p) = started(4)
        val fact = ValidatedFact.SetCompleted(10, 60.0, true, LoggingMode.LIVE)
        val r = e.applyFacts(session, p, listOf(fact)) as EngineResult.Committed
        assertTrue(r.events.any { it.type == CommittedEventType.SET_STARTED })
        val completed = r.events.filter { it.type == CommittedEventType.SET_COMPLETED }
        assertEquals(1, completed.size)
        assertEquals(1, completed.single().payload["setNumber"]!!.jsonPrimitive.int)
        assertTrue(r.events.any { it.type == CommittedEventType.REST_STARTED })
        assertEquals(WorkoutSessionState.RESTING, r.snapshot.phase)
        val r2 = e.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(8, 60.0, true, LoggingMode.LIVE))) as EngineResult.Committed
        assertEquals(2, r2.snapshot.liveSetsOnCurrent().maxOf { it.setNumber })
    }

    @Test
    fun hintsCannotOverrideEngineNumberingOnLiveSets() {
        val (e, p) = started(4)
        e.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(10, 60.0, true, LoggingMode.LIVE)))
        e.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(8, 60.0, true, LoggingMode.LIVE)))
        val r = e.applyFacts(
            session, p,
            listOf(
                ValidatedFact.SetCompleted(
                    6, 60.0, true, LoggingMode.LIVE,
                    setNumberHint = SetNumberHint.Ordinal(HintOrdinal.FIRST),
                ),
            ),
        ) as EngineResult.Committed
        val nums = r.snapshot.liveSetsOnCurrent().map { it.setNumber }.sorted()
        assertEquals(listOf(1, 2, 3), nums)
    }

    @Test
    fun catchUpNumbers123NoSetStartedNoRestBetween() {
        val (e, p) = started(4)
        val facts = listOf(
            ValidatedFact.SetCompleted(12, 40.0, false, LoggingMode.CATCH_UP, SetNumberHint.Ordinal(HintOrdinal.FIRST), batchId = "b"),
            ValidatedFact.SetCompleted(10, 40.0, true, LoggingMode.CATCH_UP, SetNumberHint.Ordinal(HintOrdinal.SECOND), batchId = "b"),
            ValidatedFact.SetCompleted(8, 40.0, true, LoggingMode.CATCH_UP, SetNumberHint.Ordinal(HintOrdinal.THIS), batchId = "b"),
        )
        val r = e.applyFacts(session, p, facts) as EngineResult.Committed
        val completed = r.events.filter { it.type == CommittedEventType.SET_COMPLETED }
        assertEquals(listOf(1, 2, 3), completed.map { it.payload["setNumber"]!!.jsonPrimitive.int })
        assertEquals(0, r.events.count { it.type == CommittedEventType.SET_STARTED })
        val restIdx = r.events.mapIndexedNotNull { i, ev -> if (ev.type == CommittedEventType.REST_STARTED) i else null }
        assertEquals(1, restIdx.size)
        val lastCompleted = r.events.indexOfLast { it.type == CommittedEventType.SET_COMPLETED }
        assertTrue(restIdx.single() > lastCompleted)
        assertEquals(WorkoutSessionState.RESTING, r.snapshot.phase)
        assertEquals(1, r.snapshot.remainingPlannedOnCurrent())
    }

    @Test
    fun correctionKeepsSetId() {
        val (e, p) = started(4)
        e.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(10, 60.0, true, LoggingMode.LIVE)))
        val id = e.snapshot(session, p).liveSetsOnCurrent().single().setId
        val r = e.applyFacts(
            session, p,
            listOf(
                ValidatedFact.SetCorrected(
                    id,
                    com.gymbuddy.domain.validation.CorrectionFields(reps = 8),
                    com.gymbuddy.domain.validation.PreviousSetSnapshot(id, 10, 60.0, 1),
                ),
            ),
        ) as EngineResult.Committed
        assertEquals(1, r.snapshot.liveSetsOnCurrent().size)
        assertEquals(id, r.snapshot.liveSetsOnCurrent().single().setId)
        assertEquals(8, r.snapshot.liveSetsOnCurrent().single().reps)
        assertEquals(id.value, r.events.single { it.type == CommittedEventType.SET_CORRECTED }.payload["setId"]!!.toString().replace("\"", ""))
    }

    @Test
    fun occupancyClarifyDoesNotReachEngineAndRenumber() {
        val v = DefaultCandidateValidator()
        val ctx = FourContexts(
            workout = WorkoutStateContext(
                sessionId = session, workoutId = workout, workoutName = "t",
                sessionState = WorkoutSessionState.RESTING, voiceState = VoiceTurnState.SILENT,
                currentExerciseId = bench,
                completedSets = listOf(
                    CompletedSetSnapshot("66666666-6666-4666-8666-666666666666", 1, 60.0, 10, occurredAt = "2026-08-20T15:58:00Z"),
                    CompletedSetSnapshot("66666666-6666-4666-8666-666666666667", 2, 60.0, 8, occurredAt = "2026-08-20T15:59:00Z"),
                    CompletedSetSnapshot("66666666-6666-4666-8666-666666666668", 3, 60.0, 7, occurredAt = "2026-08-20T16:00:00Z"),
                ),
                remainingSetsPlanned = 1, substitutions = emptyList(), startedAt = "2026-08-20T16:00:00Z",
            ),
            historical = HistoricalStateContext(),
            conversational = ConversationalStateContext(languageDetected = "en"),
            user = UserStateContext(user, emptyList(), "intermediate", emptyList(), WeightUnit.KG, "en"),
        )
        val env = CandidateEventEnvelope.parse(
            """{"schemaVersion":1,"candidates":[{"candidateId":"77777777-7777-4777-8777-777777777777","type":"SET_CORRECTED","overallConfidence":0.92,"fieldConfidence":{"reps":0.9},"setNumberHint":"second","payload":{"reps":9,"target":"second"}}]}""",
        )
        val d = v.validate(
            env.candidates.single(), ctx,
            VoiceCommitRoute(AudioRoute.HEADSET, 1, 900.0, false),
        )
        assertTrue(d is com.gymbuddy.domain.validation.ValidationDecision.Clarify)
        val (e, p) = started(4)
        e.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(10, 60.0, true, LoggingMode.LIVE)))
        e.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(8, 60.0, true, LoggingMode.LIVE)))
        e.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(7, 60.0, true, LoggingMode.LIVE)))
        val before = e.snapshot(session, p).liveSetsOnCurrent().associate { it.setNumber to it.reps }
        e.applyFacts(session, p, emptyList())
        val after = e.snapshot(session, p).liveSetsOnCurrent().associate { it.setNumber to it.reps }
        assertEquals(before, after)
    }

    @Test
    fun setDeletedSoftAndReplayable() {
        val (e, p) = started(4)
        e.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(10, 60.0, true, LoggingMode.LIVE)))
        val id = e.snapshot(session, p).liveSetsOnCurrent().single().setId
        e.applyFacts(
            session, p,
            listOf(ValidatedFact.SetDeleted(id, com.gymbuddy.domain.validation.PreviousSetSnapshot(id, 10, 60.0, 1), "scratch that")),
        )
        assertTrue(e.snapshot(session, p).liveSetsOnCurrent().isEmpty())
        assertTrue(e.snapshot(session, p).completedSets.any { it.deleted && it.setId == id })
    }

    @Test
    fun skipTransitionsToNextExercise() {
        val log = InMemoryEventLog()
        val e = WorkoutEngine(log)
        val p = plan()
        e.startSession(user, session, p)
        val r = e.applyFacts(session, p, listOf(ValidatedFact.Command(CommandKind.SKIP))) as EngineResult.Committed
        assertTrue(r.events.any { it.type == CommittedEventType.EXERCISE_SKIPPED })
        assertEquals(incline, r.snapshot.currentExercise?.exerciseId)
        val folded = WorkoutProjector.fold(log.all(), plan = p)
        assertEquals(incline, folded.currentExercise?.exerciseId)
    }

    @Test
    fun extraSetIncreasesPlannedDoesNotLogSetOrMutateOtherExercises() {
        val (e, p) = started(3)
        val before = e.snapshot(session, p).currentExercise!!.plannedSets
        val r = e.applyFacts(session, p, listOf(ValidatedFact.Command(CommandKind.EXTRA_SET))) as EngineResult.Committed
        assertTrue(r.events.any { it.type == CommittedEventType.PLANNED_SETS_CHANGED })
        assertEquals(before + 1, r.snapshot.currentExercise!!.plannedSets)
        assertEquals(0, r.snapshot.liveSetsOnCurrent().size)
        assertEquals(3, r.snapshot.plan!!.exercises[1].plannedSets)
    }

    @Test
    fun substitutionProposeDoesNotMutateAppliedDoes() {
        val (e, p) = started()
        val before = e.snapshot(session, p).currentExercise!!.exerciseId
        e.applyFacts(session, p, listOf(ValidatedFact.SubstitutionPropose(toExerciseId = incline)))
        assertEquals(before, e.snapshot(session, p).currentExercise!!.exerciseId)
        val r = e.applySubstitution(session, p, incline) as EngineResult.Committed
        assertTrue(r.events.any { it.type == CommittedEventType.SUBSTITUTION_APPLIED })
        assertEquals(incline, r.snapshot.currentExercise!!.exerciseId)
        assertEquals(bench, r.snapshot.currentExercise!!.substitutedFromExerciseId)
    }

    @Test
    fun pauseResumeRestoresPhaseNotWorkoutStarted() {
        val (e, p) = started()
        e.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(10, 60.0, true, LoggingMode.LIVE)))
        val paused = e.pause(session, p, restRemainingSec = 45.0) as EngineResult.Committed
        assertEquals(WorkoutSessionState.PAUSED, paused.snapshot.phase)
        assertEquals(45.0, paused.snapshot.restRemainingSec)
        val resumed = e.resume(session, p) as EngineResult.Committed
        assertTrue(resumed.events.any { it.type == CommittedEventType.WORKOUT_RESUMED })
        assertFalse(resumed.events.any { it.type == CommittedEventType.WORKOUT_STARTED })
        assertEquals(WorkoutSessionState.RESTING, resumed.snapshot.phase)
        assertEquals(45.0, resumed.snapshot.restRemainingSec)
    }

    @Test
    fun recoverIsRehydrateNotSecondStart() {
        val log = InMemoryEventLog()
        val e = WorkoutEngine(log)
        val p = plan()
        e.startSession(user, session, p)
        e.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(10, 60.0, true, LoggingMode.LIVE)))
        val r = e.recover(session, p) as EngineResult.Committed
        assertTrue(r.events.any { it.type == CommittedEventType.SESSION_REHYDRATED })
        assertEquals(1, log.all().count { it.type == CommittedEventType.WORKOUT_STARTED })
        assertEquals(1, r.snapshot.liveSetsOnCurrent().size)
    }

    @Test
    fun replayDeterministicAndIdempotent() {
        val log = InMemoryEventLog()
        val e = WorkoutEngine(log)
        val p = plan()
        e.startSession(user, session, p)
        e.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(10, 60.0, true, LoggingMode.LIVE)))
        val events = log.all()
        val a = WorkoutProjector.fold(events, plan = p)
        val b = WorkoutProjector.fold(events, plan = p)
        assertEquals(a, b)
        val dup = events.first()
        assertTrue(log.append(dup) is com.gymbuddy.domain.persist.AppendAck.Duplicate)
        val c = WorkoutProjector.fold(log.all(), plan = p)
        assertEquals(a.liveSetsOnCurrent().size, c.liveSetsOnCurrent().size)
    }

    @Test
    fun forbiddenTransitionsRejected() {
        val e = engine()
        val p = plan()
        val idleComplete = EventFactory.event(
            CommittedEventType.SET_COMPLETED, user, session,
            EventFactory.payload("setId" to "a", "setNumber" to 1, "reps" to 10, "weightKg" to 60.0, "loggingMode" to LoggingMode.LIVE),
            EventSource.VOICE, "2026-08-20T16:00:00Z",
        )
        assertTrue(e.tryCommitEvent(session, p, idleComplete) is EngineResult.Rejected)
        val (e2, p2) = started()
        e2.beginSet(session, p2)
        val secondStart = EventFactory.event(
            CommittedEventType.SET_STARTED, user, session,
            EventFactory.payload("workoutExerciseId" to we1, "setNumber" to 1),
            EventSource.SYSTEM, "2026-08-20T16:00:00Z",
        )
        assertTrue(e2.tryCommitEvent(session, p2, secondStart) is EngineResult.Rejected)
        e2.pause(session, p2)
        val started = EventFactory.event(
            CommittedEventType.WORKOUT_STARTED, user, session,
            EventFactory.payload("workoutId" to workout, "startedAt" to "t"),
            EventSource.SYSTEM, "2026-08-20T16:00:00Z",
        )
        assertTrue(e2.tryCommitEvent(session, p2, started) is EngineResult.Rejected)
    }

    @Test
    fun setActiveRequiresEngineSetStartedForLive() {
        val (e, p) = started()
        val r = e.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(10, 60.0, true, LoggingMode.LIVE))) as EngineResult.Committed
        val types = r.events.map { it.type }
        assertTrue(types.indexOf(CommittedEventType.SET_STARTED) < types.indexOf(CommittedEventType.SET_COMPLETED))
        assertEquals(EventSource.SYSTEM, r.events.first { it.type == CommittedEventType.SET_STARTED }.source)
    }
}
