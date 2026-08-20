package com.gymbuddy.persist

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WorkoutEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entity: WorkoutEventEntity)

    @Query("SELECT * FROM event_log WHERE session_id = :sessionId ORDER BY sequence ASC")
    fun loadBySession(sessionId: String): List<WorkoutEventEntity>

    @Query("SELECT * FROM event_log WHERE session_id = :sessionId AND sequence > :version ORDER BY sequence ASC")
    fun loadFromVersion(sessionId: String, version: Int): List<WorkoutEventEntity>

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM event_log WHERE session_id = :sessionId")
    fun latestVersion(sessionId: String): Int

    @Query("SELECT * FROM event_log WHERE user_id = :userId AND event_id = :eventId LIMIT 1")
    fun find(userId: String, eventId: String): WorkoutEventEntity?

    @Query("SELECT COUNT(*) FROM event_log WHERE user_id = :userId AND event_id = :eventId")
    fun countById(userId: String, eventId: String): Int
}
