package com.gymbuddy.persist

import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.events.WorkoutEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Typed round-trip. Unknown eventType or malformed payload fails;
 * it is never coerced into another event.
 */
object WorkoutEventCodec {
    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun toEntity(event: WorkoutEvent, sequence: Int): WorkoutEventEntity {
        val type = event.type.name
        require(type in CommittedEventType.entries.map { it.name })
        return WorkoutEventEntity(
            eventId = event.eventId,
            sessionId = event.sessionId ?: "",
            userId = event.userId,
            eventType = type,
            schemaVersion = event.schemaVersion,
            sequence = sequence,
            payloadJson = json.encodeToString(JsonObject.serializer(), event.payload),
            occurredAt = event.occurredAt,
            recordedAt = event.recordedAt,
            source = event.source.name,
            transcriptId = event.transcriptId,
            language = event.language,
            confidence = event.confidence,
            sttFinalHash = event.sttFinalHash,
            batchId = event.batchId,
        )
    }

    fun fromEntity(entity: WorkoutEventEntity): WorkoutEvent {
        val type = runCatching { CommittedEventType.valueOf(entity.eventType) }
            .getOrElse { error("unknown persisted eventType=${entity.eventType}") }
        val source = runCatching { EventSource.valueOf(entity.source) }
            .getOrElse { error("unknown persisted source=${entity.source}") }
        val payload = runCatching {
            json.parseToJsonElement(entity.payloadJson).jsonObject
        }.getOrElse { error("malformed payload_json for ${entity.eventId}: ${it.message}") }
        require(entity.schemaVersion == WorkoutEvent.SCHEMA_VERSION) {
            "unsupported schemaVersion=${entity.schemaVersion}"
        }
        return WorkoutEvent(
            eventId = entity.eventId,
            sessionId = entity.sessionId.ifEmpty { null },
            userId = entity.userId,
            occurredAt = entity.occurredAt,
            recordedAt = entity.recordedAt,
            source = source,
            schemaVersion = entity.schemaVersion,
            type = type,
            payload = payload,
            transcriptId = entity.transcriptId,
            language = entity.language,
            confidence = entity.confidence,
            sttFinalHash = entity.sttFinalHash,
            batchId = entity.batchId,
        )
    }
}
