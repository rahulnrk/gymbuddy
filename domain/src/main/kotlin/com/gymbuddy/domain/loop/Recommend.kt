package com.gymbuddy.domain.loop

import com.gymbuddy.domain.EpistemicStance
import com.gymbuddy.domain.context.CompletedSetSnapshot
import com.gymbuddy.domain.context.EpistemicFact
import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.context.PreviousSessionSnapshot
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class RecKind { OBSERVATION, INTERPRETATION, RECOMMENDATION }

enum class RecDirection { INCREASE, MAINTAIN, REDUCE }

/**
 * Talk-only. Never writes next-workout prescribed weight (KD24).
 * Null means no meaningful recommendation - do not speak to fill air.
 *
 * [basis] states which evidence tier produced this. A recommendation derived
 * only from the current session must never be phrased as a comparison with a
 * previous session (PRODUCT.md 33).
 */
data class Recommendation(
    val kind: RecKind,
    val direction: RecDirection?,
    val weightKg: Double?,
    val evidence: List<EpistemicFact>,
    val stance: EpistemicStance,
    val basis: RecBasis,
    val claims: List<Claim> = emptyList(),
)

enum class RecBasis {
    /** Only sets logged in the active session were available. */
    CURRENT_SESSION_ONLY,

    /** A stored previous session for this exercise was compared. */
    CROSS_SESSION,

    /** Derived from what the user said, not from logged rows. */
    USER_FEEDBACK,
}

/**
 * Progression suggestions.
 *
 * TECHNICAL_SPEC 24 describes cross-session progression ("last three sessions
 * were 12, 12 and 11 at 50 kg"). The engine therefore reads
 * [FourContexts.historical] as well as the live snapshot, and reports which of
 * the two it actually used. When no previous session is stored the result is
 * either null or explicitly [RecBasis.CURRENT_SESSION_ONLY] - never a fabricated
 * comparison.
 */
object Recommend {

    /** Smallest plate step assumed for a suggestion. UNKNOWN provenance - see DECISIONS_AND_THRESHOLDS.md. */
    const val STEP_KG = 2.5

    fun recommend(
        snapshot: WorkoutSessionSnapshot,
        contexts: FourContexts? = null,
        feedbackHard: Boolean = false,
    ): Recommendation? = suggest(snapshot, contexts, feedbackHard)

    fun suggest(
        snapshot: WorkoutSessionSnapshot,
        contexts: FourContexts? = null,
        feedbackHard: Boolean = false,
    ): Recommendation? {
        val live = snapshot.liveSetsOnCurrent()
        val working = snapshot.workingWeightKg

        if (feedbackHard && working != null && live.isNotEmpty()) {
            val last = live.last()
            return Recommendation(
                kind = RecKind.INTERPRETATION,
                direction = RecDirection.REDUCE,
                weightKg = (working - STEP_KG).coerceAtLeast(0.5),
                evidence = listOf(
                    EpistemicFact(
                        id = "fact:feedback:hard",
                        kind = "aggregate",
                        stance = EpistemicStance.INFERRED,
                        value = JsonPrimitive("hard"),
                        sourceRowIds = listOf("set:${last.setId.value}"),
                        speechAllowed = true,
                    ),
                ),
                stance = EpistemicStance.INFERRED,
                basis = RecBasis.USER_FEEDBACK,
            )
        }

        crossSession(snapshot, contexts)?.let { return it }
        return currentSessionOnly(snapshot)
    }

