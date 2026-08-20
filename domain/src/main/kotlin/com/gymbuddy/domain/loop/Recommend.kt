package com.gymbuddy.domain.loop

import com.gymbuddy.domain.EpistemicStance
import com.gymbuddy.domain.context.EpistemicFact
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class RecKind { OBSERVATION, INTERPRETATION, RECOMMENDATION }

enum class RecDirection { INCREASE, MAINTAIN, REDUCE }

/**
 * Talk-only. Never writes next-workout prescribed weight.
 * Null means no meaningful recommendation (do not speak just to fill air).
 */
data class Recommendation(
    val kind: RecKind,
    val direction: RecDirection?,
    val weightKg: Double?,
    val evidence: List<EpistemicFact>,
    val stance: EpistemicStance,
)

object Recommend {
    fun recommend(snapshot: WorkoutSessionSnapshot, feedbackHard: Boolean = false): Recommendation? =
        suggest(snapshot, feedbackHard)

    fun suggest(snapshot: WorkoutSessionSnapshot, feedbackHard: Boolean = false): Recommendation? {
        val live = snapshot.liveSetsOnCurrent()
        val working = snapshot.workingWeightKg
        if (feedbackHard && working != null && live.isNotEmpty()) {
            val last = live.last()
            val sources = listOf("set:${last.setId.value}")
            return Recommendation(
                kind = RecKind.INTERPRETATION,
                direction = RecDirection.REDUCE,
                weightKg = (working - 2.5).coerceAtLeast(0.5),
                evidence = listOf(
                    EpistemicFact(
                        id = "fact:feedback:hard",
                        kind = "aggregate",
                        stance = EpistemicStance.INFERRED,
                        value = JsonPrimitive("hard"),
                        sourceRowIds = sources,
                        speechAllowed = true,
                    ),
                ),
                stance = EpistemicStance.INFERRED,
            )
        }
        if (live.size < 3) return null
        val last3 = live.sortedBy { it.setNumber }.takeLast(3)
        val weights = last3.map { it.weightKg }
        if (weights.any { it == null } || weights.distinct().size != 1) return null
        val reps = last3.mapNotNull { it.reps }
        if (reps.size != 3) return null
        if (reps.distinct().size == 1) return null
        val w = weights.first()!!
        if (reps[0] >= 10 && reps[1] >= 10 && reps[2] >= 10 && reps[2] < reps[0]) {
            val ids = last3.map { "set:${it.setId.value}" }
            return Recommendation(
                kind = RecKind.RECOMMENDATION,
                direction = RecDirection.INCREASE,
                weightKg = w + 2.5,
                evidence = listOf(
                    EpistemicFact(
                        id = "fact:rec:last-three",
                        kind = "aggregate",
                        stance = EpistemicStance.KNOWN,
                        value = buildJsonObject {
                            put("reps", reps.joinToString("/"))
                            put("weightKg", w)
                        },
                        sourceRowIds = ids,
                        speechAllowed = true,
                    ),
                ),
                stance = EpistemicStance.KNOWN,
            )
        }
        return null
    }
}
