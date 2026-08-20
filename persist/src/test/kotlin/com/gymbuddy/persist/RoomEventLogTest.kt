package com.gymbuddy.persist

import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.EventId
import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.UserId
import com.gymbuddy.domain.events.WorkoutEvent
import com.gymbuddy.domain.persist.AppendAck
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomEventLogTest {
    private val user = PersistFixtures.USER_ID
    private val session = PersistFixtures.SESSION_ID

    private fun event(
        id: String,
        type: CommittedEventType = CommittedEventType.SESSION_INITIALIZED,
        payload: kotlinx.serialization.json.JsonObject = buildJsonObject {
            put("sessionId", session)
            put("workoutId", PersistFixtures.WORKOUT_ID)
        },
    ) = WorkoutEvent(
        eventId = id,
        sessionId = session,
        userId = user,
        occurredAt = "2026-08-20T16:00:00Z",
        recordedAt = "2026-08-20T16:00:00Z",
        source = EventSource.SYSTEM,
        type = type,
        payload = payload,
    )

    @Test
    fun insertAndOrderedLoad() {
        val db = GymBuddyDatabase.inMemory()
        val log = RoomEventLog(db)
        log.append(event("e1"))
        log.append(
            event(
                "e2",
                CommittedEventType.WORKOUT_STARTED,
                buildJsonObject {
                    put("workoutId", PersistFixtures.WORKOUT_ID)
                    put("startedAt", "2026-08-20T16:00:00Z")
                },
            ),
        )
        val loaded = log.load(SessionId(session))
        assertEquals(listOf("e1", "e2"), loaded.map { it.eventId })
        assertEquals(2, log.latestVersion(SessionId(session)))
        assertEquals(listOf("e2"), log.loadFromVersion(SessionId(session), 1).map { it.eventId })
        db.close()
    }

    @Test
    fun duplicateEventIdIsIdempotent() {
        val db = GymBuddyDatabase.inMemory()
        val log = RoomEventLog(db)
        val e = event("dup-1")
        assertTrue(log.append(e) is AppendAck.Written)
        assertTrue(log.appendIfAbsent(e) is AppendAck.Duplicate)
        assertEquals(1, log.load(SessionId(session)).size)
        db.close()
    }

    @Test
    fun findByUserAndId() {
        val db = GymBuddyDatabase.inMemory()
        val log = RoomEventLog(db)
        log.append(event("find-me"))
        assertEquals("find-me", log.find(UserId(user), EventId("find-me"))?.eventId)
        assertNull(log.find(UserId(user), EventId("nope")))
        db.close()
    }

    @Test
    fun unknownTypeFailsDeterministically() {
        val entity = WorkoutEventEntity(
            eventId = "x",
            sessionId = session,
            userId = user,
            eventType = "NOT_A_REAL_EVENT",
            schemaVersion = 1,
            sequence = 1,
            payloadJson = "{}",
            occurredAt = "t",
            recordedAt = "t",
            source = "SYSTEM",
        )
        assertFails { WorkoutEventCodec.fromEntity(entity) }
    }

    @Test
    fun malformedPayloadFailsDeterministically() {
        val entity = WorkoutEventEntity(
            eventId = "x",
            sessionId = session,
            userId = user,
            eventType = "WORKOUT_STARTED",
            schemaVersion = 1,
            sequence = 1,
            payloadJson = "not-json",
            occurredAt = "t",
            recordedAt = "t",
            source = "SYSTEM",
        )
        assertFails { WorkoutEventCodec.fromEntity(entity) }
    }

    @Test
    fun schemaVersionIsOne() {
        assertEquals(1, GymBuddyDatabase.VERSION)
        val db = GymBuddyDatabase.inMemory()
        db.connection.createStatement().use { st ->
            st.executeQuery("SELECT version FROM schema_version").use { rs ->
                rs.next()
                assertEquals(1, rs.getInt(1))
            }
        }
        db.close()
    }

    @Test
    fun fixturesAreSeeded() {
        val db = GymBuddyDatabase.inMemory()
        db.connection.createStatement().use { st ->
            st.executeQuery("SELECT id FROM fixture_user").use { rs ->
                rs.next()
                assertEquals(PersistFixtures.USER_ID, rs.getString(1))
            }
        }
        db.close()
    }

    @Test
    fun codecRoundTripPreservesTypedEvent() {
        val original = event(
            "round",
            CommittedEventType.SET_COMPLETED,
            buildJsonObject {
                put("setId", "set-1")
                put("reps", 10)
                put("weightKg", 60.0)
                put("setNumber", 1)
            },
        )
        val entity = WorkoutEventCodec.toEntity(original, 4)
        val back = WorkoutEventCodec.fromEntity(entity)
        assertEquals(original, back)
        assertEquals(4, entity.sequence)
        assertEquals("SET_COMPLETED", entity.eventType)
    }

    @Test
    fun appendDoesNotRewriteExistingRow() {
        val db = GymBuddyDatabase.inMemory()
        val log = RoomEventLog(db)
        val first = event("stable")
        log.append(first)
        log.append(first.copy(payload = buildJsonObject { put("tampered", true) }))
        val loaded = log.load(SessionId(session)).single()
        assertEquals(first.payload, loaded.payload)
        db.close()
    }
}
