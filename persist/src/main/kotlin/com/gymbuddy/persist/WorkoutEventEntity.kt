package com.gymbuddy.persist

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for committed [com.gymbuddy.domain.events.WorkoutEvent] only.
 * ValidatedFact is never stored here.
 */
@Entity(
    tableName = "event_log",
    indices = [
        Index(value = ["user_id", "event_id"], unique = true),
        Index(value = ["session_id", "sequence"], unique = true),
    ],
)
data class WorkoutEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "event_type")
    val eventType: String,
    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int,
    @ColumnInfo(name = "sequence")
    val sequence: Int,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: String,
    @ColumnInfo(name = "recorded_at")
    val recordedAt: String,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "transcript_id")
    val transcriptId: String? = null,
    @ColumnInfo(name = "language")
    val language: String? = null,
    @ColumnInfo(name = "confidence")
    val confidence: Double? = null,
    @ColumnInfo(name = "stt_final_hash")
    val sttFinalHash: String? = null,
    @ColumnInfo(name = "batch_id")
    val batchId: String? = null,
)
