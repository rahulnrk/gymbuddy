package com.gymbuddy.app.persist

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.gymbuddy.app.hud.GymHudSession
import com.gymbuddy.app.hud.HudWriter
import com.gymbuddy.domain.CommandKind
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.engine.WorkoutEngine
import com.gymbuddy.domain.validation.ValidatedFact
import com.gymbuddy.persist.PersistFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch

/**
 * Measurement. Snapshot must not reread the log; logged-set reads must be
 * constant in session length. Prints timings for the report.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoldCostReportTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun reportSnapshotAndReadsAtTwoHundredAndOneThousand() {
        val inner = AndroidEventLog(ctx, "fold-${System.nanoTime()}.db")
        val counting = CountingEventLog(inner)
        val engine = WorkoutEngine(counting)
        val p = EventLogHarness.plan(planned = 4)
        EventLogHarness.start(engine)
        fun size() = inner.load(SessionId(EventLogHarness.session)).size
        while (size() < 80) {
            engine.applyFacts(EventLogHarness.session, p, listOf(ValidatedFact.Command(CommandKind.EXTRA_SET)))
        }
        var n = 1
        while (size() < 200) {
            engine.applyFacts(EventLogHarness.session, p, listOf(EventLogHarness.liveSet(8 + (n % 5), 60.0)))
            n++
        }
        reportAt("200", inner, counting, engine, p)
        while (size() < 1000) {
            engine.applyFacts(EventLogHarness.session, p, listOf(ValidatedFact.Command(CommandKind.EXTRA_SET)))
        }
        reportAt("1000", inner, counting, engine, p)
        inner.close()
    }

    @Test
    fun hudWriterAppendsAndLoadsOffTheMainThread() {
        val inner = AndroidEventLog(ctx, "fold-offmain-${System.nanoTime()}.db")
        val counting = CountingEventLog(inner)
        val engine = WorkoutEngine(counting)
        val p = EventLogHarness.plan()
        val hud = GymHudSession(
            engine, p, PersistFixtures.USER_ID, PersistFixtures.SESSION_ID,
            exerciseNames = mapOf(PersistFixtures.EXERCISE_ID to "Bench Press"),
            voiceAvailable = false,
        )
        val writer = HudWriter()
        try {
            await(writer) { EventLogHarness.start(engine) }
            counting.sessionReadsOnMain = 0
            counting.appendsOnMain = 0
            await(writer) { hud.logManualSet(10, 60.0) }
            await(writer) { hud.ui() }
            println(
                "FOLD_COST_OFF_MAIN sessionReadsOnMain=${counting.sessionReadsOnMain} " +
                    "appendsOnMain=${counting.appendsOnMain} sessionReads=${counting.sessionReads} appends=${counting.appends}",
            )
            assertEquals("session load must not run on main", 0, counting.sessionReadsOnMain)
            assertEquals("append must not run on main", 0, counting.appendsOnMain)
        } finally {
            writer.shutdown()
            inner.close()
        }
    }

    private fun reportAt(
        label: String,
        inner: AndroidEventLog,
        counting: CountingEventLog,
        engine: WorkoutEngine,
        p: com.gymbuddy.domain.engine.WorkoutPlan,
    ) {
        val events = inner.load(SessionId(EventLogHarness.session)).size
        val readsBeforeSnap = counting.sessionReads
        val times = LongArray(7)
        repeat(times.size) { i ->
            val t0 = System.nanoTime()
            engine.snapshot(EventLogHarness.session, p)
            times[i] = System.nanoTime() - t0
        }
        val snapReads = counting.sessionReads - readsBeforeSnap
        val medianNs = times.sorted()[times.size / 2]
        val medianMs = medianNs / 1_000_000.0

        val hud = GymHudSession(
            engine, p, PersistFixtures.USER_ID, PersistFixtures.SESSION_ID,
            exerciseNames = mapOf(PersistFixtures.EXERCISE_ID to "Bench Press"),
            voiceAvailable = false,
        )
        val beforeSet = counting.sessionReads
        hud.logManualSet(10, 60.0)
        hud.ui()
        val readsPerHudSet = counting.sessionReads - beforeSet
        val onMain = Looper.myLooper() == Looper.getMainLooper()
        println(
            "FOLD_COST n=$label events=$events snapshot_median_ms=${"%.3f".format(medianMs)} " +
                "snapshot_reads_in_7_calls=$snapReads reads_per_hud_logManualSet_plus_ui=$readsPerHudSet " +
                "called_on_main_looper=$onMain samples_ns=${times.joinToString()}",
        )
        assertEquals("$label snapshot must not reread the log", 0, snapReads)
        assertEquals("$label logged set must not reread the log", 0, readsPerHudSet)
        assertTrue("need ~$label events, got $events", events >= label.toInt())
    }

    private fun await(writer: HudWriter, work: () -> Unit) {
        val done = CountDownLatch(1)
        writer.run(work) { done.countDown() }
        val deadline = System.currentTimeMillis() + 10_000
        while (done.count > 0 && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("hud writer did not finish", done.count == 0L)
    }
}
