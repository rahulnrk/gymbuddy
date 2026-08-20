package com.gymbuddy.domain.events

import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.EventId
import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.UserId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Committed append-only event. schemaVersion = 1.
 *
 * Produced only by the engine / safety / voice after validation.
 * The LLM never constructs this type as session truth.
 */
@Serializable
data class WorkoutEvent(
    val eventId: String,
    val sessionId: String?,
    val userId: String,
    val occurredAt: String,
    val recordedAt: String,
    val source: EventSource,
    val schemaVersion: Int = SCHEMA_VERSION,
    val type: CommittedEventType,
    val payload: JsonObject,
    val transcriptId: String? = null,
    val language: String? = null,
    val confidence: Double? = null,
    val sttFinalHash: String? = null,
    val batchId: String? = null,
) {
    init {
        require(schemaVersion == SCHEMA_VERSION)
        require(type != CommittedEventType.SET_STARTED || source == EventSource.SYSTEM) {
            "SET_STARTED is engine-emitted (source=system), never LLM"
        }
    }

    fun id(): EventId = EventId(eventId)
    fun user(): UserId = UserId(userId)
    fun session(): SessionId? = sessionId?.let(::SessionId)

    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/** Idempotency key: (userId, eventId). Re-delivery is a no-op. */
data class EventIdempotencyKey(val userId: UserId, val eventId: EventId)
