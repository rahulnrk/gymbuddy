package com.gymbuddy.persist

import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class SchemaMigrationTest {

    @Test
    fun versionIsOneAndNotDestructive() {
        assertEquals(1, GymBuddyDatabase.VERSION)
        val db = GymBuddyDatabase.inMemory()
        db.connection.createStatement().use { st ->
            st.executeQuery("SELECT version FROM schema_version").use { rs ->
                rs.next()
                assertEquals(1, rs.getInt(1))
            }
            st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'").use { rs ->
                val names = mutableSetOf<String>()
                while (rs.next()) names += rs.getString(1)
                assertTrue("event_log" in names)
                assertTrue("schema_version" in names)
                assertTrue("fixture_user" in names)
                assertTrue("validated_fact" !in names)
                assertTrue("workout_session_snapshot" !in names)
            }
        }
        db.close()
    }

    @Test
    fun reopenDoesNotWipeAndDoesNotUseDestructiveMigration() {
        val file = java.io.File.createTempFile("gymbuddy-mig", ".db")
        file.deleteOnExit()
        val db1 = GymBuddyDatabase.file(file.absolutePath)
        db1.workoutEventDao.insert(PersistTestSupport.entity("keep-me", 1))
        db1.close()
        val db2 = GymBuddyDatabase.file(file.absolutePath)
        assertEquals(listOf("keep-me"), db2.workoutEventDao.loadBySession(PersistTestSupport.session).map { it.eventId })
        assertEquals(1, SchemaMigrator.currentVersion(db2.connection))
        db2.close()
    }

    @Test
    fun unknownUpgradeIsRejectedNotDropped() {
        Class.forName("org.sqlite.JDBC")
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        SchemaMigrator.migrate(conn, 1)
        val thrown = assertFails { SchemaMigrator.migrate(conn, 2) }
        assertTrue("destructive" in thrown.message.orEmpty() || "no migration" in thrown.message.orEmpty())
        conn.close()
    }

    @Test
    fun v1SqlIsTheOnlyEventLogDdl() {
        val statements = SchemaSql.statements()
        assertTrue(statements.any { it.startsWith("CREATE TABLE IF NOT EXISTS event_log") })
        assertTrue(statements.any { "fixture_user" in it })
        assertEquals(SchemaSql.VERSION, GymBuddyDatabase.VERSION)
        val raw = SchemaSql.ddl()
        assertTrue("-- GymBuddy persist schema version 1" in raw)
    }

    @Test
    fun downwardMigrateRejected() {
        Class.forName("org.sqlite.JDBC")
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        SchemaMigrator.migrate(conn, 1)
        val thrown = assertFails { SchemaMigrator.migrate(conn, 0) }
        assertTrue("downward" in thrown.message.orEmpty())
        conn.close()
    }
}
