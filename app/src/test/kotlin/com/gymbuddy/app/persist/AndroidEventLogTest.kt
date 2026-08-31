package com.gymbuddy.app.persist

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.EventId
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.UserId
import com.gymbuddy.domain.persist.AppendAck
import com.gymbuddy.persist.PersistFixtures
import com.gymbuddy.persist.SchemaSql
import com.gymbuddy.persist.WorkoutEventCodec
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFails

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidEventLogTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val session = PersistFixtures.SESSION_ID
    private val user = PersistFixtures.USER_ID

    private fun log(name: String = "ael-${System.nanoTime()}.db") = AndroidEventLog(ctx, name)

    @Test
    fun insertAndOrderedLoad() {
        val log = log()
        log.append(EventLogHarness.event("e1"))
        log.append(
            EventLogHarness.event(
                "e2",
                CommittedEventType.WORKOUT_STARTED,
            ),
        )
        val loaded = log.load(SessionId(session))
        assertEquals(listOf("e1", "e2"), loaded.map { it.eventId })
        assertEquals(2, log.latestVersion(SessionId(session)))
        assertEquals(listOf("e2"), log.loadFromVersion(SessionId(session), 1).map { it.eventId })
        log.close()
    }

    @Test
    fun duplicateEventIdIsIdempotent() {
        val log = log()
        val e = EventLogHarness.event("dup-1")
        assertTrue(log.append(e) is AppendAck.Written)
        assertTrue(log.appendIfAbsent(e) is AppendAck.Duplicate)
        assertEquals(1, log.load(SessionId(session)).size)
        log.close()
    }

    @Test
    fun appendDoesNotRewriteExistingRow() {
        val log = log()
        val first = EventLogHarness.event("stable")
        log.append(first)
        log.append(first.copy(payload = buildJsonObject { put("tampered", true) }))
        val loaded = log.load(SessionId(session)).single()
        assertEquals(first.payload, loaded.payload)
        log.close()
    }

    @Test
    fun findByUserAndId() {
        val log = log()
        log.append(EventLogHarness.event("find-me"))
        assertEquals("find-me", log.find(UserId(user), EventId("find-me"))?.eventId)
        assertNull(log.find(UserId(user), EventId("nope")))
        log.close()
    }

    @Test
    fun unknownTypeFailsDeterministically() {
        val log = log()
        val db = log.sqlite()
        db.insertOrThrow(
            "event_log",
            null,
            ContentValues().apply {
                put("event_id", "x")
                put("session_id", session)
                put("user_id", user)
                put("event_type", "NOT_A_REAL_EVENT")
                put("schema_version", 1)
                put("sequence", 1)
                put("payload_json", "{}")
                put("occurred_at", "t")
                put("recorded_at", "t")
                put("source", "SYSTEM")
            },
        )
        assertFails { log.sessionEvents(SessionId(session)) }
        log.close()
    }

    @Test
    fun malformedPayloadFailsDeterministically() {
        val log = log()
        val db = log.sqlite()
        db.insertOrThrow(
            "event_log",
            null,
            ContentValues().apply {
                put("event_id", "x")
                put("session_id", session)
                put("user_id", user)
                put("event_type", "WORKOUT_STARTED")
                put("schema_version", 1)
                put("sequence", 1)
                put("payload_json", "not-json")
                put("occurred_at", "t")
                put("recorded_at", "t")
                put("source", "SYSTEM")
            },
        )
        assertFails { log.sessionEvents(SessionId(session)) }
        log.close()
    }

    @Test
    fun schemaVersionMismatchIsRejected() {
        val entity = com.gymbuddy.persist.WorkoutEventEntity(
            eventId = "x",
            sessionId = session,
            userId = user,
            eventType = "WORKOUT_STARTED",
            schemaVersion = 99,
            sequence = 1,
            payloadJson = "{}",
            occurredAt = "t",
            recordedAt = "t",
            source = "SYSTEM",
        )
        assertFails { WorkoutEventCodec.fromEntity(entity) }
    }

    @Test
    fun fixturesAreSeededFromSharedPersist() {
        val log = log()
        val c = log.sqlite().rawQuery("SELECT id FROM fixture_user", null)
        c.moveToFirst()
        assertEquals(PersistFixtures.USER_ID, c.getString(0))
        c.close()
        log.close()
    }

    @Test
    fun schemaComesFromPersistResource() {
        assertTrue(SchemaSql.ddl().contains("CREATE TABLE IF NOT EXISTS event_log"))
        assertEquals(1, SchemaSql.VERSION)
        val src = java.io.File("src/main/kotlin/com/gymbuddy/app/persist/AndroidEventLog.kt")
            .takeIf { it.isFile }
            ?: java.io.File("../app/src/main/kotlin/com/gymbuddy/app/persist/AndroidEventLog.kt")
        val text = src.readText()
        assertTrue("CREATE TABLE IF NOT EXISTS event_log" !in text)
        assertTrue("SchemaSql" in text)
    }
}
