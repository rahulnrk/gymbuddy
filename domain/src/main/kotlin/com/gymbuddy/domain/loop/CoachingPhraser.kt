package com.gymbuddy.domain.loop

import com.gymbuddy.domain.EpistemicStance
import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.safety.SafetySpeechDirective

/**
 * Deterministic templates. Not an LLM. Never invents numbers absent from facts.
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
    ): String? {
        val text = when (speak) {
            is SpeakDecision.Silence -> null
            is SpeakDecision.Speak -> when (speak.actId) {
                SpeechActId.SAFETY -> safety.text ?: "Stop the exercise and seek appropriate medical attention."
                SpeechActId.USEFUL_DELTA -> "Nice. That's one more than last time."
                SpeechActId.FIRST_SET_OF_EXERCISE -> "Nice."
                SpeechActId.CORRECTION_ACK -> "Got it."
                SpeechActId.ANSWER_QUESTION -> answerHistory(contexts)
                SpeechActId.CLARIFICATION -> "Was that the weight or the reps?"
                SpeechActId.WEIGHT_CHANGE_ACK -> "Got it, we'll use that weight."
                SpeechActId.SKIP_ACK -> "Okay, skipping this one."
                SpeechActId.EXTRA_SET_ACK -> "Okay, one more."
                SpeechActId.SUBSTITUTION_PROPOSE -> "We can switch if you want."
                SpeechActId.SUBSTITUTION_APPLIED -> "Okay, swapped."
                SpeechActId.SESSION_GREETING -> "Let's get started."
                SpeechActId.SESSION_RESUME -> "Welcome back."
                SpeechActId.SESSION_COMPLETE -> "That's the session."
                SpeechActId.PAUSE_ACK -> "Paused."
                SpeechActId.SUGGEST_WEIGHT_THIS_SESSION ->
                    recommendation?.weightKg?.let { "Try $it next set if you want." }
                SpeechActId.OFFLINE_VOICE_UNAVAILABLE ->
                    "I can't hear you until you're back online — tap the numbers if you want."
            }
        }
        if (text != null && banned.any { it in text }) error("banned IVR string")
        return text
    }

    private fun answerHistory(contexts: FourContexts): String {
        val sets = contexts.historical.previousSessionsForExercise.firstOrNull()?.sets.orEmpty()
        val known = sets.filter { it.weightKg != null && it.reps != null }
        val none = contexts.historical.aggregates.any { it.stance == EpistemicStance.NONE }
        if (known.isEmpty() || none) {
            return "I don't have a previous number for this yet."
        }
        val last = known.last()
        return "Last time you did ${last.weightKg} kilos for ${last.reps}."
    }
}
