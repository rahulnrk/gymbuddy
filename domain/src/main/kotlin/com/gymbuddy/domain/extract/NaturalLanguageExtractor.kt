package com.gymbuddy.domain.extract

import com.gymbuddy.domain.ai.LlmMessage
import com.gymbuddy.domain.events.CandidateEventEnvelope
import com.gymbuddy.domain.safety.SafetyFirstTurn
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Natural language → [CandidateEventEnvelope].
 *
 * SafetyFirstTurn always runs first. Does not validate, commit, or mutate
 * workout state. CandidateEventEnvelope is an internal interpretation, not
 * a restriction on the user's vocabulary.
 */
class NaturalLanguageExtractor(
    private val safetyGate: SafetyFirstTurn = SafetyFirstTurn(),
    private val fake: SchemaConstrainedFakeExtractor = SchemaConstrainedFakeExtractor(),
) {
    fun extract(request: ExtractionRequest): ExtractionResult {
        val intents = RulesFirstIntent.detect(request.transcript)
        val turn = safetyGate.process(request.transcript, extract = {
            extractAfterSafety(request, intents)
        })
        if (turn.decision.fired) {
            // safety is authoritative; still keep allowed set facts
        }
        return ExtractionResult(
            safety = turn.decision,
            speech = turn.speech,
            intents = intents + if (turn.decision.fired) setOf(IntentLabel.SAFETY) else emptySet(),
            envelope = turn.extracted,
            llmRequired = isLlmRequired(request.transcript),
            classifiedBeforeExtract = turn.classifiedBeforeExtract,
        )
    }

    private fun extractAfterSafety(
        request: ExtractionRequest,
        intents: Set<IntentLabel>,
    ): CandidateEventEnvelope {
        val rules = EnglishLastSetFallback.extract(request, intents)
        if (rules.candidates.isNotEmpty() && !isLlmRequiredMultiSet(request.transcript)) {
            return rules
        }
        val fromFake = fake.extractCandidates(
            listOf(LlmMessage("user", request.transcript)),
            buildJsonObject { put("title", "candidate-event") },
            fake.temperature,
        )
        if (fromFake.candidates.isNotEmpty()) return fromFake
        return rules
    }

    private fun isLlmRequired(transcript: String): Boolean {
        val n = transcript.lowercase()
        return isLlmRequiredMultiSet(transcript) ||
            (n.contains("nenu") && n.contains("kilos"))
    }

    private fun isLlmRequiredMultiSet(transcript: String): Boolean {
        val n = transcript.lowercase()
        return n.contains("first") && n.contains("second")
    }
}
