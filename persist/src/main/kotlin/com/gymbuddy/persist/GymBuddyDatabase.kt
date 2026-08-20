package com.gymbuddy.persist

import androidx.room.Database
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Database(entities = [WorkoutEventEntity::class], version = GymBuddyDatabase.VERSION, exportSchema = true)
abstract class GymBuddyRoomSpec {
    abstract fun workoutEventDao(): WorkoutEventDao
}

/**
 * SQLite-backed database matching the Room schema (version 1).
 * Android later uses Room.databaseBuilder on [GymBuddyRoomSpec].
 */
class GymBuddyDatabase private constructor(
    val connection: Connection,
    val lock: ReentrantLock = ReentrantLock(),
) {
    val workoutEventDao: WorkoutEventDao = JdbcWorkoutEventDao(this)

    fun close() {
        lock.withLock { connection.close() }
    }

    companion object {
        const val VERSION = 1

        fun open(jdbcUrl: String): GymBuddyDatabase {
            Class.forName("org.sqlite.JDBC")
            val conn = DriverManager.getConnection(jdbcUrl)
            conn.autoCommit = true
            conn.createStatement().use { st ->
                st.execute("PRAGMA foreign_keys = ON")
                st.execute("PRAGMA busy_timeout = 5000")
                st.executeQuery("PRAGMA journal_mode = DELETE").close()
            }
            val db = GymBuddyDatabase(conn)
            SchemaMigrator.migrate(conn, VERSION)
            PersistFixtures.seed(conn)
            return db
        }

        fun inMemory(): GymBuddyDatabase = open("jdbc:sqlite::memory:")

        fun file(path: String): GymBuddyDatabase = open("jdbc:sqlite:${path.replace('\\', '/')}")
    }
}

object SchemaMigrator {
    fun currentVersion(conn: Connection): Int =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version").use { rs ->
                if (!rs.next()) 0 else rs.getInt(1)
            }
        }

    fun migrate(conn: Connection, target: Int) {
        conn.createStatement().use { st ->
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS schema_version (
                  version INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
        val current = currentVersion(conn)
        require(current <= target) { "cannot migrate downward $current → $target" }
        if (current == 0) {
            applyV1(conn)
            conn.createStatement().use { it.execute("INSERT INTO schema_version(version) VALUES ($target)") }
        } else if (current < target) {
            error("no migration defined for $current → $target (destructive migration is forbidden)")
        }
    }

    fun applyV1(conn: Connection) {
        conn.createStatement().use { st ->
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS event_log (
                  event_id TEXT PRIMARY KEY NOT NULL,
                  session_id TEXT NOT NULL,
                  user_id TEXT NOT NULL,
                  event_type TEXT NOT NULL,
                  schema_version INTEGER NOT NULL,
                  sequence INTEGER NOT NULL,
                  payload_json TEXT NOT NULL,
                  occurred_at TEXT NOT NULL,
                  recorded_at TEXT NOT NULL,
                  source TEXT NOT NULL,
                  transcript_id TEXT,
                  language TEXT,
                  confidence REAL,
                  stt_final_hash TEXT,
                  batch_id TEXT,
                  UNIQUE(user_id, event_id),
                  UNIQUE(session_id, sequence)
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS fixture_user (
                  id TEXT PRIMARY KEY NOT NULL,
                  display_name TEXT NOT NULL
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS fixture_exercise (
                  id TEXT PRIMARY KEY NOT NULL,
                  canonical_name TEXT NOT NULL
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS fixture_workout (
                  id TEXT PRIMARY KEY NOT NULL,
                  user_id TEXT NOT NULL,
                  name TEXT NOT NULL
                )
                """.trimIndent(),
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS fixture_session (
                  id TEXT PRIMARY KEY NOT NULL,
                  user_id TEXT NOT NULL,
                  workout_id TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }
}

object PersistFixtures {
    const val USER_ID = "11111111-1111-4111-8111-111111111111"
    const val WORKOUT_ID = "33333333-3333-4333-8333-333333333333"
    const val EXERCISE_ID = "44444444-4444-4444-8444-444444444444"
    const val SESSION_ID = "22222222-2222-4222-8222-222222222222"
    const val WORKOUT_EXERCISE_ID = "55555555-5555-4555-8555-555555555555"

    fun seed(conn: Connection) {
        conn.prepareStatement("INSERT OR IGNORE INTO fixture_user(id, display_name) VALUES (?, ?)").use {
            it.setString(1, USER_ID)
            it.setString(2, "Fixture")
            it.executeUpdate()
        }
        conn.prepareStatement("INSERT OR IGNORE INTO fixture_exercise(id, canonical_name) VALUES (?, ?)").use {
            it.setString(1, EXERCISE_ID)
            it.setString(2, "Bench Press")
            it.executeUpdate()
        }
        conn.prepareStatement("INSERT OR IGNORE INTO fixture_workout(id, user_id, name) VALUES (?, ?, ?)").use {
            it.setString(1, WORKOUT_ID)
            it.setString(2, USER_ID)
            it.setString(3, "Chest / Triceps")
            it.executeUpdate()
        }
        conn.prepareStatement("INSERT OR IGNORE INTO fixture_session(id, user_id, workout_id) VALUES (?, ?, ?)").use {
            it.setString(1, SESSION_ID)
            it.setString(2, USER_ID)
            it.setString(3, WORKOUT_ID)
            it.executeUpdate()
        }
    }
}
