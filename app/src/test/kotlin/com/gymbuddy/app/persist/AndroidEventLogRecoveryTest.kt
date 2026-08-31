package com.gymbuddy.app.persist

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gymbuddy.domain.CommandKind
import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.engine.EngineResult
import com.gymbuddy.domain.engine.WorkoutEngine
import com.gymbuddy.domain.engine.WorkoutProjector
import com.gymbuddy.domain.validation.HintOrdinal
import com.gymbuddy.domain.validation.ValidatedFact
import com.gymbuddy.persist.PersistFixtures
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidEventLogRecoveryTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun recoverAfter(
        name: String = "rec-${System.nanoTime()}.db",
        act: (WorkoutEngine) -> Unit,
    ): Pair<com.gymbuddy.domain.engine.WorkoutSessionSnapshot, List<com.gymbuddy.domain.events.WorkoutEvent>> {
        val p = EventLogHarness.plan()
        val log1 = AndroidEventLog(ctx, name)
        val e1 = WorkoutEngine(log1)
        act(e1)
        val before = e1.snapshot(EventLogHarness.session, p)
        val beforeEvents = log1.load(SessionId(EventLogHarness.session))
        log1.close()

        val log2 = AndroidEventLog(ctx, name)
        val events = log2.load(SessionId(EventLogHarness.session))
        val folded = WorkoutProjector.fold(events, plan = p)
        assertEquals(beforeEvents.size, events.size)
        assertEquals(EventLogHarness.typeSeq(beforeEvents), EventLogHarness.typeSeq(events))
        assertEquals(EventLogHarness.snapKey(before), EventLogHarness.snapKey(folded))
        val e2 = WorkoutEngine(log2)
        val recovered = e2.recover(EventLogHarness.session, p)
        assertTrue(recovered is EngineResult.Committed)
        assertTrue(log2.load(SessionId(EventLogHarness.session)).any { it.type == CommittedEventType.SESSION_REHYDRATED })
        assertEquals(1, log2.load(SessionId(EventLogHarness.session)).count { it.type == CommittedEventType.WORKOUT_STARTED })
        log2.close()
        return folded to events
    }

    @Test
    fun processKillReplaysEventLogNotSnapshotBlob() {
        val name = "blob-${System.nanoTime()}.db"
        val log1 = AndroidEventLog(ctx, name)
        val e1 = WorkoutEngine(log1)
        val p = EventLogHarness.start(e1)
        e1.applyFacts(EventLogHarness.session, p, listOf(ValidatedFact.SetCompleted(10, 60.0, true, LoggingMode.LIVE)))
        e1.pause(EventLogHarness.session, p, 40.0)
        val before = e1.snapshot(EventLogHarness.session, p)
        log1.close()

        val log2 = AndroidEventLog(ctx, name)
        val tables = log2.sqlite().rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table'",
            null,
        ).use { c ->
            val names = mutableSetOf<String>()
            while (c.moveToNext()) names += c.getString(0)
            names
        }
        assertTrue("workout_session_snapshot" !in tables)
        assertTrue("event_log" in tables)
        val events = log2.load(SessionId(EventLogHarness.session))
        val folded = WorkoutProjector.fold(events, plan = p)
        assertEquals(before.phase, folded.phase)
        assertEquals(WorkoutSessionState.PAUSED, folded.phase)
        assertEquals(before.liveSetsOnCurrent().map { it.reps }, folded.liveSetsOnCurrent().map { it.reps })
        val e2 = WorkoutEngine(log2)
        val recovered = e2.recover(EventLogHarness.session, p)
        assertTrue(recovered is EngineResult.Committed)
        assertEquals(WorkoutSessionState.PAUSED, e2.snapshot(EventLogHarness.session, p).phase)
        log2.close()
    }

    @Test
    fun recoversCurrentWorkoutExerciseAndSet() {
        val (snap, events) = recoverAfter { e ->
            val p = EventLogHarness.start(e)
            e.beginSet(EventLogHarness.session, p)
        }
        assertEquals(WorkoutSessionState.SET_ACTIVE, snap.phase)
        assertEquals(EventLogHarness.exercise, snap.currentExercise?.exerciseId)
        assertTrue(events.any { it.type == CommittedEventType.SET_STARTED })
    }

    @Test
    fun recoversCompletedSetsAndRest() {
        val (snap, events) = recoverAfter { e ->
            val p = EventLogHarness.start(e)
            e.applyFacts(EventLogHarness.session, p, listOf(EventLogHarness.liveSet(10)))
        }
        assertEquals(WorkoutSessionState.RESTING, snap.phase)
        assertEquals(listOf(10), snap.liveSetsOnCurrent().map { it.reps })
        assertTrue(events.any { it.type == CommittedEventType.REST_STARTED })
        assertTrue(events.any { it.type == CommittedEventType.SET_COMPLETED })
    }

    @Test
    fun recoversPauseResume() {
        val (snap, events) = recoverAfter { e ->
            val p = EventLogHarness.start(e)
            e.applyFacts(EventLogHarness.session, p, listOf(EventLogHarness.liveSet(10)))
            e.pause(EventLogHarness.session, p, 40.0)
            e.resume(EventLogHarness.session, p)
        }
        assertEquals(WorkoutSessionState.RESTING, snap.phase)
        assertTrue(events.any { it.type == CommittedEventType.WORKOUT_PAUSED })
        assertTrue(events.any { it.type == CommittedEventType.WORKOUT_RESUMED })
        assertEquals(1, events.count { it.type == CommittedEventType.WORKOUT_STARTED })
    }

    @Test
    fun recoversCorrectionAppendOnly() {
        val (snap, events) = recoverAfter { e ->
            val p = EventLogHarness.start(e)
            e.applyFacts(EventLogHarness.session, p, listOf(EventLogHarness.liveSet(10)))
            EventLogHarness.correctionOfFirst(e, p, 9)
        }
        assertEquals(listOf(9), snap.liveSetsOnCurrent().map { it.reps })
        assertEquals(1, events.count { it.type == CommittedEventType.SET_COMPLETED })
        assertEquals(1, events.count { it.type == CommittedEventType.SET_CORRECTED })
        val completed = events.first { it.type == CommittedEventType.SET_COMPLETED }
        val corrected = events.first { it.type == CommittedEventType.SET_CORRECTED }
        assertEquals(completed.payload["setId"], corrected.payload["setId"])
        assertEquals(10, completed.payload["reps"]!!.jsonPrimitive.int)
    }

    @Test
    fun recoversDeletionWithoutErasingHistory() {
        val (snap, events) = recoverAfter { e ->
            val p = EventLogHarness.start(e)
            e.applyFacts(EventLogHarness.session, p, listOf(EventLogHarness.liveSet(10)))
            EventLogHarness.deleteFirst(e, p)
        }
        assertTrue(snap.liveSetsOnCurrent().isEmpty())
        assertEquals(1, snap.completedSets.count { it.deleted })
        assertEquals(1, events.count { it.type == CommittedEventType.SET_DELETED })
        assertEquals(1, events.count { it.type == CommittedEventType.SET_COMPLETED })
    }

    @Test
    fun recoversSkip() {
        val (snap, events) = recoverAfter { e ->
            val p = EventLogHarness.start(e)
            e.applyFacts(EventLogHarness.session, p, listOf(ValidatedFact.Command(CommandKind.SKIP)))
        }
        assertEquals(EventLogHarness.exercise2, snap.currentExercise?.exerciseId)
        assertTrue(events.any { it.type == CommittedEventType.EXERCISE_SKIPPED })
    }

    @Test
    fun recoversExtraSet() {
        val (snap, events) = recoverAfter { e ->
            val p = EventLogHarness.start(e)
            e.applyFacts(EventLogHarness.session, p, listOf(ValidatedFact.Command(CommandKind.EXTRA_SET)))
        }
        assertEquals(5, snap.currentExercise?.plannedSets)
        assertTrue(events.any { it.type == CommittedEventType.PLANNED_SETS_CHANGED })
    }

    @Test
    fun recoversCatchUp() {
        val (snap, events) = recoverAfter { e ->
            val p = EventLogHarness.start(e)
            e.applyFacts(
                EventLogHarness.session, p,
                listOf(
                    EventLogHarness.catchUp(12, 40.0, HintOrdinal.FIRST),
                    EventLogHarness.catchUp(10, 40.0, HintOrdinal.SECOND),
                    EventLogHarness.catchUp(8, 40.0, HintOrdinal.THIS),
                ),
            )
        }
        assertEquals(listOf(1, 2, 3), snap.liveSetsOnCurrent().map { it.setNumber })
        assertEquals(listOf(12, 10, 8), snap.liveSetsOnCurrent().map { it.reps })
        assertEquals(WorkoutSessionState.RESTING, snap.phase)
        assertEquals(3, events.count { it.type == CommittedEventType.SET_COMPLETED })
    }
}
