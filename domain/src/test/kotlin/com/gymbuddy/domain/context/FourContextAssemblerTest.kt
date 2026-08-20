package com.gymbuddy.domain.context

import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.EpistemicStance
import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.WeightUnit
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.engine.EngineResult
import com.gymbuddy.domain.engine.InMemoryEventLog
import com.gymbuddy.domain.engine.PlannedExercise
import com.gymbuddy.domain.engine.WorkoutEngine
import com.gymbuddy.domain.engine.WorkoutPlan
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
import com.gymbuddy.domain.events.WorkoutEvent
import com.gymbuddy.domain.validation.CorrectionFields
import com.gymbuddy.domain.validation.PreviousSetSnapshot
import com.gymbuddy.domain.validation.ValidatedFact
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FourContextAssemblerTest {
    private val user = "11111111-1111-4111-8111-111111111111"
    private val session = "22222222-2222-4222-8222-222222222222"
    private val workout = "33333333-3333-4333-8333-333333333333"
    private val bench = "44444444-4444-4444-8444-444444444444"
    private val we1 = "55555555-5555-4555-8555-555555555555"
    private val we2 = "55555555-5555-4555-8555-555555555556"
    private val incline = "44444444-4444-4444-8444-444444444445"

    private fun plan() = WorkoutPlan(
        workout,
        listOf(PlannedExercise(we1, bench, 4), PlannedExercise(we2, incline, 3)),
    )

    private fun started(): Triple<WorkoutEngine, InMemoryEventLog, WorkoutPlan> {
        val log = InMemoryEventLog()
        val engine = WorkoutEngine(log)
        val p = plan()
        val r = engine.startSession(user, session, p)
        assertTrue(r is EngineResult.Committed)
        return Triple(engine, log, p)
    }

    private fun assemble(
        snapshot: WorkoutSessionSnapshot,
        events: List<WorkoutEvent> = emptyList(),
        history: HistoricalStore = HistoricalStore(),
        profile: UserProfileStore = UserProfileStore(userId = user, units = WeightUnit.KG),
        conversation: ConversationalStore = ConversationalStore(),
        names: Map<String, String> = mapOf(bench to "Bench Press"),
        workoutName: String = "Chest / Triceps",
    ) = FourContextAssembler.assemble(
        AssemblyRequest(
            snapshot = snapshot,
            sessionEvents = events,
            history = history,
            profile = profile,
            conversation = conversation,
            exerciseNames = names,
            workoutName = workoutName,
        ),
    )

    @Test
    fun emptyHistoryDoesNotInventWeight() {
        val (engine, log, p) = started()
        engine.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(10, 60.0, true, LoggingMode.LIVE)))
        val ctx = assemble(engine.snapshot(session, p), log.sessionEvents(com.gymbuddy.domain.SessionId(session)))
        assertEquals(60.0, ctx.workout.currentWeightKg)
        assertTrue(ctx.historical.previousSessionsForExercise.isEmpty())
        assertNull(ctx.historical.lastWorkoutSummary)
        assertTrue(ctx.historical.knownPrs.isEmpty())
        val none = ctx.historical.aggregates.single { it.stance == EpistemicStance.NONE }
        assertFalse(none.speechAllowed)
        assertTrue(none.sourceRowIds.isEmpty())
        assertTrue(ctx.facts.none { it.id.startsWith("fact:hist:set:") })
        val histText = ctx.historical.toString().lowercase()
        assertFalse("last time" in histText)
        assertFalse("60 kilos" in histText)
    }

    @Test
    fun currentWorkoutStateFromSnapshot() {
        val (engine, log, p) = started()
        engine.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(10, 60.0, true, LoggingMode.LIVE)))
        val snap = engine.snapshot(session, p)
        val ctx = assemble(snap, log.sessionEvents(com.gymbuddy.domain.SessionId(session)))
        assertEquals(session, ctx.workout.sessionId)
        assertEquals(workout, ctx.workout.workoutId)
        assertEquals(WorkoutSessionState.RESTING, ctx.workout.sessionState)
        assertEquals(bench, ctx.workout.currentExerciseId)
        assertEquals("Bench Press", ctx.workout.currentExerciseName)
        assertEquals(60.0, ctx.workout.currentWeightKg)
        assertEquals(listOf(10), ctx.workout.completedSets.map { it.reps })
        assertEquals(1, ctx.workout.completedSets.single().setNumber)
        assertEquals(3, ctx.workout.remainingSetsPlanned)
        assertNotNull(ctx.workout.restTargetSec)
        assertEquals("Chest / Triceps", ctx.workout.workoutName)
        val knownWeight = ctx.facts.single { it.id == "fact:workout:current-weight" }
        assertEquals(EpistemicStance.KNOWN, knownWeight.stance)
        assertTrue(knownWeight.sourceRowIds.isNotEmpty())
    }

    @Test
    fun noCurrentWeightStaysNull() {
        val (engine, log, p) = started()
        val ctx = assemble(engine.snapshot(session, p), log.sessionEvents(com.gymbuddy.domain.SessionId(session)))
        assertNull(ctx.workout.currentWeightKg)
        assertTrue(ctx.facts.none { it.id == "fact:workout:current-weight" })
    }

    @Test
    fun historicalWorkoutPreservesSetIdsAndNumbers() {
        val (engine, log, p) = started()
        val histSets = listOf(
            CompletedSetSnapshot("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1", 1, 60.0, 10),
            CompletedSetSnapshot("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2", 2, 60.0, 9),
            CompletedSetSnapshot("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3", 3, 55.0, 8),
        )
        val ctx = assemble(
            engine.snapshot(session, p),
            log.sessionEvents(com.gymbuddy.domain.SessionId(session)),
            history = HistoricalStore(
                previousSessionsForExercise = listOf(
                    PreviousSessionSnapshot("2026-08-13", histSets),
                ),
            ),
        )
        assertEquals(60.0, ctx.historical.previousSessionsForExercise.single().sets[0].weightKg)
        assertEquals(listOf(1, 2, 3), ctx.historical.previousSessionsForExercise.single().sets.map { it.setNumber })
        assertEquals(histSets.map { it.setId }, ctx.historical.previousSessionsForExercise.single().sets.map { it.setId })
        assertNull(ctx.workout.currentWeightKg)
        assertTrue(ctx.facts.any { it.id == "fact:hist:set:aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1" && it.stance == EpistemicStance.KNOWN })
    }

    @Test
    fun currentAndHistoricalWeightsStaySeparate() {
        val (engine, log, p) = started()
        engine.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(10, 60.0, true, LoggingMode.LIVE)))
        val ctx = assemble(
            engine.snapshot(session, p),
            log.sessionEvents(com.gymbuddy.domain.SessionId(session)),
            history = HistoricalStore(
                previousSessionsForExercise = listOf(
                    PreviousSessionSnapshot(
                        "2026-08-13",
                        listOf(CompletedSetSnapshot("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1", 1, 55.0, 10)),
                    ),
                ),
            ),
        )
        assertEquals(60.0, ctx.workout.currentWeightKg)
        assertEquals(55.0, ctx.historical.previousSessionsForExercise.single().sets.single().weightKg)
        assertEquals(60.0, ctx.facts.single { it.id == "fact:workout:current-weight" }.value?.jsonPrimitive?.double)
        assertTrue(ctx.facts.any { it.id.startsWith("fact:hist:set:") && it.stance == EpistemicStance.KNOWN })
    }

    @Test
    fun userPreferencesCopiedNotInvented() {
        val (engine, log, p) = started()
        val ctx = assemble(
            engine.snapshot(session, p),
            log.sessionEvents(com.gymbuddy.domain.SessionId(session)),
            profile = UserProfileStore(
                userId = user,
                goals = listOf("strength"),
                experience = "intermediate",
                equipment = listOf("barbell"),
                units = WeightUnit.KG,
                primaryLanguage = "en",
                additionalLanguages = listOf("te"),
                preferences = listOf(PreferenceSnapshot("dislike_exercise:$incline", "true")),
                limitations = listOf("no_overhead_press"),
            ),
        )
        assertEquals(listOf("strength"), ctx.user.goals)
        assertEquals("intermediate", ctx.user.experience)
        assertEquals(listOf("barbell"), ctx.user.equipment)
        assertEquals("en", ctx.user.primaryLanguage)
        assertEquals(listOf("te"), ctx.user.additionalLanguages)
        assertEquals("true", ctx.user.preferences.single().value)
        assertEquals(listOf("no_overhead_press"), ctx.user.limitations)
        val empty = assemble(engine.snapshot(session, p), log.sessionEvents(com.gymbuddy.domain.SessionId(session)))
        assertTrue(empty.user.goals.isEmpty())
        assertEquals("", empty.user.experience)
        assertTrue(empty.user.preferences.isEmpty())
    }

    @Test
    fun pendingQuestionStaysStructured() {
        val (engine, log, p) = started()
        val ctx = assemble(
            engine.snapshot(session, p),
            log.sessionEvents(com.gymbuddy.domain.SessionId(session)),
            conversation = ConversationalStore(
                currentTopic = "nutrition",
                pendingClarification = PendingClarification("Replace set 2 or keep both?", listOf("replace", "keep_both")),
                lastUserUtterance = UtteranceSnapshot("How much protein should I have?", "en", "2026-08-20T16:00:00Z"),
            ),
        )
        assertEquals("nutrition", ctx.conversational.currentTopic)
        assertEquals("Replace set 2 or keep both?", ctx.conversational.pendingClarification?.question)
        assertEquals("How much protein should I have?", ctx.conversational.lastUserUtterance?.text)
        assertTrue(ctx.conversational.queuedSpeechActs.isEmpty())
    }

    @Test
    fun recentCorrectionFromCommittedEvents() {
        val (engine, log, p) = started()
        engine.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(10, 60.0, true, LoggingMode.LIVE)))
        val setId = engine.snapshot(session, p).liveSetsOnCurrent().single().setId
        engine.applyFacts(
            session, p,
            listOf(ValidatedFact.SetCorrected(setId, CorrectionFields(reps = 8), PreviousSetSnapshot(setId, 10, 60.0, 1))),
        )
        val events = log.sessionEvents(com.gymbuddy.domain.SessionId(session))
        val ctx = assemble(engine.snapshot(session, p), events)
        assertEquals(1, ctx.conversational.recentCorrections.size)
        assertEquals(setId.value, ctx.conversational.recentCorrections.single().setId)
        assertEquals(listOf("reps"), ctx.conversational.recentCorrections.single().fields)
        assertEquals(setId.value, ctx.workout.completedSets.single().setId)
        assertEquals(8, ctx.workout.completedSets.single().reps)
        assertEquals(1, events.count { it.type == CommittedEventType.SET_CORRECTED })
        assertEquals(1, events.count { it.type == CommittedEventType.SET_COMPLETED })
    }

    @Test
    fun estimatedRequiresTwoHistoricalRows() {
        val one = HistoricalStore(
            previousSessionsForExercise = listOf(
                PreviousSessionSnapshot("2026-08-06", listOf(CompletedSetSnapshot("s1", 1, 50.0, 10))),
            ),
        )
        val two = HistoricalStore(
            previousSessionsForExercise = listOf(
                PreviousSessionSnapshot("2026-08-06", listOf(CompletedSetSnapshot("s1", 1, 50.0, 10))),
                PreviousSessionSnapshot("2026-08-13", listOf(CompletedSetSnapshot("s2", 1, 70.0, 8))),
            ),
        )
        val idle = WorkoutSessionSnapshot(sessionId = session, workoutId = workout, userId = user)
        val a = assemble(idle, history = one)
        assertTrue(a.historical.aggregates.none { it.stance == EpistemicStance.ESTIMATED })
        val b = assemble(idle, history = two)
        val est = b.historical.aggregates.single { it.stance == EpistemicStance.ESTIMATED }
        assertEquals(2, est.sourceRowIds.size)
        assertEquals(60.0, est.value?.jsonPrimitive?.doubleOrNull)
        assertTrue(est.speechAllowed)
    }

    @Test
    fun inferredPriorSessionUsesStoredDatesOnly() {
        val (engine, log, p) = started()
        val ctx = assemble(
            engine.snapshot(session, p),
            log.sessionEvents(com.gymbuddy.domain.SessionId(session)),
            history = HistoricalStore(
                previousSessionsForExercise = listOf(
                    PreviousSessionSnapshot("2026-08-13", listOf(CompletedSetSnapshot("s1", 1, 55.0, 10))),
                ),
            ),
        )
        val inf = ctx.historical.aggregates.single { it.stance == EpistemicStance.INFERRED }
        assertEquals("2026-08-13", inf.value?.jsonPrimitive?.content)
        assertTrue(inf.sourceRowIds.isNotEmpty())
        assertTrue(inf.id.startsWith("fact:inferred:prior-session"))
    }

    @Test
    fun uncertainDoesNotBecomeKnownHistory() {
        val idle = WorkoutSessionSnapshot(sessionId = session, workoutId = workout, userId = user)
        val ctx = assemble(
            idle,
            history = HistoricalStore(
                uncertainFacts = listOf(
                    EpistemicFact(
                        id = "fact:uncertain:last-week-weight",
                        kind = "aggregate",
                        stance = EpistemicStance.UNCERTAIN,
                        value = JsonPrimitive(55.0),
                        sourceRowIds = emptyList(),
                        speechAllowed = false,
                    ),
                ),
            ),
            conversation = ConversationalStore(
                lastUserUtterance = UtteranceSnapshot("I think I used 55 last week.", "en", "2026-08-20T16:00:00Z"),
            ),
        )
        assertTrue(ctx.historical.previousSessionsForExercise.isEmpty())
        val u = ctx.historical.aggregates.single { it.stance == EpistemicStance.UNCERTAIN }
        assertEquals(55.0, u.value?.jsonPrimitive?.doubleOrNull)
        assertFalse(u.speechAllowed)
        assertEquals("I think I used 55 last week.", ctx.conversational.lastUserUtterance?.text)
        assertTrue(ctx.facts.none { it.stance == EpistemicStance.KNOWN && it.value == JsonPrimitive(55.0) })
    }

    @Test
    fun assemblyIsDeterministicAndReadOnly() {
        val (engine, log, p) = started()
        engine.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(10, 60.0, true, LoggingMode.LIVE)))
        val snap = engine.snapshot(session, p)
        val events = log.sessionEvents(com.gymbuddy.domain.SessionId(session))
        val req = AssemblyRequest(
            snapshot = snap,
            sessionEvents = events,
            history = HistoricalStore(
                previousSessionsForExercise = listOf(
                    PreviousSessionSnapshot("2026-08-13", listOf(CompletedSetSnapshot("s1", 2, 55.0, 9))),
                ),
            ),
            exerciseNames = mapOf(bench to "Bench Press"),
            workoutName = "Chest / Triceps",
        )
        val a = FourContextAssembler.assemble(req)
        val b = FourContextAssembler.assembleFourContexts(req)
        assertEquals(a, b)
        assertEquals(snap.phase, engine.snapshot(session, p).phase)
        assertEquals(events.size, log.sessionEvents(com.gymbuddy.domain.SessionId(session)).size)
        assertEquals(2, a.historical.previousSessionsForExercise.single().sets.single().setNumber)
    }

    @Test
    fun emptyOptionalContextStaysEmpty() {
        val ctx = assemble(WorkoutSessionSnapshot())
        assertEquals("", ctx.workout.sessionId)
        assertTrue(ctx.workout.completedSets.isEmpty())
        assertNull(ctx.workout.currentWeightKg)
        assertNull(ctx.workout.currentExerciseId)
        assertTrue(ctx.historical.previousSessionsForExercise.isEmpty())
        assertNull(ctx.conversational.pendingClarification)
        assertNull(ctx.conversational.lastUserUtterance)
        assertTrue(ctx.user.goals.isEmpty())
        assertEquals("", ctx.user.experience)
    }

    @Test
    fun beginSetExposesCurrentSetNumberFromSnapshot() {
        val (engine, log, p) = started()
        engine.beginSet(session, p)
        val ctx = assemble(engine.snapshot(session, p), log.sessionEvents(com.gymbuddy.domain.SessionId(session)))
        assertEquals(WorkoutSessionState.SET_ACTIVE, ctx.workout.sessionState)
        assertEquals(1, ctx.workout.currentSetNumber)
    }

    @Test
    fun pauseStateIsExposed() {
        val (engine, log, p) = started()
        engine.applyFacts(session, p, listOf(ValidatedFact.SetCompleted(10, 60.0, true, LoggingMode.LIVE)))
        engine.pause(session, p, 40.0)
        val ctx = assemble(engine.snapshot(session, p), log.sessionEvents(com.gymbuddy.domain.SessionId(session)))
        assertEquals(WorkoutSessionState.PAUSED, ctx.workout.sessionState)
        assertEquals(40.0, ctx.workout.restRemainingSec)
        assertEquals(1, ctx.workout.completedSets.single().setNumber)
    }
}
