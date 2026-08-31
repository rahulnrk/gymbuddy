package com.gymbuddy.app.persist

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.gymbuddy.domain.EventId
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.UserId
import com.gymbuddy.domain.events.WorkoutEvent
import com.gymbuddy.domain.persist.AppendAck
import com.gymbuddy.domain.persist.EventLog
import com.gymbuddy.persist.PersistFixtures
import com.gymbuddy.persist.SchemaSql
import com.gymbuddy.persist.WorkoutEventCodec
import com.gymbuddy.persist.WorkoutEventEntity
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Android SQLite EventLog using :persist schema and [WorkoutEventCodec].
 * JDBC sqlite is excluded from the APK; this is the on-device store.
 */
class AndroidEventLog(
    context: Context,
    name: String = "gymbuddy.db",
    version: Int = SchemaSql.VERSION,
) : EventLog {

    private val helper = Helper(context.applicationContext, name, version)
    private val lock = ReentrantLock()

    override fun append(event: WorkoutEvent): AppendAck {
        val db = helper.writableDatabase
        repeat(8) {
            lock.withLock {
                db.beginTransaction()
                try {
                    if (countById(db, event.userId, event.eventId) > 0) {
                        db.endTransaction()
                        return AppendAck.Duplicate(EventId(event.eventId))
                    }
                    val sessionKey = event.sessionId ?: ""
                    val next = latestVersion(db, sessionKey) + 1
                    val entity = WorkoutEventCodec.toEntity(event, next)
                    db.insertOrThrow("event_log", null, values(entity))
                    db.setTransactionSuccessful()
                    return AppendAck.Written(EventId(event.eventId))
                } catch (e: SQLiteConstraintException) {
                    val msg = e.message.orEmpty().lowercase()
                    if ("event_id" in msg || event.eventId.lowercase() in msg) {
                        return AppendAck.Duplicate(EventId(event.eventId))
                    }
                    // sequence UNIQUE: retry
                } finally {
                    if (db.inTransaction()) db.endTransaction()
                }
            }
        }
        error("could not assign a unique sequence for event ${event.eventId}")
    }

    override fun find(userId: UserId, eventId: EventId): WorkoutEvent? = lock.withLock {
        helper.readableDatabase.rawQuery(
            "SELECT * FROM event_log WHERE user_id = ? AND event_id = ? LIMIT 1",
            arrayOf(userId.value, eventId.value),
        ).use { c -> if (c.moveToFirst()) WorkoutEventCodec.fromEntity(row(c)) else null }
    }

    override fun sessionEvents(sessionId: SessionId): List<WorkoutEvent> = lock.withLock {
        helper.readableDatabase.rawQuery(
            "SELECT * FROM event_log WHERE session_id = ? ORDER BY sequence ASC",
            arrayOf(sessionId.value),
        ).use { c ->
            val out = mutableListOf<WorkoutEvent>()
            while (c.moveToNext()) out += WorkoutEventCodec.fromEntity(row(c))
            out
        }
    }

    override fun latestVersion(sessionId: SessionId): Int = lock.withLock {
        latestVersion(helper.readableDatabase, sessionId.value)
    }

    fun close() = helper.close()

    /** Exposed for tests that insert malformed rows. */
    internal fun sqlite(): SQLiteDatabase = helper.writableDatabase

    private fun countById(db: SQLiteDatabase, userId: String, eventId: String): Int {
        db.rawQuery(
            "SELECT COUNT(*) FROM event_log WHERE user_id = ? AND event_id = ?",
            arrayOf(userId, eventId),
        ).use { c ->
            c.moveToFirst()
            return c.getInt(0)
        }
    }

    private fun latestVersion(db: SQLiteDatabase, sessionId: String): Int {
        db.rawQuery(
            "SELECT COALESCE(MAX(sequence), 0) FROM event_log WHERE session_id = ?",
            arrayOf(sessionId),
        ).use { c ->
            c.moveToFirst()
            return c.getInt(0)
        }
    }

    private fun values(e: WorkoutEventEntity) = ContentValues().apply {
        put("event_id", e.eventId)
        put("session_id", e.sessionId)
        put("user_id", e.userId)
        put("event_type", e.eventType)
        put("schema_version", e.schemaVersion)
        put("sequence", e.sequence)
        put("payload_json", e.payloadJson)
        put("occurred_at", e.occurredAt)
        put("recorded_at", e.recordedAt)
        put("source", e.source)
        put("transcript_id", e.transcriptId)
        put("language", e.language)
        if (e.confidence == null) putNull("confidence") else put("confidence", e.confidence)
        put("stt_final_hash", e.sttFinalHash)
        put("batch_id", e.batchId)
    }

    private fun row(c: Cursor) = WorkoutEventEntity(
        eventId = c.getString(c.getColumnIndexOrThrow("event_id")),
        sessionId = c.getString(c.getColumnIndexOrThrow("session_id")),
        userId = c.getString(c.getColumnIndexOrThrow("user_id")),
        eventType = c.getString(c.getColumnIndexOrThrow("event_type")),
        schemaVersion = c.getInt(c.getColumnIndexOrThrow("schema_version")),
        sequence = c.getInt(c.getColumnIndexOrThrow("sequence")),
        payloadJson = c.getString(c.getColumnIndexOrThrow("payload_json")),
        occurredAt = c.getString(c.getColumnIndexOrThrow("occurred_at")),
        recordedAt = c.getString(c.getColumnIndexOrThrow("recorded_at")),
        source = c.getString(c.getColumnIndexOrThrow("source")),
        transcriptId = c.optionalString("transcript_id"),
        language = c.optionalString("language"),
        confidence = if (c.isNull(c.getColumnIndexOrThrow("confidence"))) null else c.getDouble(c.getColumnIndexOrThrow("confidence")),
        sttFinalHash = c.optionalString("stt_final_hash"),
        batchId = c.optionalString("batch_id"),
    )

    private fun Cursor.optionalString(col: String): String? {
        val i = getColumnIndexOrThrow(col)
        return if (isNull(i)) null else getString(i)
    }

    private class Helper(context: Context, name: String, version: Int) :
        SQLiteOpenHelper(context, name, null, version) {
        override fun onCreate(db: SQLiteDatabase) {
            SchemaSql.apply { sql -> db.execSQL(sql) }
            PersistFixtures.seed { sql, args -> db.execSQL(sql, args) }
        }

        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            error("no migration defined for $oldVersion → $newVersion (destructive migration is forbidden)")
        }

        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            error("cannot migrate downward $oldVersion → $newVersion")
        }
    }
}
