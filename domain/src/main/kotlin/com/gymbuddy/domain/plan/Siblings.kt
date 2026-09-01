package com.gymbuddy.domain.plan

/**
 * One-hop pattern fallback when a slot is empty. The lists are the contract,
 * not a guess from [com.gymbuddy.domain.programming.patternGroup]. Hinge never
 * swaps with squat.
 */
object PatternSiblings {
    val MAP: Map<String, List<String>> = mapOf(
        "vertical pull" to listOf("horizontal pull"),
        "horizontal pull" to listOf("vertical pull"),
        "vertical push" to listOf("incline push", "horizontal push"),
        "horizontal push" to listOf("incline push", "vertical push"),
        "incline push" to listOf("horizontal push"),
        "decline push" to listOf("horizontal push"),
        "squat" to listOf("lunge"),
        "lunge" to listOf("squat"),
        "lats" to listOf("vertical pull", "horizontal pull"),
    )

    fun of(pattern: String): List<String> = MAP[pattern].orEmpty()
}
