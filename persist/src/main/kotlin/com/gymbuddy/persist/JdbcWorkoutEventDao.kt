package com.gymbuddy.persist

import java.sql.ResultSet
import kotlin.concurrent.withLock

internal class JdbcWorkoutEventDao(
    private val db: GymBuddyDatabase,
) : WorkoutEventDao {

    override fun insert(entity: WorkoutEventEntity) {
        db.lock.withLock {
            db.connection.prepareStatement(
                """
                INSERT INTO event_log(
                  event_id, session_id, user_id, event_type, schema_version, sequence,
                  payload_json, occurred_at, recorded_at, source, transcript_id, language,
                  confidence, stt_final_hash, batch_id
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """.trimIndent(),
            ).use { ps ->
                ps.setString(1, entity.eventId)
                ps.setString(2, entity.sessionId)
                ps.setString(3, entity.userId)
                ps.setString(4, entity.eventType)
                ps.setInt(5, entity.schemaVersion)
                ps.setInt(6, entity.sequence)
                ps.setString(7, entity.payloadJson)
                ps.setString(8, entity.occurredAt)
                ps.setString(9, entity.recordedAt)
                ps.setString(10, entity.source)
                ps.setString(11, entity.transcriptId)
                ps.setString(12, entity.language)
                if (entity.confidence == null) ps.setNull(13, java.sql.Types.REAL)
                else ps.setDouble(13, entity.confidence)
                ps.setString(14, entity.sttFinalHash)
                ps.setString(15, entity.batchId)
                ps.executeUpdate()
            }
        }
    }

    override fun loadBySession(sessionId: String): List<WorkoutEventEntity> = db.lock.withLock {
        db.connection.prepareStatement(
            "SELECT * FROM event_log WHERE session_id = ? ORDER BY sequence ASC",
        ).use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs -> rows(rs) }
        }
    }

    override fun loadFromVersion(sessionId: String, version: Int): List<WorkoutEventEntity> = db.lock.withLock {
        db.connection.prepareStatement(
            "SELECT * FROM event_log WHERE session_id = ? AND sequence > ? ORDER BY sequence ASC",
        ).use { ps ->
            ps.setString(1, sessionId)
            ps.setInt(2, version)
            ps.executeQuery().use { rs -> rows(rs) }
        }
    }

    override fun latestVersion(sessionId: String): Int = db.lock.withLock {
        db.connection.prepareStatement(
            "SELECT COALESCE(MAX(sequence), 0) FROM event_log WHERE session_id = ?",
        ).use { ps ->
            ps.setString(1, sessionId)
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
    }

    override fun find(userId: String, eventId: String): WorkoutEventEntity? = db.lock.withLock {
        db.connection.prepareStatement(
            "SELECT * FROM event_log WHERE user_id = ? AND event_id = ? LIMIT 1",
        ).use { ps ->
            ps.setString(1, userId)
            ps.setString(2, eventId)
            ps.executeQuery().use { rs -> if (rs.next()) row(rs) else null }
        }
    }

    override fun countById(userId: String, eventId: String): Int = db.lock.withLock {
        db.connection.prepareStatement(
            "SELECT COUNT(*) FROM event_log WHERE user_id = ? AND event_id = ?",
        ).use { ps ->
            ps.setString(1, userId)
            ps.setString(2, eventId)
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
    }

    private fun rows(rs: ResultSet): List<WorkoutEventEntity> {
        val out = mutableListOf<WorkoutEventEntity>()
        while (rs.next()) out += row(rs)
        return out
    }

    private fun row(rs: ResultSet): WorkoutEventEntity {
        val conf = rs.getObject("confidence")
        return WorkoutEventEntity(
            eventId = rs.getString("event_id"),
            sessionId = rs.getString("session_id"),
            userId = rs.getString("user_id"),
            eventType = rs.getString("event_type"),
            schemaVersion = rs.getInt("schema_version"),
            sequence = rs.getInt("sequence"),
            payloadJson = rs.getString("payload_json"),
            occurredAt = rs.getString("occurred_at"),
            recordedAt = rs.getString("recorded_at"),
            source = rs.getString("source"),
            transcriptId = rs.getString("transcript_id"),
            language = rs.getString("language"),
            confidence = (conf as? Number)?.toDouble(),
            sttFinalHash = rs.getString("stt_final_hash"),
            batchId = rs.getString("batch_id"),
        )
    }
}
