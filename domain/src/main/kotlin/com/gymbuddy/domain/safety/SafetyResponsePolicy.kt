package com.gymbuddy.domain.safety

import com.gymbuddy.domain.SafetySeverity

/**
 * Response rendering is separate from classification.
 * Templates contain no diagnosis and cannot be overridden for HIGH/CRITICAL.
 */
data class SafetySpeechDirective(
    val templateId: String?,
    val text: String?,
    val suppressNormalCoaching: Boolean,
)

object SafetyResponsePolicy {
    const val CRITICAL_TEMPLATE_ID = "safety.critical.stop_seek_help"
    const val HIGH_TEMPLATE_ID = "safety.high.pause_no_push"
    const val MEDIUM_TEMPLATE_ID = "safety.medium.advise_reduce"

    /** Frozen critical wording. No diagnosis. LLM cannot override. */
    const val CRITICAL_TEXT =
        "Stop the exercise and seek appropriate medical attention."

    const val HIGH_TEXT =
        "Let's pause this exercise. Don't push through."

    const val MEDIUM_TEXT =
        "I hear you. We can reduce the load, switch this movement, or stop this exercise."

    fun directive(result: SafetyResult): SafetySpeechDirective {
        if (!result.fired || result.severity == null) {
            return SafetySpeechDirective(null, null, suppressNormalCoaching = false)
        }
        return when (result.severity) {
            SafetySeverity.CRITICAL -> SafetySpeechDirective(
                CRITICAL_TEMPLATE_ID, CRITICAL_TEXT, suppressNormalCoaching = true,
            )
            SafetySeverity.HIGH -> SafetySpeechDirective(
                HIGH_TEMPLATE_ID, HIGH_TEXT, suppressNormalCoaching = true,
            )
            SafetySeverity.MEDIUM, SafetySeverity.LOW -> SafetySpeechDirective(
                MEDIUM_TEMPLATE_ID, MEDIUM_TEXT, suppressNormalCoaching = false,
            )
        }
    }

    /**
     * LLM phrasing is ignored when coaching is suppressed.
     * Never returns "continue the workout" on HIGH/CRITICAL.
     */
    fun resolve(result: SafetyResult, llmSuggestion: String?): SafetySpeechDirective {
        val base = directive(result)
        if (base.suppressNormalCoaching) return base
        if (!llmSuggestion.isNullOrBlank() && !result.fired) {
            return SafetySpeechDirective(null, llmSuggestion, suppressNormalCoaching = false)
        }
        return base
    }
}