    /**
     * Compare this session's work on the current exercise against the most
     * recent stored session for the same exercise.
     */
    private fun crossSession(
        snapshot: WorkoutSessionSnapshot,
        contexts: FourContexts?,
    ): Recommendation? {
        val ctx = contexts ?: return null
        val prior = mostRecentPreviousSession(ctx) ?: return null
        val live = snapshot.liveSetsOnCurrent().sortedBy { it.setNumber }
        if (live.isEmpty()) return null

        val currentKg = live.mapNotNull { it.weightKg }.distinct().singleOrNull() ?: return null
        val priorKg = prior.sets.mapNotNull { it.weightKg }.distinct().singleOrNull() ?: return null
        val currentReps = live.mapNotNull { it.reps }
        val priorReps = prior.sets.mapNotNull { it.reps }
        if (currentReps.isEmpty() || priorReps.isEmpty()) return null
        if (currentReps.size < priorReps.size) return null

        val currentFacts = live.mapNotNull { s -> ctx.facts.firstOrNull { it.id == "fact:workout:set:${s.setId.value}" } }
        val priorFacts = prior.sets.mapNotNull { s -> ctx.facts.firstOrNull { it.id == "fact:hist:set:${s.setId}" } }
        if (currentFacts.isEmpty() || priorFacts.isEmpty()) return null
        if (priorFacts.any { !it.speechAllowed }) return null

        val comparable = minOf(currentReps.size, priorReps.size)
        val currentTotal = currentReps.take(comparable).sum()
        val priorTotal = priorReps.take(comparable).sum()

        val direction = when {
            currentKg > priorKg -> RecDirection.MAINTAIN
            currentKg == priorKg && currentTotal > priorTotal -> RecDirection.INCREASE
            currentKg == priorKg && currentTotal == priorTotal -> RecDirection.MAINTAIN
            currentKg == priorKg -> RecDirection.MAINTAIN
            else -> RecDirection.MAINTAIN
        }

        val repClaim = if (currentTotal != priorTotal) {
            Claim.RepDelta(
                currentReps = currentReps.last(),
                baselineReps = priorReps.take(comparable).last(),
                baseline = Baseline.PREVIOUS_SESSION,
                evidence = listOf(currentFacts.last(), priorFacts.take(comparable).last()),
            ).takeIf { it.delta != 0 }
        } else null

        val weightClaim = if (kotlin.math.abs(currentKg - priorKg) >= DeltaPhrase.MIN_KG) {
            Claim.WeightDelta(
                currentKg = currentKg,
                baselineKg = priorKg,
                baseline = Baseline.PREVIOUS_SESSION,
                evidence = listOf(currentFacts.last(), priorFacts.last()),
            )
        } else null

        if (repClaim == null && weightClaim == null) return null

        return Recommendation(
            kind = RecKind.RECOMMENDATION,
            direction = direction,
            weightKg = if (direction == RecDirection.INCREASE) currentKg + STEP_KG else currentKg,
            evidence = (currentFacts + priorFacts).distinctBy { it.id },
            stance = EpistemicStance.KNOWN,
            basis = RecBasis.CROSS_SESSION,
            claims = listOfNotNull(repClaim, weightClaim),
        )
    }

    /**
     * Fallback when nothing is stored for this exercise yet.
     * Deliberately narrow, and never phrased as a comparison with "last time".
     */
    private fun currentSessionOnly(snapshot: WorkoutSessionSnapshot): Recommendation? {
        val live = snapshot.liveSetsOnCurrent().sortedBy { it.setNumber }
        if (live.size < 3) return null
        val last3 = live.takeLast(3)
        val weights = last3.map { it.weightKg }
        if (weights.any { it == null } || weights.distinct().size != 1) return null
        val reps = last3.mapNotNull { it.reps }
        if (reps.size != 3 || reps.distinct().size == 1) return null
        val w = weights.first()!!
        if (!(reps[0] >= 10 && reps[1] >= 10 && reps[2] >= 10 && reps[2] < reps[0])) return null
        return Recommendation(
            kind = RecKind.RECOMMENDATION,
            direction = RecDirection.INCREASE,
            weightKg = w + STEP_KG,
            evidence = listOf(
                EpistemicFact(
                    id = "fact:rec:last-three",
                    kind = "aggregate",
                    stance = EpistemicStance.KNOWN,
                    value = buildJsonObject {
                        put("reps", reps.joinToString("/"))
                        put("weightKg", w)
                    },
                    sourceRowIds = last3.map { "set:${it.setId.value}" },
                    speechAllowed = true,
                ),
            ),
            stance = EpistemicStance.KNOWN,
            basis = RecBasis.CURRENT_SESSION_ONLY,
        )
    }

    /** Most recent stored session for the current exercise. Ordering is explicit, not assumed. */
    fun mostRecentPreviousSession(ctx: FourContexts): PreviousSessionSnapshot? =
        ctx.historical.previousSessionsForExercise
            .filter { it.stance == EpistemicStance.KNOWN && it.sets.isNotEmpty() }
            .maxByOrNull { it.occurredOn }

    /** Corresponding set in the previous session, by set number, else its last set. */
    fun priorCorrespondingSet(prior: PreviousSessionSnapshot, setNumber: Int): CompletedSetSnapshot? =
        prior.sets.firstOrNull { it.setNumber == setNumber } ?: prior.sets.maxByOrNull { it.setNumber }
}
