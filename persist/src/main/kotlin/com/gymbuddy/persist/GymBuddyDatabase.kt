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
        const val VERSION = SchemaSql.VERSION

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
            SchemaSql.apply { sql -> st.execute(sql) }
        }
    }
}

object PersistFixtures {
    const val USER_ID = "11111111-1111-4111-8111-111111111111"
    const val WORKOUT_ID = "33333333-3333-4333-8333-333333333333"
    const val EXERCISE_ID = "44444444-4444-4444-8444-444444444444"
    const val SESSION_ID = "22222222-2222-4222-8222-222222222222"
    const val WORKOUT_EXERCISE_ID = "55555555-5555-4555-8555-555555555555"

    fun seedStatements(): List<Pair<String, Array<String>>> = listOf(
        "INSERT OR IGNORE INTO fixture_user(id, display_name) VALUES (?, ?)" to
            arrayOf(USER_ID, "Fixture"),
        "INSERT OR IGNORE INTO fixture_exercise(id, canonical_name) VALUES (?, ?)" to
            arrayOf(EXERCISE_ID, "Bench Press"),
        "INSERT OR IGNORE INTO fixture_workout(id, user_id, name) VALUES (?, ?, ?)" to
            arrayOf(WORKOUT_ID, USER_ID, "Chest / Triceps"),
        "INSERT OR IGNORE INTO fixture_session(id, user_id, workout_id) VALUES (?, ?, ?)" to
            arrayOf(SESSION_ID, USER_ID, WORKOUT_ID),
    )

    fun seed(exec: (sql: String, args: Array<String>) -> Unit) {
        seedStatements().forEach { (sql, args) -> exec(sql, args) }
    }

    fun seed(conn: Connection) {
        seed { sql, args ->
            conn.prepareStatement(sql).use { ps ->
                args.forEachIndexed { i, v -> ps.setString(i + 1, v) }
                ps.executeUpdate()
            }
        }
    }
}
