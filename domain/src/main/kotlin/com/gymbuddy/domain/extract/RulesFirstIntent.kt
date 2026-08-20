package com.gymbuddy.domain.extract

/**
 * Deterministic multi-label intent. Rules win over the fake extractor.
 * Natural phrasing, not "COMMAND: SKIP".
 */
object RulesFirstIntent {
    private val CORRECTION = listOf(
        "no,", "nope", "actually", "wait, that was", "wait that was",
        "scratch that", "delete that", "counted wrong", "kaadu",
        "that was actually", "make that",
    )
    private val QUESTION = listOf(
        "?", "what's", "whats", "what did", "what do", "why ", "how much",
        "should i", "should we", "entha", "emiti", "kya ", "enna ",
        "what's next", "whats next", "last week", "last time", "last thursday",
    )
    private val COMMAND = listOf(
        "drop the weight", "skip this", "skip it", "let's skip", "lets skip",
        "one more", "another set", "extra set", "pause", "i'm done", "im done",
        "swap", "instead", "occupied",
    )
    private val FEEDBACK = listOf("heavy", "easy", "tired", "rough", "weird")
    private val HISTORICAL = listOf("last week", "last time", "last thursday", "i think i used")

    fun detect(transcript: String): Set<IntentLabel> {
        val n = transcript.lowercase()
        val out = mutableSetOf<IntentLabel>()
        if (CORRECTION.any { it in n } && !HISTORICAL.any { it in n }) out += IntentLabel.CORRECTION
        if (QUESTION.any { it in n } || HISTORICAL.any { it in n }) out += IntentLabel.QUESTION
        if (COMMAND.any { it in n }) out += IntentLabel.COMMAND
        if (FEEDBACK.any { it in n }) out += IntentLabel.FEEDBACK
        if (isLogging(n) && IntentLabel.QUESTION !in out && IntentLabel.CORRECTION !in out) {
            out += IntentLabel.LOGGING
        }
        if (out.isEmpty()) out += IntentLabel.CASUAL
        return out
    }

    private fun isLogging(n: String): Boolean {
        if (HISTORICAL.any { it in n }) return false
        val got = Regex("""\b(got|did|that's|thats|that is)\s+(\d+|ten|eight|twelve|eleven|nine|six|seven)\b""")
        val reps = Regex("""\b(\d+|ten|eight|twelve)\s+reps\b""")
        val forReps = Regex("""\b\d+(?:\.\d+)?\s*(kg|kilos|kilo|lb|lbs)?\s*(for|x|×)\s*""")
        return got.containsMatchIn(n) || reps.containsMatchIn(n) || forReps.containsMatchIn(n)
    }
}
