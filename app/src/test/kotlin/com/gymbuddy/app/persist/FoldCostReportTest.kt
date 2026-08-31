package com.gymbuddy.app.persist

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.gymbuddy.app.hud.GymHudSession
import com.gymbuddy.domain.CommandKind
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.engine.WorkoutEngine
import com.gymbuddy.domain.validation.ValidatedFact
import com.gymbuddy.persist.PersistFixtures
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Measurement only. Does not fail on slowness — report the printed numbers.
 *
 * Robolectric SQLite is not a phone, but it is Android's SQLite API on a file,
 * which is the store the APK uses. GymModeActivity.refreshHud() calls
 * engine.snapshot() on the UI thread (compose onClick).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoldCostReportTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun reportTwoHundredEventSnapshotAndReadsPerSet() {
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
        val events = size()
        assertTrue("need ~200 events, got $events", events >= 200)

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
            "FOLD_COST events=$events snapshot_median_ms=${"%.3f".format(medianMs)} " +
                "snapshot_reads_in_7_calls=$snapReads reads_per_hud_logManualSet_plus_ui=$readsPerHudSet " +
                "called_on_main_looper=$onMain samples_ns=${times.joinToString()}",
        )
        inner.close()
    }
}
