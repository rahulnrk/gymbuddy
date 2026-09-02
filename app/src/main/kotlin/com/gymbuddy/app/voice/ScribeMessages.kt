package com.gymbuddy.app.voice

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.Json

sealed class ScribeEvent {
    data class Partial(val text: String) : ScribeEvent()
    data class Committed(val text: String) : ScribeEvent()
    data class Started(val sessionId: String) : ScribeEvent()
    data class Error(val kind: String, val detail: String) : ScribeEvent()
    data object Other : ScribeEvent()
}

object ScribeMessages {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(message: String): ScribeEvent {
        val obj = runCatching { json.parseToJsonElement(message).jsonObject }.getOrNull()
            ?: return ScribeEvent.Other
        val type = obj["message_type"]?.jsonPrimitive?.contentOrNull ?: return ScribeEvent.Other
        val text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val error = obj["error"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return when (type) {
            "partial_transcript" -> ScribeEvent.Partial(text)
            "committed_transcript",
            "committed_transcript_with_timestamps",
            -> ScribeEvent.Committed(text)
            "session_started" -> ScribeEvent.Started(
                obj["session_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
            "auth_error",
            "quota_exceeded",
            "error",
            "rate_limited",
            "invalid_request",
            "input_error",
            "transcriber_error",
            "session_time_limit_exceeded",
            -> ScribeEvent.Error(type, error.ifBlank { type })
            else -> ScribeEvent.Other
        }
    }
}
