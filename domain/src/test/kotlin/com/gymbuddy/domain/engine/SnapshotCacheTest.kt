package com.gymbuddy.domain.engine

import com.gymbuddy.domain.CommandKind
import com.gymbuddy.domain.EventId
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.UserId
import com.gymbuddy.domain.events.WorkoutEvent
import com.gymbuddy.domain.persist.AppendAck
import com.gymbuddy.domain.persist.EventLog
import com.gymbuddy.domain.validation.ValidatedFact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnapshotCacheTest {
    private val user = "11111111-1111-4111-8111-111111111111"
    private val session = "22222222-2222-4222-8222-222222222222"
    private val workout = "33333333-3333-4333-8333-333333333333"
    private val bench = "44444444-4444-4444-8444-444444444444"
    private val incline = "44444444-4444-4444-8444-444444444445"
    private val we1 = "55555555-5555-4555-8555-555555555555"
    private val we2 = "55555555-5555-4555-8555-555555555556"

    private fun plan() = WorkoutPlan(
        workout,
        listOf(PlannedExercise(we1, bench, 4), PlannedExercise(we2, incline, 3)),
    )

    private fun live(reps: Int) =
        ValidatedFact.SetCompleted(reps, 60.0, true, LoggingMode.LIVE)

    @Test
    fun snapshotDoesNotRereadLogAfterCommit() {
        val counting = CountingLog()
        val engine = WorkoutEngine(counting)
        val p = plan()
        engine.startSession(user, session, p)
        val readsAfterStart = counting.reads
        assertTrue(readsAfterStart >= 1)
        engine.snapshot(session, p)
        engine.snapshot(session, p)
        assertEquals(readsAfterStart, counting.reads)
        engine.applyFacts(session, p, listOf(live(10)))
        engine.snapshot(session, p)
        assertEquals(readsAfterStart, counting.reads)
    }

    @Test
    fun incrementalMatchesFullFold() {
        val log = InMemoryEventLog()
        val engine = WorkoutEngine(log)
        val p = plan()
        engine.startSession(user, session, p)
        engine.applyFacts(session, p, listOf(live(10)))
        engine.applyFacts(session, p, listOf(live(8)))
        engine.applyFacts(session, p, listOf(ValidatedFact.Command(CommandKind.EXTRA_SET)))
        engine.pause(session, p, 40.0)
        engine.resume(session, p)
        val cached = engine.snapshot(session, p)
        val folded = WorkoutProjector.fold(log.sessionEvents(SessionId(session)), plan = p)
        assertEquals(folded, cached)
    }

    @Test
    fun extraSetStaysOnCachedPlan() {
        val log = InMemoryEventLog()
        val engine = WorkoutEngine(log)
        val p = plan()
        engine.startSession(user, session, p)
        engine.applyFacts(session, p, listOf(ValidatedFact.Command(CommandKind.EXTRA_SET)))
        val cached = engine.snapshot(session, p)
        val folded = WorkoutProjector.fold(log.sessionEvents(SessionId(session)), plan = p)
        assertEquals(5, cached.currentExercise?.plannedSets)
        assertEquals(folded.currentExercise?.plannedSets, cached.currentExercise?.plannedSets)
    }

    @Test
    fun newEngineReloadsFromLog() {
        val log = InMemoryEventLog()
        val e1 = WorkoutEngine(log)
        val p = plan()
        e1.startSession(user, session, p)
        e1.applyFacts(session, p, listOf(live(10)))
        val liveSnap = e1.snapshot(session, p)
        val e2 = WorkoutEngine(log)
        val reloaded = e2.snapshot(session, p)
        assertEquals(liveSnap.phase, reloaded.phase)
        assertEquals(liveSnap.liveSetsOnCurrent().map { it.reps }, reloaded.liveSetsOnCurrent().map { it.reps })
        assertEquals(liveSnap.localEpoch, reloaded.localEpoch)
        assertEquals(liveSnap.appliedEventIds, reloaded.appliedEventIds)
    }

    private class CountingLog : EventLog {
        val inner = InMemoryEventLog()
        var reads = 0
        override fun append(event: WorkoutEvent): AppendAck = inner.append(event)
        override fun find(userId: UserId, eventId: EventId) = inner.find(userId, eventId)
        override fun sessionEvents(sessionId: SessionId): List<WorkoutEvent> {
            reads++
            return inner.sessionEvents(sessionId)
        }
    }
}
