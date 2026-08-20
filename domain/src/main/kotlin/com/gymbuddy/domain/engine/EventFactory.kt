package com.gymbuddy.domain.engine

import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.events.WorkoutEvent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

internal object EventFactory {
    fun event(
        type: CommittedEventType,
        userId: String,
        sessionId: String?,
        payload: JsonObject,
        source: EventSource,
        now: String,
        batchId: String? = null,
        eventId: String = UUID.randomUUID().toString(),
    ): WorkoutEvent = WorkoutEvent(
        eventId = eventId,
        sessionId = sessionId,
        userId = userId,
        occurredAt = now,
        recordedAt = now,
        source = source,
        type = type,
        payload = payload,
        batchId = batchId,
    )

    fun payload(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
        for ((k, v) in pairs) {
            when (v) {
                null -> put(k, JsonNull)
                is String -> put(k, v)
                is Int -> put(k, v)
                is Long -> put(k, v)
                is Double -> put(k, v)
                is Boolean -> put(k, v)
                is LoggingMode -> put(k, v.name.lowercase())
                is JsonObject -> put(k, v)
                is JsonElement -> put(k, v)
                else -> put(k, JsonPrimitive(v.toString()))
            }
        }
    }
}
