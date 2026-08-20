package com.gymbuddy.persist

import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.events.WorkoutEvent
import com.gymbuddy.domain.persist.AppendAck
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomConcurrencyTest {
    private fun ev(id: String) = WorkoutEvent(
        eventId = id,
        sessionId = PersistFixtures.SESSION_ID,
        userId = PersistFixtures.USER_ID,
        occurredAt = "2026-08-20T16:00:00Z",
        recordedAt = "2026-08-20T16:00:00Z",
        source = EventSource.SYSTEM,
        type = CommittedEventType.SESSION_INITIALIZED,
        payload = buildJsonObject {
            put("sessionId", PersistFixtures.SESSION_ID)
            put("workoutId", PersistFixtures.WORKOUT_ID)
        },
    )

    @Test
    fun sameEventConcurrentlyOneLogicalRow() {
        val file = File.createTempFile("gymbuddy-c1", ".db")
        file.deleteOnExit()
        val db = GymBuddyDatabase.file(file.absolutePath)
        val log = RoomEventLog(db)
        val event = ev("same-event")
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
        db.close()
    }

    @Test
    fun differentEventsConcurrentlyOrderedUniquely() {
        val file = File.createTempFile("gymbuddy-c2", ".db")
        file.deleteOnExit()
        val db = GymBuddyDatabase.file(file.absolutePath)
        val log = RoomEventLog(db)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)
        pool.submit {
            start.await()
            log.append(ev("a-event"))
            done.countDown()
        }
        pool.submit {
            start.await()
            log.append(ev("b-event"))
            done.countDown()
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        val loaded = log.load(SessionId(PersistFixtures.SESSION_ID))
        assertEquals(2, loaded.size)
        assertEquals(2, loaded.map { it.eventId }.toSet().size)
        val sequences = db.workoutEventDao.loadBySession(PersistFixtures.SESSION_ID).map { it.sequence }.sorted()
        assertEquals(listOf(1, 2), sequences)
        assertEquals(2, log.latestVersion(SessionId(PersistFixtures.SESSION_ID)))
        pool.shutdown()
        db.close()
    }

    @Test
    fun appendAckDuplicateOnSecondWrite() {
        val db = GymBuddyDatabase.inMemory()
        val log = RoomEventLog(db)
        val e = ev("once")
        assertTrue(log.append(e) is AppendAck.Written)
        assertTrue(log.append(e) is AppendAck.Duplicate)
        db.close()
    }
}
