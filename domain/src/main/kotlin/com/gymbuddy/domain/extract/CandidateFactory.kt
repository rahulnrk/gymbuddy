package com.gymbuddy.domain.extract

import com.gymbuddy.domain.CandidateType
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.events.CandidateEvent
import com.gymbuddy.domain.events.CandidateEventEnvelope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

internal object CandidateFactory {
    fun envelope(transcript: String, items: List<CandidateEvent>): CandidateEventEnvelope =
        CandidateEventEnvelope(CandidateEventEnvelope.SCHEMA_VERSION, items)

    fun event(
        transcript: String,
        index: Int,
        type: CandidateType,
        payload: JsonObject,
        fieldConfidence: Map<String, Double> = emptyMap(),
        loggingMode: LoggingMode? = null,
        setNumberHint: JsonElement? = null,
        overall: Double = 0.9,
        exerciseNameRaw: String? = null,
    ): CandidateEvent = CandidateEvent(
        candidateId = id(transcript, type, index),
        type = type,
        overallConfidence = overall,
        fieldConfidence = fieldConfidence,
        exerciseNameRaw = exerciseNameRaw,
        setNumberHint = setNumberHint,
        loggingMode = loggingMode,
        payload = payload,
    )

    fun hint(value: String): JsonPrimitive = JsonPrimitive(value)

    fun id(transcript: String, type: CandidateType, index: Int): String =
        UUID.nameUUIDFromBytes("$transcript|${type.name}|$index".toByteArray()).toString()

    val empty: CandidateEventEnvelope =
        CandidateEventEnvelope(CandidateEventEnvelope.SCHEMA_VERSION, emptyList())
}

internal object NumberWords {
    val map: Map<String, Int> = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12,
    )

    fun parse(token: String): Int? =
        token.toIntOrNull() ?: map[token.lowercase()]
}
