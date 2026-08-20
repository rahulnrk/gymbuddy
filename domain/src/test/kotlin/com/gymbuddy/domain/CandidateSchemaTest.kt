package com.gymbuddy.domain

import com.gymbuddy.domain.events.CandidateEventEnvelope
import com.gymbuddy.domain.events.SetCompletedCandidate
import com.gymbuddy.domain.validation.CandidateSchemaGate
import com.gymbuddy.domain.validation.SchemaGateResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFails

class CandidateSchemaTest {
    @Test
    fun got10_validEnvelopeDecodesRepsAsInt() {
        val raw = """
            {
              "schemaVersion": 1,
              "candidates": [{
                "candidateId": "77777777-7777-4777-8777-777777777777",
                "type": "SET_COMPLETED",
                "overallConfidence": 0.92,
                "fieldConfidence": { "reps": 0.96 },
                "loggingMode": "live",
                "payload": { "reps": 10 }
              }]
            }
        """.trimIndent()
        val envelope = CandidateEventEnvelope.parse(raw)
        val payload = envelope.candidates.single().decodePayload()
        assertIs<SetCompletedCandidate>(payload)
        assertEquals(10, payload.reps)
    }

    @Test
    fun tenAsString_failsSchemaGate() {
        val raw = """
            {
              "schemaVersion": 1,
              "candidates": [{
                "candidateId": "77777777-7777-4777-8777-777777777777",
                "type": "SET_COMPLETED",
                "overallConfidence": 0.9,
                "fieldConfidence": { "reps": 0.9 },
                "payload": { "reps": "ten" }
              }]
            }
        """.trimIndent()
        val result = CandidateSchemaGate.inspect(raw)
        assertIs<SchemaGateResult.Invalid>(result)
    }

    @Test
    fun unknownType_failsEnumDecode() {
        val raw = """
            {
              "schemaVersion": 1,
              "candidates": [{
                "candidateId": "77777777-7777-4777-8777-777777777777",
                "type": "HALLUCINATED_PR",
                "overallConfidence": 0.99,
                "fieldConfidence": {},
                "payload": { "weightKg": 200 }
              }]
            }
        """.trimIndent()
        assertFails { CandidateEventEnvelope.parse(raw) }
    }

    @Test
    fun extraEnvelopeKey_rejected() {
        val raw = """
            {
              "schemaVersion": 1,
              "insertIntoSets": true,
              "candidates": []
            }
        """.trimIndent()
        assertFails { CandidateEventEnvelope.parse(raw) }
    }

    @Test
    fun commandSkip_isCandidateNotCommittedEvent() {
        val raw = """
            {
              "schemaVersion": 1,
              "candidates": [{
                "candidateId": "77777777-7777-4777-8777-777777777777",
                "type": "COMMAND",
                "overallConfidence": 0.85,
                "fieldConfidence": { "kind": 0.85 },
                "payload": { "kind": "skip" }
              }]
            }
        """.trimIndent()
        val envelope = CandidateEventEnvelope.parse(raw)
        assertEquals(CandidateType.COMMAND, envelope.candidates.single().type)
        assertTrue(CommittedEventType.EXERCISE_SKIPPED.name != envelope.candidates.single().type.name)
    }
}
