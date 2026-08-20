package com.gymbuddy.domain.persist

import com.gymbuddy.domain.EventId
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.UserId
import com.gymbuddy.domain.events.WorkoutEvent

/**
 * Persistence ports. Room lives in :persist (PR 3). The engine depends only
 * on these interfaces. Conversation/LLM packages must not depend on this
 * package except through application services.
 */
interface EventLog {
    fun append(event: WorkoutEvent): AppendAck
    fun find(userId: UserId, eventId: EventId): WorkoutEvent?
    fun sessionEvents(sessionId: SessionId): List<WorkoutEvent>

    /** Same as [append]; unique (userId, eventId) makes a second write a duplicate. */
    fun appendIfAbsent(event: WorkoutEvent): AppendAck = append(event)

    /** Ordered committed events for a session (sequence ascending). */
    fun load(sessionId: SessionId): List<WorkoutEvent> = sessionEvents(sessionId)

    /** Events with sequence strictly greater than [version]. */
    fun loadFromVersion(sessionId: SessionId, version: Int): List<WorkoutEvent> =
        sessionEvents(sessionId).drop(version.coerceAtLeast(0))

    /** Max sequence for the session, or 0 if none. */
    fun latestVersion(sessionId: SessionId): Int = sessionEvents(sessionId).size
}

sealed class AppendAck {
    data class Written(val eventId: EventId) : AppendAck()
    data class Duplicate(val eventId: EventId) : AppendAck()
}

interface SessionSnapshotStore {
    fun load(sessionId: SessionId): ByteArray?
    fun save(sessionId: SessionId, snapshot: ByteArray)
}

/** MVP: local SQLite is session truth. No sync server. */
interface LocalSessionTruth {
    val isRemoteAuthoritative: Boolean get() = false
}
