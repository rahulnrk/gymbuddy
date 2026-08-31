package com.gymbuddy.app.persist

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gymbuddy.domain.CommandKind
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.engine.InMemoryEventLog
import com.gymbuddy.domain.engine.WorkoutEngine
import com.gymbuddy.domain.engine.WorkoutProjector
import com.gymbuddy.domain.persist.EventLog
import com.gymbuddy.domain.validation.HintOrdinal
import com.gymbuddy.domain.validation.ValidatedFact
import com.gymbuddy.persist.GymBuddyDatabase
import com.gymbuddy.persist.RoomEventLog
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThreeWayEventLogEquivalenceTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun assertEquivalent(act: (WorkoutEngine) -> Unit) {
        val memLog = InMemoryEventLog()
        act(WorkoutEngine(memLog))
        val memEvents = memLog.sessionEvents(SessionId(EventLogHarness.session))
        val p = EventLogHarness.plan()
        val memSnap = WorkoutProjector.fold(memEvents, plan = p)

        val db = GymBuddyDatabase.inMemory()
        val androidLog = AndroidEventLog(ctx, "eq-${System.nanoTime()}.db")
        try {
            val roomLog = RoomEventLog(db)
            act(WorkoutEngine(roomLog))
            act(WorkoutEngine(androidLog))

            fun check(name: String, log: EventLog) {
                val events = log.load(SessionId(EventLogHarness.session))
                val snap = WorkoutProjector.fold(events, plan = p)
                assertEquals("$name size", memEvents.size, events.size)
                assertEquals("$name version", memLog.latestVersion(SessionId(EventLogHarness.session)), log.latestVersion(SessionId(EventLogHarness.session)))
                assertEquals("$name types", EventLogHarness.typeSeq(memEvents), EventLogHarness.typeSeq(events))
                assertEquals("$name sources", memEvents.map { it.source }, events.map { it.source })
                assertEquals("$name snap", EventLogHarness.snapKey(memSnap), EventLogHarness.snapKey(snap))
                assertEquals(
                    "$name live sets",
                    memSnap.liveSetsOnCurrent().map { it.setNumber to it.reps },
                    snap.liveSetsOnCurrent().map { it.setNumber to it.reps },
                )
                val foldedAgain = WorkoutProjector.fold(events, plan = p)
                assertEquals("$name fold stable", EventLogHarness.snapKey(snap), EventLogHarness.snapKey(foldedAgain))
            }
            check("RoomEventLog", roomLog)
            check("AndroidEventLog", androidLog)
        } finally {
            db.close()
            androidLog.close()
        }
    }

    @Test
    fun catchUpCorrectionPauseResumeExtraSetSkip() {
        assertEquivalent { engine ->
            val p = EventLogHarness.start(engine)
            engine.applyFacts(
                EventLogHarness.session, p,
                listOf(
                    EventLogHarness.catchUp(12, 40.0, HintOrdinal.FIRST),
                    EventLogHarness.catchUp(10, 40.0, HintOrdinal.SECOND),
                    EventLogHarness.catchUp(8, 40.0, HintOrdinal.THIS),
                ),
            )
            EventLogHarness.correctionOfFirst(engine, p, 11)
            engine.pause(EventLogHarness.session, p, 30.0)
            engine.resume(EventLogHarness.session, p)
            engine.applyFacts(EventLogHarness.session, p, listOf(ValidatedFact.Command(CommandKind.EXTRA_SET)))
            engine.applyFacts(EventLogHarness.session, p, listOf(ValidatedFact.Command(CommandKind.SKIP)))
        }
    }

    @Test
    fun liveSetRestAndDeletion() {
        assertEquivalent { engine ->
            val p = EventLogHarness.start(engine)
            engine.applyFacts(EventLogHarness.session, p, listOf(EventLogHarness.liveSet(10)))
            engine.applyFacts(EventLogHarness.session, p, listOf(EventLogHarness.liveSet(8)))
            EventLogHarness.deleteFirst(engine, p)
        }
    }

    @Test
    fun occupancyClarifyWritesNothing() {
        assertEquivalent { engine ->
            val p = EventLogHarness.start(engine)
            engine.applyFacts(
                EventLogHarness.session, p,
                listOf(
                    EventLogHarness.catchUp(10, 60.0, HintOrdinal.FIRST),
                    EventLogHarness.catchUp(8, 60.0, HintOrdinal.SECOND),
                    EventLogHarness.catchUp(7, 60.0, HintOrdinal.THIS),
                ),
            )
            engine.applyFacts(EventLogHarness.session, p, emptyList())
        }
    }

    @Test
    fun currentSetAndBeginSet() {
        assertEquivalent { engine ->
            val p = EventLogHarness.start(engine)
            engine.beginSet(EventLogHarness.session, p)
        }
    }
}
