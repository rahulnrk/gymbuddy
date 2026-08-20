package com.gymbuddy.persist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutEventDaoTest {

    @Test
    fun insertAndOrderedRetrieval() {
        val db = GymBuddyDatabase.inMemory()
        val dao = db.workoutEventDao
        dao.insert(PersistTestSupport.entity("e1", 1))
        dao.insert(PersistTestSupport.entity("e2", 2, type = "WORKOUT_STARTED"))
        dao.insert(PersistTestSupport.entity("e3", 3, type = "EXERCISE_STARTED"))
        val loaded = dao.loadBySession(PersistTestSupport.session)
        assertEquals(listOf("e1", "e2", "e3"), loaded.map { it.eventId })
        assertEquals(listOf(1, 2, 3), loaded.map { it.sequence })
        db.close()
    }

    @Test
    fun queryBySessionExcludesOtherSessions() {
        val db = GymBuddyDatabase.inMemory()
        val dao = db.workoutEventDao
        dao.insert(PersistTestSupport.entity("a1", 1, sessionId = "sess-a"))
        dao.insert(PersistTestSupport.entity("b1", 1, sessionId = "sess-b"))
        dao.insert(PersistTestSupport.entity("a2", 2, sessionId = "sess-a"))
        assertEquals(listOf("a1", "a2"), dao.loadBySession("sess-a").map { it.eventId })
        assertEquals(listOf("b1"), dao.loadBySession("sess-b").map { it.eventId })
        db.close()
    }

    @Test
    fun latestVersionAndLoadFromVersion() {
        val db = GymBuddyDatabase.inMemory()
        val dao = db.workoutEventDao
        assertEquals(0, dao.latestVersion(PersistTestSupport.session))
        dao.insert(PersistTestSupport.entity("e1", 1))
        dao.insert(PersistTestSupport.entity("e2", 2))
        dao.insert(PersistTestSupport.entity("e3", 3))
        assertEquals(3, dao.latestVersion(PersistTestSupport.session))
        assertEquals(listOf("e2", "e3"), dao.loadFromVersion(PersistTestSupport.session, 1).map { it.eventId })
        assertEquals(emptyList(), dao.loadFromVersion(PersistTestSupport.session, 3).map { it.eventId })
        db.close()
    }

    @Test
    fun duplicateEventIdRejectedByPrimaryKey() {
        val db = GymBuddyDatabase.inMemory()
        val dao = db.workoutEventDao
        dao.insert(PersistTestSupport.entity("dup", 1))
        val thrown = assertFails { dao.insert(PersistTestSupport.entity("dup", 2)) }
        assertTrue(thrown.message.orEmpty().contains("UNIQUE", ignoreCase = true) || thrown is java.sql.SQLException)
        assertEquals(1, dao.loadBySession(PersistTestSupport.session).size)
        db.close()
    }

    @Test
    fun idempotencyConstraintOnUserAndEventId() {
        val db = GymBuddyDatabase.inMemory()
        db.connection.createStatement().use { st ->
            st.executeQuery(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name='event_log'",
            ).use { rs ->
                rs.next()
                val sql = rs.getString(1)
                val compact = sql.replace("\\s+".toRegex(), "")
                assertTrue("UNIQUE(user_id,event_id)" in compact, sql)
                assertTrue("UNIQUE(session_id,sequence)" in compact, sql)
            }
        }
        db.close()
    }

    @Test
    fun duplicateSequenceRejected() {
        val db = GymBuddyDatabase.inMemory()
        val dao = db.workoutEventDao
        dao.insert(PersistTestSupport.entity("e1", 1))
        val thrown = assertFails { dao.insert(PersistTestSupport.entity("e2", 1)) }
        assertTrue(thrown is java.sql.SQLException)
        assertEquals(listOf(1), dao.loadBySession(PersistTestSupport.session).map { it.sequence })
        db.close()
    }

    @Test
    fun findByUserAndEventId() {
        val db = GymBuddyDatabase.inMemory()
        val dao = db.workoutEventDao
        dao.insert(PersistTestSupport.entity("find-me", 1))
        assertEquals("find-me", dao.find(PersistTestSupport.user, "find-me")?.eventId)
        assertNull(dao.find(PersistTestSupport.user, "missing"))
        assertEquals(1, dao.countById(PersistTestSupport.user, "find-me"))
        assertEquals(0, dao.countById(PersistTestSupport.user, "missing"))
        db.close()
    }

    @Test
    fun loadOrderIsSequenceNotInsertionTime() {
        val db = GymBuddyDatabase.inMemory()
        val dao = db.workoutEventDao
        // Insert out of wall-clock order but sequence is authoritative.
        dao.insert(PersistTestSupport.entity("late", 2))
        dao.insert(PersistTestSupport.entity("early", 1))
        dao.insert(PersistTestSupport.entity("last", 3))
        assertEquals(listOf("early", "late", "last"), dao.loadBySession(PersistTestSupport.session).map { it.eventId })
        db.close()
    }
}
