package com.gymbuddy.domain.safety

/**
 * Mandatory order: classify raw transcript, then extract.
 * The classifier never calls the extractor; this gate does, after safety.
 *
 * Does not mutate workout state, append events, or write Room.
 */
data class SafetyTurn<T>(
    val decision: SafetyResult,
    val speech: SafetySpeechDirective,
    val extracted: T,
    val classifiedBeforeExtract: Boolean = true,
)

class SafetyFirstTurn(
    private val classifier: SafetyClassifier = DefaultSafetyClassifier(),
) {
    fun <T> process(
        transcript: String,
        extract: () -> T,
        llmCoachingSuggestion: String? = null,
    ): SafetyTurn<T> {
        val decision = classifier.classify(transcript)
        val extracted = extract()
        val speech = SafetyResponsePolicy.resolve(decision, llmCoachingSuggestion)
        return SafetyTurn(decision, speech, extracted)
    }
}
