package com.gymbuddy.domain.adapt

import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.catalog.CatalogTest
import com.gymbuddy.domain.engine.ApplyResult
import com.gymbuddy.domain.engine.EngineResult
import com.gymbuddy.domain.engine.InMemoryEventLog
import com.gymbuddy.domain.engine.WorkoutEngine
import com.gymbuddy.domain.engine.WorkoutPlan
import com.gymbuddy.domain.engine.WorkoutProjector
import com.gymbuddy.domain.engine.exerciseOutcome
import com.gymbuddy.domain.engine.ExerciseOutcome
import com.gymbuddy.domain.engine.PlannedExercise as EnginePlanned
import com.gymbuddy.domain.events.WorkoutEvent
import com.gymbuddy.domain.picker.PickContext
import com.gymbuddy.domain.picker.Slot
import com.gymbuddy.domain.picker.SlotRole
import com.gymbuddy.domain.picker.rankSlot
import com.gymbuddy.domain.programming.Goal
import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.programming.PlannedExercise
import com.gymbuddy.domain.programming.PlannedSession
import com.gymbuddy.domain.programming.TrainingAge
import com.gymbuddy.domain.programming.UserContext
import com.gymbuddy.domain.programming.effectiveSlot
import com.gymbuddy.domain.validation.ValidatedFact
import com.gymbuddy.domain.CommandKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdaptTest {
    private val catalog = CatalogTest.loadCatalog()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private val squatSlot = Slot("squat", SlotRole.PRIMARY, setOf("quad"))
    private val isolationSlot = Slot("chest isolation", SlotRole.ISOLATION, setOf("chest_mid"))

    private fun fullGym(): PickContext = PickContext(
        user = UserContext(Goal.HYPERTROPHY, TrainingAge.INTERMEDIATE),
        equipment = catalog.equipment.keys,
    )

    private fun planned(
        slug: String,
        hardSets: Int = 3,
        slot: Slot? = null,
        rest: Int? = null,
    ): PlannedExercise {
        val ex = catalog.bySlug(slug)!!
        return PlannedExercise(
            exercise = ex,
            hardSets = hardSets,
            prescribedRestSec = rest,
            slot = slot ?: Slot(ex.pattern, if (ex.kind == "isolation") SlotRole.ISOLATION else SlotRole.PRIMARY),
        )
    }

    @Test
    fun cookedQuadsProposeAnAdjustment() {
        val ctx = fullGym().copy(fatigue = mapOf("quad" to 1.0))
        val ranked = rankSlot(squatSlot, catalog, ctx)
        assertTrue(ranked.readiness.allCandidatesFatigued, ranked.readiness.explanation)
        assertTrue(ranked.readiness.worstTargetFatigue >= 0.7)
        val session = PlannedSession(listOf(planned("back-squat", slot = squatSlot)))
        val adj = adjustForToday(session, Readiness(muscleFatigue = mapOf("quad" to 1.0)), ctx, catalog)
        assertTrue(adj.none { it is Adjustment.AsPlanned }, adj.toString())
        assertTrue(adj.any { it is Adjustment.LowerEffort || it is Adjustment.ReduceSets || it is Adjustment.SwapPattern }, adj.toString())
        adj.filter { it !is Adjustment.AsPlanned }.forEach { assertSpeakable(it.spoken()!!) }
    }

    @Test
    fun mildlyTiredMuscleIsAsPlanned() {
        val ctx = fullGym().copy(fatigue = mapOf("quad" to 0.35))
        val session = PlannedSession(listOf(planned("back-squat", slot = squatSlot)))
        val adj = adjustForToday(session, Readiness(muscleFatigue = mapOf("quad" to 0.35)), ctx, catalog)
        assertEquals(listOf(Adjustment.AsPlanned), adj)
        assertNull(Adjustment.AsPlanned.spoken())
    }

    @Test
    fun unknownReadinessIsAsPlannedAndSilent() {
        val session = PlannedSession(listOf(planned("back-squat", slot = squatSlot)))
        val adj = adjustForToday(
            session,
            Readiness(selfReported = SelfReport.UNKNOWN),
            fullGym(),
            catalog,
        )
        assertEquals(listOf(Adjustment.AsPlanned), adj)
        assertNull(adj.single().spoken())
    }

    @Test
    fun cheaperAdjustmentsComeBeforeDropAndPrimaryIsProtected() {
        val ctx = fullGym().copy(fatigue = mapOf("quad" to 1.0, "chest_mid" to 1.0))
        val session = PlannedSession(
            listOf(
                planned("back-squat", hardSets = 3, slot = squatSlot),
                planned("leg-extension", hardSets = 2, slot = Slot("quads", SlotRole.ISOLATION, setOf("quad"))),
            ),
        )
        val adj = adjustForToday(session, Readiness(muscleFatigue = ctx.fatigue), ctx, catalog)
        val ranks = adj.map { preferenceRank(it) }
        assertEquals(ranks.sorted(), ranks, adj.toString())
        assertTrue(adj.any { it is Adjustment.LowerEffort }, adj.toString())
        assertTrue(adj.any { it is Adjustment.ReduceSets }, adj.toString())
        val drops = adj.filterIsInstance<Adjustment.DropSlot>()
        assertTrue(drops.isNotEmpty(), adj.toString())
        drops.forEach { drop ->
            val role = session.exercises[drop.slot].effectiveSlot().role
            assertTrue(role == SlotRole.ISOLATION || role == SlotRole.ACCESSORY, "dropped $role")
        }
        assertTrue(adj.none { it is Adjustment.DropSlot && session.exercises[it.slot].effectiveSlot().role == SlotRole.PRIMARY })
        adj.forEach { a -> a.spoken()?.let { assertSpeakable(it) } }
    }

    @Test
    fun oneBadNightNeverDropsASlot() {
        val session = PlannedSession(
            listOf(
                planned("back-squat", slot = squatSlot),
                planned("leg-extension", slot = Slot("quads", SlotRole.ISOLATION, setOf("quad"))),
            ),
        )
        val adj = adjustForToday(session, Readiness(sleepHours = 4.0), fullGym(), catalog)
        assertTrue(adj.none { it is Adjustment.DropSlot }, adj.toString())
        assertEquals(listOf(Adjustment.AsPlanned), adj)
    }

    @Test
    fun adjustForTodayDoesNotMutateTheSession() {
        val exercises = mutableListOf(
            planned("back-squat", slot = squatSlot),
            planned("lat-pulldown"),
        )
        val session = PlannedSession(exercises)
        val before = session.exercises.toList()
        val slugs = before.map { it.exercise.slug }
        adjustForToday(
            session,
            Readiness(muscleFatigue = mapOf("quad" to 1.0), minutesAvailable = 20, sleepHours = 4.0),
            fullGym().copy(fatigue = mapOf("quad" to 1.0)),
            catalog,
        )
        assertEquals(before, session.exercises)
        assertEquals(slugs, session.exercises.map { it.exercise.slug })
        assertEquals(2, exercises.size)
    }

    @Test
    fun nullWaitAsksHowLongAndNeverSubstitutes() {
        val session = PlannedSession(listOf(planned("cable-fly", slot = isolationSlot)))
        val r = respondToUnavailable(0, session, fullGym(), catalog, expectedWaitMinutes = null)
        assertIs<UnavailableResponse.AskHowLong>(r)
        assertEquals(AdaptCopy.ASK_HOW_LONG, r.question)
        assertTrue(r !is UnavailableResponse.Substitute)
        assertSpeakable(r.spoken())
    }

    @Test
    fun waitShorterThanRestIsWait() {
        val session = PlannedSession(listOf(planned("back-squat", slot = squatSlot, rest = 210)))
        val r = respondToUnavailable(0, session, fullGym(), catalog, expectedWaitMinutes = 2)
        val wait = assertIs<UnavailableResponse.Wait>(r)
        assertEquals(AdaptCopy.WAIT, wait.why)
        assertSpeakable(wait.why)
    }

    @Test
    fun primaryWithLaterFreeSlotDefersNotSubstitutes() {
        val session = PlannedSession(
            listOf(
                planned("back-squat", slot = squatSlot),
                planned("lat-pulldown"),
            ),
        )
        val r = respondToUnavailable(0, session, fullGym(), catalog, expectedWaitMinutes = 6)
        val defer = assertIs<UnavailableResponse.Defer>(r)
        assertEquals(1, defer.untilAfter)
        assertTrue("Lat Pulldown" in defer.why, defer.why)
        assertTrue(r !is UnavailableResponse.Substitute)
        assertSpeakable(defer.why)
    }

    @Test
    fun isolationWithLongWaitSubstitutes() {
        val session = PlannedSession(listOf(planned("cable-fly", slot = isolationSlot)))
        val r = respondToUnavailable(0, session, fullGym(), catalog, expectedWaitMinutes = 6)
        val sub = assertIs<UnavailableResponse.Substitute>(r)
        assertTrue(sub.alternative.exercise.slug != "cable-fly")
        assertSpeakable(sub.why)
    }

    @Test
    fun deferralRefusedMidSet() {
        val user = "11111111-1111-4111-8111-111111111111"
        val session = "22222222-2222-4222-8222-222222222222"
        val e = WorkoutEngine(InMemoryEventLog())
        val p = enginePlan()
        e.startSession(user, session, p)
        e.beginSet(session, p)
        val r = e.acceptDeferral(session, p, "the rack is busy")
        assertIs<EngineResult.Rejected>(r)
        assertEquals(WorkoutSessionState.SET_ACTIVE, e.snapshot(session, p).phase)
        val deferred = deferredEvent(p.exercises.first().workoutExerciseId, p.exercises.first().exerciseId)
        val applied = WorkoutProjector.apply(e.snapshot(session, p), deferred)
        assertIs<ApplyResult.Rejected>(applied)
        assertFalse(maySpeakNow(WorkoutSessionState.SET_ACTIVE))
        assertTrue(maySpeakNow(WorkoutSessionState.RESTING))
        val queued = queueIfSetActive(WorkoutSessionState.SET_ACTIVE, AdaptCopy.ASK_HOW_LONG)
        assertEquals(DrainOn.RESTING, queued.drainOn)
    }

    @Test
    fun thirdDeferralBecomesSkippedWithReason() {
        val user = "11111111-1111-4111-8111-111111111111"
        val sessionId = "22222222-2222-4222-8222-222222222222"
        val log = InMemoryEventLog()
        val e = WorkoutEngine(log)
        val p = enginePlan(three = true)
        e.startSession(user, sessionId, p)
        val reason = "the rack is busy"
        val first = e.acceptDeferral(sessionId, p, reason)
        assertIs<EngineResult.Committed>(first)
        assertTrue(first.events.any { it.type == CommittedEventType.EXERCISE_DEFERRED })
        skipCurrent(e, sessionId)
        val second = e.acceptDeferral(sessionId, e.snapshot(sessionId).plan ?: p, reason)
        assertIs<EngineResult.Committed>(second)
        assertTrue(second.events.any { it.type == CommittedEventType.EXERCISE_DEFERRED })
        skipCurrent(e, sessionId)
        val third = e.acceptDeferral(sessionId, e.snapshot(sessionId).plan ?: p, reason)
        assertIs<EngineResult.Committed>(third)
        val skip = third.events.single { it.type == CommittedEventType.EXERCISE_SKIPPED }
        assertEquals(reason, skip.payload["reason"]!!.jsonPrimitive.content)
        val snap = e.snapshot(sessionId)
        val we1 = p.exercises.first().workoutExerciseId
        assertEquals(ExerciseOutcome.SKIPPED, exerciseOutcome(snap, we1))
        val skipped = snap.plan!!.exercises.first { it.workoutExerciseId == we1 }
        assertTrue(skipped.skipped)
        assertEquals(reason, skipped.skippedReason)
    }

    @Test
    fun deferredThenCompletedIsCompleted() {
        val user = "11111111-1111-4111-8111-111111111111"
        val sessionId = "22222222-2222-4222-8222-222222222222"
        val e = WorkoutEngine(InMemoryEventLog())
        val p = enginePlan(plannedSets = 1)
        e.startSession(user, sessionId, p)
        val we1 = p.exercises[0].workoutExerciseId
        val deferred = e.acceptDeferral(sessionId, p, "the rack is busy")
        assertIs<EngineResult.Committed>(deferred)
        val mid = e.snapshot(sessionId)
        assertEquals(ExerciseOutcome.DEFERRED, exerciseOutcome(mid, we1))
        val live = mid.plan ?: p
        e.applyFacts(sessionId, live, listOf(ValidatedFact.SetCompleted(8, 60.0, true, LoggingMode.LIVE)))
        val afterNext = e.snapshot(sessionId)
        e.applyFacts(sessionId, afterNext.plan ?: live, listOf(ValidatedFact.SetCompleted(8, 60.0, true, LoggingMode.LIVE)))
        val done = e.snapshot(sessionId)
        assertEquals(ExerciseOutcome.COMPLETED, exerciseOutcome(done, we1))
        assertTrue(done.completedSets.any { it.workoutExerciseId == we1 && !it.deleted })
    }

    @Test
    fun everyWhyAndQuestionIsPlainLanguage() {
        val spoken = mutableListOf<String>()
        spoken += AdaptCopy.ASK_HOW_LONG
        spoken += AdaptCopy.LOWER_EFFORT
        spoken += AdaptCopy.LOWER_EFFORT_SHORT_NIGHT
        spoken += AdaptCopy.WORK_IN
        spoken += AdaptCopy.SKIP_NO_ALTERNATIVE
        spoken += AdaptCopy.SKIP_MISSING
        spoken += AdaptCopy.DROP_EXTRA
        spoken += AdaptCopy.DROP_TIRED
        spoken += AdaptCopy.FRESH_ENOUGH
        spoken += AdaptCopy.reduceSets(2, 3)
        spoken += AdaptCopy.reduceSets(1, 2)
        spoken += AdaptCopy.shorten(20)
        spoken += AdaptCopy.WAIT
        spoken += AdaptCopy.defer("Lat Pulldown")
        spoken += AdaptCopy.substitute("Dumbbell Fly")
        spoken += AdaptCopy.swapToExercise("quads", "Deadlift")
        spoken += AdaptCopy.cookedSlot("quads")
        spoken += AdaptCopy.mildFatigue("quads")
        val ctx = fullGym().copy(fatigue = mapOf("quad" to 1.0, "chest_mid" to 1.0))
        val session = PlannedSession(
            listOf(
                planned("back-squat", slot = squatSlot),
                planned("cable-fly", slot = isolationSlot),
                planned("lat-pulldown"),
            ),
        )
        adjustForToday(
            session,
            Readiness(muscleFatigue = ctx.fatigue, minutesAvailable = 20, sleepHours = 5.0),
            ctx,
            catalog,
        ).forEach { it.spoken()?.let(spoken::add) }
        listOf<Int?>(null, 1, 2, 6).forEach { wait ->
            spoken += respondToUnavailable(0, session, ctx, catalog, wait).spoken()
            spoken += respondToUnavailable(1, session, ctx, catalog, wait).spoken()
        }
        spoken.forEach { assertSpeakable(it) }
        assertEquals("How long do they look?", AdaptCopy.ASK_HOW_LONG)
        assertEquals("That's less than your rest anyway. We'll wait.", AdaptCopy.WAIT)
        val exerciseNames = catalog.exercises.map { it.name }.toSet()
        adjustForToday(
            session,
            Readiness(muscleFatigue = ctx.fatigue),
            ctx,
            catalog,
        ).filterIsInstance<Adjustment.SwapPattern>().forEach { swap ->
            assertTrue(
                exerciseNames.any { it in swap.why },
                "swap must name an exercise, not a pattern: ${swap.why}",
            )
            assertFalse(
                swap.why.endsWith("to ${swap.to}."),
                "spoke pattern id ${swap.to}: ${swap.why}",
            )
        }
    }

    @Test
    fun exerciseDeferredRoundTripsCodecAndProjector() {
        val golden = loadGolden()
        assertEquals("32-adaptation.event.exercise-deferred-round-trip", golden["id"]!!.jsonPrimitive.content)
        assertEquals(1, golden["schemaVersion"]!!.jsonPrimitive.intOrNull)
        val rawEvent = golden["input"]!!.jsonObject["event"]!!.jsonObject
        val event = json.decodeFromJsonElement(WorkoutEvent.serializer(), rawEvent)
        assertEquals(CommittedEventType.EXERCISE_DEFERRED, event.type)
        assertEquals(1, event.schemaVersion)
        val encoded = json.encodeToString(WorkoutEvent.serializer(), event)
        val decoded = json.decodeFromString(WorkoutEvent.serializer(), encoded)
        assertEquals(event.type, decoded.type)
        assertEquals(event.payload, decoded.payload)
        assertEquals(event.schemaVersion, decoded.schemaVersion)

        val user = event.userId
        val session = event.sessionId!!
        val we1 = event.payload["workoutExerciseId"]!!.jsonPrimitive.content
        val we2 = "55555555-5555-4555-8555-555555555556"
        val p = WorkoutPlan(
            "33333333-3333-4333-8333-333333333333",
            listOf(
                EnginePlanned(we1, event.payload["exerciseId"]!!.jsonPrimitive.content, 3),
                EnginePlanned(we2, "44444444-4444-4444-8444-444444444445", 3),
            ),
        )
        val log = InMemoryEventLog()
        val engine = WorkoutEngine(log)
        engine.startSession(user, session, p)
        val before = engine.snapshot(session, p)
        assertEquals(we1, before.currentExercise?.workoutExerciseId)
        val applied = WorkoutProjector.apply(before, event)
        val ok = assertIs<ApplyResult.Ok>(applied)
        assertEquals(we2, ok.snapshot.plan!!.exercises.first().workoutExerciseId)
        assertEquals(we1, ok.snapshot.plan!!.exercises[1].workoutExerciseId)
        assertEquals(1, ok.snapshot.plan!!.exercises[1].deferralCount)
        assertEquals(
            event.payload["reason"]!!.jsonPrimitive.content,
            ok.snapshot.plan!!.exercises[1].deferralReason,
        )
        val folded = WorkoutProjector.fold(listOf(event), initial = before, plan = before.plan)
        assertEquals(we2, folded.plan!!.exercises.first().workoutExerciseId)
        assertEquals(we1, folded.plan!!.exercises[1].workoutExerciseId)
        assertEquals(we2, folded.currentExercise?.workoutExerciseId)

        val expect = golden["expect"]!!.jsonObject
        assertEquals("EXERCISE_DEFERRED", expect["eventType"]!!.jsonPrimitive.content)
        assertEquals(true, expect["foldRestores"]!!.jsonPrimitive.booleanOrNull)
    }

    private fun preferenceRank(adj: Adjustment): Int = when (adj) {
        Adjustment.AsPlanned -> 0
        is Adjustment.LowerEffort -> 1
        is Adjustment.ReduceSets -> 2
        is Adjustment.SwapPattern -> 3
        is Adjustment.DropSlot -> 4
        is Adjustment.ShortenSession -> 5
    }

    private fun enginePlan(plannedSets: Int = 3, three: Boolean = false): WorkoutPlan {
        val we1 = "55555555-5555-4555-8555-555555555555"
        val we2 = "55555555-5555-4555-8555-555555555556"
        val we3 = "55555555-5555-4555-8555-555555555557"
        val exercises = mutableListOf(
            EnginePlanned(we1, "44444444-4444-4444-8444-444444444444", plannedSets),
            EnginePlanned(we2, "44444444-4444-4444-8444-444444444445", plannedSets),
        )
        if (three) {
            exercises += EnginePlanned(we3, "44444444-4444-4444-8444-444444444446", plannedSets)
        }
        return WorkoutPlan("33333333-3333-4333-8333-333333333333", exercises)
    }

    private fun skipCurrent(engine: WorkoutEngine, sessionId: String) {
        val live = engine.snapshot(sessionId).plan!!
        val r = engine.applyFacts(sessionId, live, listOf(ValidatedFact.Command(CommandKind.SKIP)))
        assertIs<EngineResult.Committed>(r)
    }

    private fun deferredEvent(workoutExerciseId: String, exerciseId: String): WorkoutEvent =
        WorkoutEvent(
            eventId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            sessionId = "22222222-2222-4222-8222-222222222222",
            userId = "11111111-1111-4111-8111-111111111111",
            occurredAt = "2026-09-01T12:00:00Z",
            recordedAt = "2026-09-01T12:00:00Z",
            source = EventSource.SYSTEM,
            schemaVersion = 1,
            type = CommittedEventType.EXERCISE_DEFERRED,
            payload = buildJsonObject {
                put("workoutExerciseId", workoutExerciseId)
                put("exerciseId", exerciseId)
                put("fromPosition", 0)
                put("reason", "the rack is busy")
            },
        )

    private fun loadGolden(): JsonObject {
        val cwd = File("").absoluteFile
        val file = listOf(
            File(cwd, "tests/golden/32-adaptation.event.exercise-deferred-round-trip.json"),
            File(cwd, "../tests/golden/32-adaptation.event.exercise-deferred-round-trip.json"),
            File(cwd, "../../tests/golden/32-adaptation.event.exercise-deferred-round-trip.json"),
        ).first { it.isFile }
        return json.parseToJsonElement(file.readText()).jsonObject
    }
}

