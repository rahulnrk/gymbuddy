package com.gymbuddy.app.persist

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gymbuddy.domain.CommandKind
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.engine.InMemoryEventLog
import com.gymbuddy.domain.engine.WorkoutEngine
import com.gymbuddy.domain.engine.WorkoutProjector
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
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
class SnapshotDriftTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val session = SessionId(EventLogHarness.session)
    private val p get() = EventLogHarness.plan()

    @Test
    fun inMemoryDoesNotDriftThenRebuildsOnNewEngine() {
        val log = InMemoryEventLog()
        assertNoDrift(log, reopen = { log })
    }

    @Test
    fun roomDoesNotDriftThenRebuildsAfterClose() {
        val file = java.io.File.createTempFile("drift-room", ".db")
        file.deleteOnExit()
        val db1 = GymBuddyDatabase.file(file.absolutePath)
        val engine = WorkoutEngine(RoomEventLog(db1))
        play(engine)
        val live = engine.snapshot(EventLogHarness.session, p)
        val folded = WorkoutProjector.fold(RoomEventLog(db1).load(session), plan = p)
        assertSameProjection("live-room", live, folded)
        db1.close()
        val db2 = GymBuddyDatabase.file(file.absolutePath)
        try {
            val e2 = WorkoutEngine(RoomEventLog(db2))
            val cold = e2.snapshot(EventLogHarness.session, p)
            assertSameProjection("cold-room", cold, WorkoutProjector.fold(RoomEventLog(db2).load(session), plan = p))
            e2.recover(EventLogHarness.session, p)
            assertSameProjection(
                "recover-room",
                e2.snapshot(EventLogHarness.session, p),
                WorkoutProjector.fold(RoomEventLog(db2).load(session), plan = p),
            )
        } finally {
            db2.close()
        }
    }

    @Test
    fun androidDoesNotDriftThenRebuildsAfterProcessKill() {
        val name = "drift-${System.nanoTime()}.db"
        val log1 = AndroidEventLog(ctx, name)
        try {
            assertNoDrift(log1, close = { log1.close() }, reopen = { AndroidEventLog(ctx, name) })
        } finally {
            runCatching { log1.close() }
        }
    }

    private fun play(engine: WorkoutEngine) {
        val plan = EventLogHarness.start(engine)
        engine.applyFacts(EventLogHarness.session, plan, listOf(EventLogHarness.liveSet(10)))
        engine.applyFacts(EventLogHarness.session, plan, listOf(EventLogHarness.liveSet(8)))
        EventLogHarness.correctionOfFirst(engine, plan, 9)
        engine.applyFacts(EventLogHarness.session, plan, listOf(ValidatedFact.Command(CommandKind.EXTRA_SET)))
        engine.pause(EventLogHarness.session, plan, 30.0)
        engine.resume(EventLogHarness.session, plan)
        EventLogHarness.deleteFirst(engine, plan)
        engine.applyFacts(
            EventLogHarness.session, plan,
            listOf(
                EventLogHarness.catchUp(12, 40.0, HintOrdinal.FIRST),
                EventLogHarness.catchUp(10, 40.0, HintOrdinal.SECOND),
                EventLogHarness.catchUp(8, 40.0, HintOrdinal.THIS),
            ),
        )
        engine.applyFacts(EventLogHarness.session, plan, listOf(ValidatedFact.Command(CommandKind.SKIP)))
        engine.applyFacts(EventLogHarness.session, plan, listOf(EventLogHarness.liveSet(7, 50.0)))
        engine.beginSet(EventLogHarness.session, plan)
    }

    private fun assertNoDrift(
        log: EventLog,
        close: () -> Unit = {},
        reopen: () -> EventLog,
    ) {
        val engine = WorkoutEngine(log)
        play(engine)
        val cached = engine.snapshot(EventLogHarness.session, p)
        val folded = WorkoutProjector.fold(log.load(session), plan = p)
        assertSameProjection("live", cached, folded)

        close()
        val log2 = reopen()
        try {
            val e2 = WorkoutEngine(log2)
            val cold = e2.snapshot(EventLogHarness.session, p)
            val foldedCold = WorkoutProjector.fold(log2.load(session), plan = p)
            assertSameProjection("cold-start", cold, foldedCold)
            e2.recover(EventLogHarness.session, p)
            val afterRecover = e2.snapshot(EventLogHarness.session, p)
            val foldedRecover = WorkoutProjector.fold(log2.load(session), plan = p)
            assertSameProjection("after-recover", afterRecover, foldedRecover)
        } finally {
            if (log2 is AndroidEventLog) log2.close()
            if (log2 is RoomEventLog) runCatching { /* GymBuddyDatabase closed by caller if distinct */ }
        }
    }

    private fun assertSameProjection(label: String, cached: WorkoutSessionSnapshot, folded: WorkoutSessionSnapshot) {
        assertEquals("$label phase", folded.phase, cached.phase)
        assertEquals("$label epoch", folded.localEpoch, cached.localEpoch)
        assertEquals("$label lastEvent", folded.lastEventId, cached.lastEventId)
        assertEquals("$label applied", folded.appliedEventIds, cached.appliedEventIds)
        assertEquals("$label sets", folded.completedSets, cached.completedSets)
        assertEquals("$label snapKey", EventLogHarness.snapKey(folded), EventLogHarness.snapKey(cached))
        assertEquals("$label data", folded.copy(plan = cached.plan), cached.copy(plan = cached.plan))
    }
}
