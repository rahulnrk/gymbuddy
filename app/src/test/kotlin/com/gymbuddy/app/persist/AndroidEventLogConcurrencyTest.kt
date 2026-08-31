package com.gymbuddy.app.persist

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.persist.AppendAck
import com.gymbuddy.persist.PersistFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidEventLogConcurrencyTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun sameEventConcurrentlyOneLogicalRow() {
        val log = AndroidEventLog(ctx, "c1-${System.nanoTime()}.db")
        val event = EventLogHarness.event("same-event")
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)
        repeat(2) {
            pool.submit {
                start.await()
                log.append(event)
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(1, log.load(SessionId(PersistFixtures.SESSION_ID)).size)
        pool.shutdown()
        log.close()
    }

    @Test
    fun differentEventsConcurrentlyOrderedUniquely() {
        val log = AndroidEventLog(ctx, "c2-${System.nanoTime()}.db")
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)
        pool.submit {
            start.await()
            log.append(EventLogHarness.event("a-event"))
            done.countDown()
        }
        pool.submit {
            start.await()
            log.append(EventLogHarness.event("b-event"))
            done.countDown()
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        val loaded = log.load(SessionId(PersistFixtures.SESSION_ID))
        assertEquals(2, loaded.size)
        assertEquals(2, loaded.map { it.eventId }.toSet().size)
        val sequences = log.sqlite().rawQuery(
            "SELECT sequence FROM event_log WHERE session_id = ? ORDER BY sequence ASC",
            arrayOf(PersistFixtures.SESSION_ID),
        ).use { c ->
            val out = mutableListOf<Int>()
            while (c.moveToNext()) out += c.getInt(0)
            out
        }
        assertEquals(listOf(1, 2), sequences)
        assertEquals(2, log.latestVersion(SessionId(PersistFixtures.SESSION_ID)))
        pool.shutdown()
        log.close()
    }

    @Test
    fun appendAckDuplicateOnSecondWrite() {
        val log = AndroidEventLog(ctx, "c3-${System.nanoTime()}.db")
        val e = EventLogHarness.event("once")
        assertTrue(log.append(e) is AppendAck.Written)
        assertTrue(log.append(e) is AppendAck.Duplicate)
        log.close()
    }
}
