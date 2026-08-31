package com.gymbuddy.domain.loop

import com.gymbuddy.domain.EpistemicStance
import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
import com.gymbuddy.domain.safety.SafetySpeechDirective

/**
 * Deterministic templates. Not an LLM.
 *
 * Every line that asserts a number is generated from [Claim]s and returns them
 * with the text, so [ClaimVerifier] can check the sentence against its own
 * evidence. Lines that assert nothing carry no claims.
 */
object CoachingPhraser {
    val banned = listOf(
        "Command received.",
        "Your set has been logged.",
        "Proceed to the next exercise.",
    )

    fun phrase(
        speak: SpeakDecision,
        safety: SafetySpeechDirective,
        contexts: FourContexts,
        recommendation: Recommendation?,
        snapshot: WorkoutSessionSnapshot? = null,
    ): CoachingStatement {
        val statement = build(speak, safety, contexts, recommendation, snapshot)
        statement.text?.let { t -> if (banned.any { it in t }) error("banned IVR string") }
        return statement
    }

    private fun build(
        speak: SpeakDecision,
        safety: SafetySpeechDirective,
        contexts: FourContexts,
        recommendation: Recommendation?,
        snapshot: WorkoutSessionSnapshot?,
    ): CoachingStatement = when (speak) {
        is SpeakDecision.Silence -> CoachingStatement.silent
        is SpeakDecision.Speak -> when (speak.actId) {
            SpeechActId.SAFETY -> CoachingStatement.plain(
                safety.text ?: "Stop the exercise and seek appropriate medical attention.",
            )
            SpeechActId.USEFUL_DELTA -> usefulDelta(snapshot, contexts)
            SpeechActId.FIRST_SET_OF_EXERCISE -> CoachingStatement.plain("Nice.")
            SpeechActId.CORRECTION_ACK -> CoachingStatement.plain("Got it.")
            SpeechActId.ANSWER_QUESTION -> answerHistory(contexts)
            SpeechActId.CLARIFICATION -> CoachingStatement.plain("Was that the weight or the reps?")
            SpeechActId.WEIGHT_CHANGE_ACK -> CoachingStatement.plain("Got it, we'll use that weight.")
            SpeechActId.SKIP_ACK -> CoachingStatement.plain("Okay, skipping this one.")
            SpeechActId.EXTRA_SET_ACK -> CoachingStatement.plain("Okay, one more.")
            SpeechActId.SUBSTITUTION_PROPOSE -> CoachingStatement.plain("We can switch if you want.")
            SpeechActId.SUBSTITUTION_APPLIED -> CoachingStatement.plain("Okay, swapped.")
            SpeechActId.SESSION_GREETING -> CoachingStatement.plain("Let's get started.")
            SpeechActId.SESSION_RESUME -> CoachingStatement.plain("Welcome back.")
            SpeechActId.SESSION_COMPLETE -> CoachingStatement.plain("That's the session.")
            SpeechActId.PAUSE_ACK -> CoachingStatement.plain("Paused.")
            SpeechActId.SUGGEST_WEIGHT_THIS_SESSION -> suggestWeight(recommendation)
            SpeechActId.OFFLINE_VOICE_UNAVAILABLE -> CoachingStatement.plain(
                "I can't hear you until you're back online - tap the numbers if you want.",
            )
        }
    }

    /**
     * Phrased from the actual delta. +1, +2, -1 and a weight-only change all
     * produce different wording; a delta of zero or an unknown baseline
     * produces no claim rather than a false one.
     */
    private fun usefulDelta(snapshot: WorkoutSessionSnapshot?, contexts: FourContexts): CoachingStatement {
        val snap = snapshot ?: return CoachingStatement.plain("Nice.")
        val analysis = Deltas.of(snap, contexts)
        val text = DeltaPhrase.phrase(analysis.reps, analysis.weight)
            ?: return CoachingStatement.plain("Nice.")
        return CoachingStatement(text, analysis.claims)
    }

    /**
     * Quotes a stored set. Ordering is explicit (most recent session first);
     * previously this read whichever session happened to be first in the list.
     */
    private fun answerHistory(contexts: FourContexts): CoachingStatement {
        val none = CoachingStatement("I don't have a previous number for that yet.", listOf(Claim.NoHistory))
        val prior = Recommend.mostRecentPreviousSession(contexts) ?: return none
        val known = prior.sets.filter { it.weightKg != null && it.reps != null }
        if (known.isEmpty()) return none
        val last = known.maxByOrNull { it.setNumber } ?: known.last()
        val fact = contexts.facts.firstOrNull { it.id == "fact:hist:set:${last.setId}" }
        if (fact == null || !fact.speechAllowed || fact.stance != EpistemicStance.KNOWN) return none
        val claim = Claim.HistoricalSet(prior.occurredOn, last.weightKg, last.reps, listOf(fact))
        return CoachingStatement("Last time you did ${trim(last.weightKg!!)} kilos for ${last.reps}.", listOf(claim))
    }

    private fun suggestWeight(rec: Recommendation?): CoachingStatement {
        val kg = rec?.weightKg ?: return CoachingStatement.silent
        return CoachingStatement("Try ${trim(kg)} next set if you want.", rec.claims)
    }

    private fun trim(v: Double): String =
        if (kotlin.math.abs(v - v.toInt()) < 1e-9) v.toInt().toString() else v.toString()
}
