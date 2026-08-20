package com.gymbuddy.domain.safety

import com.gymbuddy.domain.SafetyAction
import com.gymbuddy.domain.SafetySeverity

/**
 * [fired] false is “none” — [SafetySeverity] has no NONE member.
 * LOW/MEDIUM/HIGH/CRITICAL use the frozen enum.
 */
data class SafetyResult(
    val fired: Boolean,
    val category: String? = null,
    val severity: SafetySeverity? = null,
    val action: SafetyAction? = null,
    val persistNote: Boolean = false,
    val pause: Boolean = false,
    val allowSetCompleted: Boolean = true,
    val templateId: String? = null,
)

/**
 * Dedicated safety layer. Runs on raw transcript BEFORE extraction.
 * Cannot be disabled in production Gym Mode. The LLM cannot override
 * severity ≥ high.
 */
interface SafetyClassifier {
    fun classify(rawTranscript: String): SafetyResult
}

data class SafetyTableRow(
    val patternId: String,
    val examples: List<String>,
    val category: String?,
    val severity: SafetySeverity?,
    val action: SafetyAction?,
    val persistNote: Boolean,
    val pause: Boolean,
    val allowSetCompletedIfNumbers: Boolean,
)

object SafetyDecisionTable {
    val rows: List<SafetyTableRow> = listOf(
        SafetyTableRow(
            patternId = "chest_pain_critical",
            examples = listOf("my chest hurts", "chest pain"),
            category = "chest_pain",
            severity = SafetySeverity.CRITICAL,
            action = SafetyAction.STOP_RECOMMEND,
            persistNote = true,
            pause = true,
            allowSetCompletedIfNumbers = true,
        ),
        SafetyTableRow(
            patternId = "faint_critical",
            examples = listOf("I feel faint", "dizzy, going to pass out"),
            category = "faintness",
            severity = SafetySeverity.CRITICAL,
            action = SafetyAction.STOP_RECOMMEND,
            persistNote = true,
            pause = true,
            allowSetCompletedIfNumbers = true,
        ),
        SafetyTableRow(
            patternId = "sob_critical",
            examples = listOf("I can't breathe"),
            category = "severe_sob",
            severity = SafetySeverity.CRITICAL,
            action = SafetyAction.STOP_RECOMMEND,
            persistNote = true,
            pause = true,
            allowSetCompletedIfNumbers = true,
        ),
        SafetyTableRow(
            patternId = "sharp_pain_high",
            examples = listOf("sharp pain in my shoulder", "I think I tore something"),
            category = "sharp_pain",
            severity = SafetySeverity.HIGH,
            action = SafetyAction.PAUSE,
            persistNote = true,
            pause = true,
            allowSetCompletedIfNumbers = true,
        ),
        SafetyTableRow(
            patternId = "shoulder_weird_medium",
            examples = listOf(
                "My shoulder feels a little weird on this.",
                "I'm feeling this more in my shoulder.",
                "Naku chest kanna shoulder lo ekkuva feel avutundi.",
            ),
            category = "discomfort_mild",
            severity = SafetySeverity.MEDIUM,
            action = SafetyAction.ADVISE,
            persistNote = true,
            pause = false,
            allowSetCompletedIfNumbers = true,
        ),
        SafetyTableRow(
            patternId = "chest_fly_not_safety",
            examples = listOf("That chest fly machine is occupied."),
            category = null,
            severity = null,
            action = null,
            persistNote = false,
            pause = false,
            allowSetCompletedIfNumbers = false,
        ),
        SafetyTableRow(
            patternId = "technique_question_not_safety",
            examples = listOf("Why do I feel this in my shoulder?"),
            category = null,
            severity = null,
            action = null,
            persistNote = false,
            pause = false,
            allowSetCompletedIfNumbers = false,
        ),
    )
}
