package com.gymbuddy.persist

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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomRecoveryTest {

    private fun recoverAfter(act: (WorkoutEngine) -> Unit): Pair<com.gymbuddy.domain.engine.WorkoutSessionSnapshot, List<com.gymbuddy.domain.events.WorkoutEvent>> {
        val file = File.createTempFile("gymbuddy-recover", ".db")
        file.deleteOnExit()
        val db1 = GymBuddyDatabase.file(file.absolutePath)
        val log1 = RoomEventLog(db1)
        val e1 = WorkoutEngine(log1)
        act(e1)
        val p = PersistTestSupport.plan()
        val before = e1.snapshot(PersistTestSupport.session, p)
        val beforeEvents = log1.load(SessionId(PersistTestSupport.session))
        db1.close()

        val db2 = GymBuddyDatabase.file(file.absolutePath)
        val log2 = RoomEventLog(db2)
        val events = log2.load(SessionId(PersistTestSupport.session))
        val folded = WorkoutProjector.fold(events, plan = p)
        assertEquals(beforeEvents.size, events.size)
        assertEquals(PersistTestSupport.typeSeq(beforeEvents), PersistTestSupport.typeSeq(events))
        assertEquals(PersistTestSupport.snapKey(before), PersistTestSupport.snapKey(folded))
        val e2 = WorkoutEngine(log2)
        val recovered = e2.recover(PersistTestSupport.session, p)
        assertTrue(recovered is EngineResult.Committed)
        assertTrue(log2.load(SessionId(PersistTestSupport.session)).any { it.type == CommittedEventType.SESSION_REHYDRATED })
        assertEquals(1, log2.load(SessionId(PersistTestSupport.session)).count { it.type == CommittedEventType.WORKOUT_STARTED })
        db2.close()
        return folded to events
    }

    @Test
    fun processKillReplaysEventLogNotSnapshotBlob() {
        val file = File.createTempFile("gymbuddy-recover-blob", ".db")
        file.deleteOnExit()
        val db1 = GymBuddyDatabase.file(file.absolutePath)
        val log1 = RoomEventLog(db1)
        val e1 = WorkoutEngine(log1)
        val p = PersistTestSupport.start(e1)
        e1.applyFacts(PersistTestSupport.session, p, listOf(ValidatedFact.SetCompleted(10, 60.0, true, LoggingMode.LIVE)))
        e1.pause(PersistTestSupport.session, p, 40.0)
        val before = e1.snapshot(PersistTestSupport.session, p)
        db1.close()

        val db2 = GymBuddyDatabase.file(file.absolutePath)
        db2.connection.createStatement().use { st ->
            st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'").use { rs ->
                val names = mutableSetOf<String>()
                while (rs.next()) names += rs.getString(1)
                assertTrue("workout_session_snapshot" !in names)
                assertTrue("event_log" in names)
            }
        }
        val log2 = RoomEventLog(db2)
        val events = log2.load(SessionId(PersistTestSupport.session))
        val folded = WorkoutProjector.fold(events, plan = p)
        assertEquals(before.phase, folded.phase)
        assertEquals(WorkoutSessionState.PAUSED, folded.phase)
        assertEquals(before.liveSetsOnCurrent().map { it.reps }, folded.liveSetsOnCurrent().map { it.reps })
        assertEquals(1, folded.liveSetsOnCurrent().size)
        val e2 = WorkoutEngine(log2)
        val recovered = e2.recover(PersistTestSupport.session, p)
        assertTrue(recovered is EngineResult.Committed)
        assertEquals(WorkoutSessionState.PAUSED, e2.snapshot(PersistTestSupport.session, p).phase)
        db2.close()
    }

    @Test
    fun recoversCurrentWorkoutExerciseAndSet() {
        val (snap, events) = recoverAfter { e ->
            val p = PersistTestSupport.start(e)
            e.beginSet(PersistTestSupport.session, p)
        }
        assertEquals(WorkoutSessionState.SET_ACTIVE, snap.phase)
        assertEquals(PersistTestSupport.exercise, snap.currentExercise?.exerciseId)
        assertTrue(events.any { it.type == CommittedEventType.SET_STARTED })
    }

    @Test
    fun recoversCompletedSetsAndRest() {
        val (snap, events) = recoverAfter { e ->
            val p = PersistTestSupport.start(e)
            e.applyFacts(PersistTestSupport.session, p, listOf(PersistTestSupport.liveSet(10)))
        }
        assertEquals(WorkoutSessionState.RESTING, snap.phase)
        assertEquals(listOf(10), snap.liveSetsOnCurrent().map { it.reps })
        assertTrue(events.any { it.type == CommittedEventType.REST_STARTED })
        assertTrue(events.any { it.type == CommittedEventType.SET_COMPLETED })
    }

    @Test
    fun recoversPauseResume() {
        val (snap, events) = recoverAfter { e ->
            val p = PersistTestSupport.start(e)
            e.applyFacts(PersistTestSupport.session, p, listOf(PersistTestSupport.liveSet(10)))
            e.pause(PersistTestSupport.session, p, 40.0)
            e.resume(PersistTestSupport.session, p)
        }
        assertEquals(WorkoutSessionState.RESTING, snap.phase)
        assertTrue(events.any { it.type == CommittedEventType.WORKOUT_PAUSED })
        assertTrue(events.any { it.type == CommittedEventType.WORKOUT_RESUMED })
        assertEquals(1, events.count { it.type == CommittedEventType.WORKOUT_STARTED })
    }

    @Test
    fun recoversCorrectionAppendOnly() {
        val (snap, events) = recoverAfter { e ->
            val p = PersistTestSupport.start(e)
            e.applyFacts(PersistTestSupport.session, p, listOf(PersistTestSupport.liveSet(10)))
            PersistTestSupport.correctionOfFirst(e, p, 9)
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
            val p = PersistTestSupport.start(e)
            e.applyFacts(PersistTestSupport.session, p, listOf(PersistTestSupport.liveSet(10)))
            PersistTestSupport.deleteFirst(e, p)
        }
        assertTrue(snap.liveSetsOnCurrent().isEmpty())
        assertEquals(1, snap.completedSets.count { it.deleted })
        assertEquals(1, events.count { it.type == CommittedEventType.SET_DELETED })
        assertEquals(1, events.count { it.type == CommittedEventType.SET_COMPLETED })
    }

    @Test
    fun recoversSkip() {
        val (snap, events) = recoverAfter { e ->
            val p = PersistTestSupport.start(e)
            e.applyFacts(PersistTestSupport.session, p, listOf(ValidatedFact.Command(CommandKind.SKIP)))
        }
        assertEquals(PersistTestSupport.exercise2, snap.currentExercise?.exerciseId)
        assertTrue(events.any { it.type == CommittedEventType.EXERCISE_SKIPPED })
    }

    @Test
    fun recoversExtraSet() {
        val (snap, events) = recoverAfter { e ->
            val p = PersistTestSupport.start(e)
            e.applyFacts(PersistTestSupport.session, p, listOf(ValidatedFact.Command(CommandKind.EXTRA_SET)))
        }
        assertEquals(5, snap.currentExercise?.plannedSets)
        assertTrue(events.any { it.type == CommittedEventType.PLANNED_SETS_CHANGED })
    }

    @Test
    fun recoversCatchUp() {
        val (snap, events) = recoverAfter { e ->
            val p = PersistTestSupport.start(e)
            e.applyFacts(
                PersistTestSupport.session, p,
                listOf(
                    PersistTestSupport.catchUp(12, 40.0, HintOrdinal.FIRST),
                    PersistTestSupport.catchUp(10, 40.0, HintOrdinal.SECOND),
                    PersistTestSupport.catchUp(8, 40.0, HintOrdinal.THIS),
                ),
            )
        }
        assertEquals(listOf(1, 2, 3), snap.liveSetsOnCurrent().map { it.setNumber })
        assertEquals(listOf(12, 10, 8), snap.liveSetsOnCurrent().map { it.reps })
        assertEquals(WorkoutSessionState.RESTING, snap.phase)
        assertEquals(3, events.count { it.type == CommittedEventType.SET_COMPLETED })
    }
}