internal fun assertSpeakable(text: String) {
    val banned = listOf(
        "extraRir", "expectedWaitMinutes", "allCandidatesFatigued", "minutesAvailable",
        "selfReported", "hardSets", "workoutExerciseId", "fromPosition", "deferralCount",
        "FATIGUE_COOKED", "WEIGHT_FRESHNESS", "DEFER_CAP", "SlotIndex", "muscleFatigue",
        "worstTargetFatigue", "prescribedRestSec", "defaultRestSec", "targetMuscles",
        "skillDemand", "setCredit", "PickContext", "SlotRole",
    )
    banned.forEach { token ->
        assertFalse(token in text, "field name $token in: $text")
    }
    assertFalse('_' in text, "underscore in: $text")
    val lower = text.lowercase()
    assertFalse("kit" in lower, "said kit: $text")
    assertFalse("hard set" in lower, "said hard set: $text")
    assertFalse("movements" in lower, "said movements: $text")
    val covered = Regex(
        """\d+(?:\.\d+)?(?:\s+to\s+\d+(?:\.\d+)?)?\s+(?:hard sets|hard set|sets|set|seconds|second|minutes|minute|hours|hour|reps|times|time|percent)""",
        RegexOption.IGNORE_CASE,
    )
    val spans = covered.findAll(text).flatMap { it.range.toList() }.toSet()
    Regex("""\d+(?:\.\d+)?""").findAll(text).forEach { m ->
        assertTrue(
            m.range.any { it in spans },
            "number '${m.value}' has no unit in: $text",
        )
    }
    val timeUnits = listOf("minutes", "minute", "seconds", "second", "hours", "hour")
    text.split(Regex("""(?<=[.!?])\s+""")).forEach { sentence ->
        val hits = timeUnits.filter { unit ->
            Regex("""\b$unit\b""", RegexOption.IGNORE_CASE).containsMatchIn(sentence)
        }
        assertTrue(
            hits.size <= 1,
            "two time units (${hits.joinToString()}) in one sentence: $sentence",
        )
    }
}
