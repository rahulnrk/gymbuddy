package com.gymbuddy.persist

import com.gymbuddy.domain.EventId
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.UserId
import com.gymbuddy.domain.events.WorkoutEvent
import com.gymbuddy.domain.persist.AppendAck
import com.gymbuddy.domain.persist.EventLog
import java.sql.SQLException
import kotlin.concurrent.withLock

/**
 * EventLog backed by the Room [event_log] schema.
 * Stores committed [WorkoutEvent] only — never ValidatedFact.
 *
 * Sequencing strategy (documented before implementation):
 * one transaction reads MAX(sequence)+1 and inserts under a connection lock.
 * UNIQUE(session_id, sequence) rejects a race; the append retries rather than
 * overwriting. UNIQUE(user_id, event_id) and PRIMARY KEY event_id enforce
 * idempotency at the database, not only in application code.
 */
class RoomEventLog(
    private val db: GymBuddyDatabase,
) : EventLog {

    override fun append(event: WorkoutEvent): AppendAck {
        repeat(8) {
            db.lock.withLock {
                val conn = db.connection
                val prev = conn.autoCommit
                conn.autoCommit = false
                try {
                    if (db.workoutEventDao.countById(event.userId, event.eventId) > 0) {
                        conn.rollback()
                        return AppendAck.Duplicate(EventId(event.eventId))
                    }
                    val sessionKey = event.sessionId ?: ""
                    val next = db.workoutEventDao.latestVersion(sessionKey) + 1
                    db.workoutEventDao.insert(WorkoutEventCodec.toEntity(event, next))
                    conn.commit()
                    return AppendAck.Written(EventId(event.eventId))
                } catch (e: SQLException) {
                    conn.rollback()
                    if (isEventIdConflict(e, event.eventId)) {
                        return AppendAck.Duplicate(EventId(event.eventId))
                    }
                    if (!isUniqueViolation(e)) throw e
                    // sequence UNIQUE: retry with a fresh MAX(sequence)+1
                } finally {
                    conn.autoCommit = prev
                }
            }
        }
        error("could not assign a unique sequence for event ${event.eventId}")
    }

    private fun isUniqueViolation(e: SQLException): Boolean {
        if (e.errorCode in UNIQUE_CODES) return true
        val msg = e.message.orEmpty().lowercase()
        return "unique" in msg || "constraint" in msg
    }

    private fun isEventIdConflict(e: SQLException, eventId: String): Boolean {
        if (!isUniqueViolation(e)) return false
        val msg = e.message.orEmpty().lowercase()
        return "event_id" in msg || eventId.lowercase() in msg
    }

    private companion object {
        val UNIQUE_CODES = setOf(19, 2067, 1555, 2579)
    }

    override fun appendIfAbsent(event: WorkoutEvent): AppendAck = append(event)

    override fun find(userId: UserId, eventId: EventId): WorkoutEvent? =
        db.workoutEventDao.find(userId.value, eventId.value)?.let(WorkoutEventCodec::fromEntity)

    override fun sessionEvents(sessionId: SessionId): List<WorkoutEvent> =
        db.workoutEventDao.loadBySession(sessionId.value).map(WorkoutEventCodec::fromEntity)

    override fun load(sessionId: SessionId): List<WorkoutEvent> = sessionEvents(sessionId)

    override fun loadFromVersion(sessionId: SessionId, version: Int): List<WorkoutEvent> =
        db.workoutEventDao.loadFromVersion(sessionId.value, version).map(WorkoutEventCodec::fromEntity)

    override fun latestVersion(sessionId: SessionId): Int =
        db.workoutEventDao.latestVersion(sessionId.value)
}
