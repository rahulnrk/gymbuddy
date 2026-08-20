package com.gymbuddy.domain.extract

import com.gymbuddy.domain.ai.LlmMessage
import com.gymbuddy.domain.ai.LlmProvider
import com.gymbuddy.domain.events.CandidateEventEnvelope
import kotlinx.serialization.json.JsonObject

/**
 * Schema-constrained fake standing in for a future LLM extractor.
 * temperature must be ≤ 0.2. No network. No vendor. Deterministic fixtures.
 */
class SchemaConstrainedFakeExtractor(
    val temperature: Double = 0.2,
) : LlmProvider {
    init {
        require(temperature <= 0.2) { "extract temperature must be <= 0.2" }
    }

    override fun extractCandidates(
        messages: List<LlmMessage>,
        schema: JsonObject,
        temperature: Double,
    ): CandidateEventEnvelope {
        require(temperature <= 0.2) { "extract temperature must be <= 0.2" }
        val text = messages.lastOrNull { it.role == "user" }?.content?.trim().orEmpty()
        return envelopeFor(text)
    }

    override fun phraseCoaching(messages: List<LlmMessage>, temperature: Double): String = ""

    fun envelopeFor(transcript: String): CandidateEventEnvelope {
        val key = transcript.trim().lowercase()
        val json = fixtures[key] ?: return CandidateFactory.empty
        return try {
            CandidateEventEnvelope.parse(json)
        } catch (_: Exception) {
            CandidateFactory.empty
        }
    }

    companion object {
        private val fixtures: Map<String, String> = mapOf(
            "first set was 12 at 40, second was 10, and i got 8 on this one." to """
                {"schemaVersion":1,"candidates":[
                  {"candidateId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1","type":"SET_COMPLETED","overallConfidence":0.9,"fieldConfidence":{"reps":0.9,"weight":0.9},"setNumberHint":"first","loggingMode":"catch_up","payload":{"reps":12,"weight":40,"weightUnit":"kg"}},
                  {"candidateId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2","type":"SET_COMPLETED","overallConfidence":0.9,"fieldConfidence":{"reps":0.9},"setNumberHint":"second","loggingMode":"catch_up","payload":{"reps":10}},
                  {"candidateId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3","type":"SET_COMPLETED","overallConfidence":0.9,"fieldConfidence":{"reps":0.9},"setNumberHint":"this","loggingMode":"catch_up","payload":{"reps":8}}
                ]}
            """.trimIndent(),
            "nenu 60 kilos tho eight reps chesa." to """
                {"schemaVersion":1,"candidates":[
                  {"candidateId":"77777777-7777-4777-8777-777777777777","type":"SET_COMPLETED","overallConfidence":0.92,"fieldConfidence":{"reps":0.95},"payload":{"reps":8,"weight":60,"weightUnit":"kg"},"loggingMode":"live"}
                ]}
            """.trimIndent(),
        )
    }
}
