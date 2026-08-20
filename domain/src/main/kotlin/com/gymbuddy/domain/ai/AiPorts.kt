package com.gymbuddy.domain.ai

import com.gymbuddy.domain.events.CandidateEventEnvelope
import kotlinx.serialization.json.JsonObject

data class LlmMessage(val role: String, val content: String)

/**
 * LLM port. Extraction MUST pass the frozen CandidateEvent JSON schema.
 * Return type is CandidateEventEnvelope, never arbitrary JSON that writes the DB.
 */
interface LlmProvider {
    fun extractCandidates(
        messages: List<LlmMessage>,
        schema: JsonObject,
        temperature: Double = 0.2,
    ): CandidateEventEnvelope

    fun phraseCoaching(
        messages: List<LlmMessage>,
        temperature: Double = 0.6,
    ): String
}

data class RetrievalHit(
    val index: String,
    val id: String,
    val title: String,
    val text: String,
    val score: Double,
)

interface RetrievalProvider {
    fun query(index: String, q: String, k: Int): List<RetrievalHit>
}

interface EmbeddingProvider {
    fun embed(texts: List<String>): List<List<Double>>
}
