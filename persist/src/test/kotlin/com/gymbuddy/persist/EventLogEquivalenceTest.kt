package com.gymbuddy.persist

import com.gymbuddy.domain.CommandKind
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.engine.InMemoryEventLog
import com.gymbuddy.domain.engine.WorkoutEngine
import com.gymbuddy.domain.engine.WorkoutProjector
import com.gymbuddy.domain.validation.HintOrdinal
import com.gymbuddy.domain.validation.ValidatedFact
import kotlin.test.Test
import kotlin.test.assertEquals

class EventLogEquivalenceTest {

    private fun assertEquivalent(act: (WorkoutEngine) -> Unit) {
        val memLog = InMemoryEventLog()
        act(WorkoutEngine(memLog))
        val memEvents = memLog.sessionEvents(SessionId(PersistTestSupport.session))
        val p = PersistTestSupport.plan()
        val memSnap = WorkoutProjector.fold(memEvents, plan = p)

        val db = GymBuddyDatabase.inMemory()
        try {
            val roomLog = RoomEventLog(db)
            act(WorkoutEngine(roomLog))
            val roomEvents = roomLog.load(SessionId(PersistTestSupport.session))
            val roomSnap = WorkoutProjector.fold(roomEvents, plan = p)

            assertEquals(memEvents.size, roomEvents.size)
            assertEquals(memLog.latestVersion(SessionId(PersistTestSupport.session)), roomLog.latestVersion(SessionId(PersistTestSupport.session)))
            assertEquals(PersistTestSupport.typeSeq(memEvents), PersistTestSupport.typeSeq(roomEvents))
            assertEquals(memEvents.map { it.source }, roomEvents.map { it.source })
            assertEquals(PersistTestSupport.snapKey(memSnap), PersistTestSupport.snapKey(roomSnap))
            assertEquals(
                memSnap.liveSetsOnCurrent().map { it.setNumber to it.reps },
                roomSnap.liveSetsOnCurrent().map { it.setNumber to it.reps },
            )
            val foldedAgain = WorkoutProjector.fold(roomEvents, plan = p)
            assertEquals(PersistTestSupport.snapKey(roomSnap), PersistTestSupport.snapKey(foldedAgain))
        } finally {
            db.close()
        }
    }

    @Test
    fun catchUpCorrectionPauseResumeExtraSetSkip() {
        assertEquivalent { engine ->
            val p = PersistTestSupport.start(engine)
            engine.applyFacts(
                PersistTestSupport.session, p,
                listOf(
                    PersistTestSupport.catchUp(12, 40.0, HintOrdinal.FIRST),
                    PersistTestSupport.catchUp(10, 40.0, HintOrdinal.SECOND),
                    PersistTestSupport.catchUp(8, 40.0, HintOrdinal.THIS),
                ),
            )
            PersistTestSupport.correctionOfFirst(engine, p, 11)
            engine.pause(PersistTestSupport.session, p, 30.0)
            engine.resume(PersistTestSupport.session, p)
            engine.applyFacts(PersistTestSupport.session, p, listOf(ValidatedFact.Command(CommandKind.EXTRA_SET)))
            engine.applyFacts(PersistTestSupport.session, p, listOf(ValidatedFact.Command(CommandKind.SKIP)))
        }
    }

    @Test
    fun liveSetRestAndDeletion() {
        assertEquivalent { engine ->
            val p = PersistTestSupport.start(engine)
            engine.applyFacts(PersistTestSupport.session, p, listOf(PersistTestSupport.liveSet(10)))
            engine.applyFacts(PersistTestSupport.session, p, listOf(PersistTestSupport.liveSet(8)))
            PersistTestSupport.deleteFirst(engine, p)
        }
    }

    @Test
    fun occupancyClarifyWritesNothing() {
        assertEquivalent { engine ->
            val p = PersistTestSupport.start(engine)
            engine.applyFacts(
                PersistTestSupport.session, p,
                listOf(
                    PersistTestSupport.catchUp(10, 60.0, HintOrdinal.FIRST),
                    PersistTestSupport.catchUp(8, 60.0, HintOrdinal.SECOND),
                    PersistTestSupport.catchUp(7, 60.0, HintOrdinal.THIS),
                ),
            )
            PersistTestSupport.occupancyDoesNotInvent(engine, p)
        }
    }

    @Test
    fun currentSetAndBeginSet() {
        assertEquivalent { engine ->
            val p = PersistTestSupport.start(engine)
            engine.beginSet(PersistTestSupport.session, p)
        }
    }
}
