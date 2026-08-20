package com.gymbuddy.domain.loop

import com.gymbuddy.domain.EpistemicStance
import com.gymbuddy.domain.VoiceTurnState
import com.gymbuddy.domain.WeightUnit
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.context.ConversationalStateContext
import com.gymbuddy.domain.context.EpistemicFact
import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.context.HistoricalStateContext
import com.gymbuddy.domain.context.UserStateContext
import com.gymbuddy.domain.context.WorkoutStateContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Part 7: a coaching line must be checkable against its own evidence.
 * These tests fail when the statement is false even though the sentence reads
 * fine - that is the entire point of claim-level citation.
 */
class ClaimVerifierTest {

    private fun fact(id: String, reps: Int, kg: Double, allowed: Boolean = true, stance: EpistemicStance = EpistemicStance.KNOWN) =
        EpistemicFact(
            id = id,
            kind = "set",
            stance = stance,
            value = buildJsonObject { put("reps", reps); put("weightKg", kg) },
            sourceRowIds = listOf(id),
            speechAllowed = allowed,
        )

    private fun ctx(vararg facts: EpistemicFact) = FourContexts(
        workout = WorkoutStateContext(
            sessionId = "s", workoutId = "w", workoutName = "",
            sessionState = WorkoutSessionState.RESTING, voiceState = VoiceTurnState.SILENT,
            remainingSetsPlanned = 2, startedAt = "2026-08-20T16:00:00Z",
        ),
        historical = HistoricalStateContext(),
        conversational = ConversationalStateContext(languageDetected = "en"),
        user = UserStateContext("u", emptyList(), "", emptyList(), WeightUnit.KG, "en"),
        facts = facts.toList(),
    )

    @Test
    fun supportedRepDeltaPasses() {
        val a = fact("cur", 10, 60.0)
        val b = fact("prev", 9, 60.0)
        val s = CoachingStatement("Nice. One more rep than your last set.", listOf(
            Claim.RepDelta(10, 9, Baseline.PREVIOUS_SET_THIS_SESSION, listOf(a, b)),
        ))
        assertEquals(emptyList(), ClaimVerifier.verify(s, ctx(a, b)))
    }

    /** The sentence reads fine. The numbers do not support it. Must fail. */
    @Test
    fun repDeltaOfZeroIsRejectedEvenThoughSentenceLooksReasonable() {
        val a = fact("cur", 10, 60.0)
        val b = fact("prev", 10, 60.0)
        val s = CoachingStatement("Nice. One more rep than your last set.", listOf(
            Claim.RepDelta(10, 10, Baseline.PREVIOUS_SET_THIS_SESSION, listOf(a, b)),
        ))
        val v = ClaimVerifier.verify(s, ctx(a, b))
        assertTrue(v.any { "delta of 0" in it }, v.toString())
    }

    @Test
    fun claimCitingNumbersAbsentFromEvidenceIsRejected() {
        val a = fact("cur", 10, 60.0)
        val b = fact("prev", 9, 60.0)
        // asserts 12 vs 7 - neither is in the cited rows
        val s = CoachingStatement("Five more reps than your last set.", listOf(
            Claim.RepDelta(12, 7, Baseline.PREVIOUS_SET_THIS_SESSION, listOf(a, b)),
        ))
        val v = ClaimVerifier.verify(s, ctx(a, b))
        assertTrue(v.any { "appears in cited rows" in it }, v.toString())
    }

    @Test
    fun claimWithNoEvidenceIsRejected() {
        val s = CoachingStatement("You're up 2.5 kilos from last week.", listOf(
            Claim.WeightDelta(62.5, 60.0, Baseline.PREVIOUS_SESSION, emptyList()),
        ))
        val v = ClaimVerifier.verify(s, ctx())
        assertTrue(v.any { "no supporting evidence" in it }, v.toString())
    }

    @Test
    fun citingAFactNotInContextIsRejected() {
        val ghost = fact("ghost", 9, 60.0)
        val a = fact("cur", 10, 60.0)
        val s = CoachingStatement("Nice. One more rep than your last set.", listOf(
            Claim.RepDelta(10, 9, Baseline.PREVIOUS_SET_THIS_SESSION, listOf(a, ghost)),
        ))
        val v = ClaimVerifier.verify(s, ctx(a))
        assertTrue(v.any { "cites unknown fact" in it }, v.toString())
    }

    @Test
    fun citingANonSpeakableFactIsRejected() {
        val a = fact("cur", 10, 60.0)
        val muted = fact("prev", 9, 60.0, allowed = false)
        val s = CoachingStatement("Nice. One more rep than your last set.", listOf(
            Claim.RepDelta(10, 9, Baseline.PREVIOUS_SET_THIS_SESSION, listOf(a, muted)),
        ))
        val v = ClaimVerifier.verify(s, ctx(a, muted))
        assertTrue(v.any { "non-speakable" in it }, v.toString())
    }

    @Test
    fun historicalSetClaimMustMatchTheStoredRow() {
        val h = fact("fact:hist:set:x", 10, 60.0)
        val wrong = CoachingStatement("Last time you did 65 kilos for 10.", listOf(
            Claim.HistoricalSet("2026-08-13", 65.0, 10, listOf(h)),
        ))
        assertTrue(ClaimVerifier.verify(wrong, ctx(h)).any { "not in cited rows" in it })
    }

    @Test
    fun noHistoryClaimNeedsNoEvidence() {
        val s = CoachingStatement("I don't have a previous number for that yet.", listOf(Claim.NoHistory))
        assertEquals(emptyList(), ClaimVerifier.verify(s, ctx()))
    }
}
