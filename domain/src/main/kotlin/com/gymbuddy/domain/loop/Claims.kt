package com.gymbuddy.domain.loop

import com.gymbuddy.domain.EpistemicStance
import com.gymbuddy.domain.context.EpistemicFact
import com.gymbuddy.domain.context.FourContexts
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Claim-level evidence for coaching speech.
 *
 * A coaching line is a set of [Claim]s. Each claim states the numbers it
 * asserts **and** the facts that support them, so [ClaimVerifier] can
 * recompute the assertion instead of trusting the sentence.
 *
 * This is deliberately small: no retrieval, no LLM reasoning trace. It exists
 * so "one more rep than last time" cannot be spoken when the delta is zero.
 *
 * Product law: never invent workout history (PRODUCT.md §33). A claim whose
 * evidence is absent, not speech-allowed, or numerically inconsistent is a
 * defect, not a phrasing preference.
 */
sealed class Claim {
    abstract val evidence: List<EpistemicFact>

    /** Reps compared against a named baseline. */
    data class RepDelta(
        val currentReps: Int,
        val baselineReps: Int,
        val baseline: Baseline,
        override val evidence: List<EpistemicFact>,
    ) : Claim() {
        val delta: Int get() = currentReps - baselineReps
    }

    /** Working weight compared against a named baseline. */
    data class WeightDelta(
        val currentKg: Double,
        val baselineKg: Double,
        val baseline: Baseline,
        override val evidence: List<EpistemicFact>,
    ) : Claim() {
        val deltaKg: Double get() = currentKg - baselineKg
    }

    /** A specific stored historical set being quoted back to the user. */
    data class HistoricalSet(
        val occurredOn: String,
        val weightKg: Double?,
        val reps: Int?,
        override val evidence: List<EpistemicFact>,
    ) : Claim()

    /**
     * An explicit statement that no history exists. Carries no evidence by
     * design — it asserts absence, so there is nothing to cite.
     */
    data object NoHistory : Claim() {
        override val evidence: List<EpistemicFact> get() = emptyList()
    }
}

/** What a delta is measured against. Changes the wording, so it must be explicit. */
enum class Baseline {
    /** The immediately previous completed set in this session. */
    PREVIOUS_SET_THIS_SESSION,

    /** The corresponding set in the most recent previous session. */
    PREVIOUS_SESSION,
}

/**
 * A coaching line plus the claims that justify it.
 * [citedFactIds] is derived from the claims — never from "all allowed facts".
 */
data class CoachingStatement(
    val text: String?,
    val claims: List<Claim> = emptyList(),
) {
    val citedFactIds: List<String>
        get() = claims.flatMap { c -> c.evidence.map { it.id } }.distinct()

    companion object {
        val silent = CoachingStatement(null, emptyList())
        fun plain(text: String?) = CoachingStatement(text, emptyList())
    }
}

/**
 * Recomputes each claim from its own evidence.
 *
 * Returns the list of violations; empty means the statement is supported.
 * A violation is a hard failure: the caller must not speak the line.
 */
object ClaimVerifier {

    fun verify(statement: CoachingStatement, contexts: FourContexts): List<String> {
        val out = mutableListOf<String>()
        val byId = contexts.facts.associateBy { it.id }

        statement.claims.forEach { claim ->
            if (claim !is Claim.NoHistory && claim.evidence.isEmpty()) {
                out += "${claim::class.simpleName}: no supporting evidence"
            }
            claim.evidence.forEach { f ->
                val known = byId[f.id]
                if (known == null) {
                    out += "${claim::class.simpleName}: cites unknown fact ${f.id}"
                    return@forEach
                }
                if (!known.speechAllowed) {
                    out += "${claim::class.simpleName}: cites non-speakable fact ${f.id}"
                }
                if (known.stance == EpistemicStance.NONE) {
                    out += "${claim::class.simpleName}: cites stance=none fact ${f.id}"
                }
            }
            out += recompute(claim)
        }
        return out
    }

    /** Numeric self-consistency: the asserted numbers must match the cited rows. */
    private fun recompute(claim: Claim): List<String> = when (claim) {
        is Claim.RepDelta -> buildList {
            if (claim.delta == 0) add("RepDelta: asserts a delta of 0")
            val reps = claim.evidence.mapNotNull { repsOf(it) }
            if (reps.isNotEmpty()) {
                if (claim.currentReps !in reps && claim.baselineReps !in reps) {
                    add("RepDelta: neither ${claim.currentReps} nor ${claim.baselineReps} appears in cited rows $reps")
                }
            }
        }
        is Claim.WeightDelta -> buildList {
            if (kotlin.math.abs(claim.deltaKg) < 1e-9) add("WeightDelta: asserts a delta of 0")
            val kgs = claim.evidence.mapNotNull { weightOf(it) }
            if (kgs.isNotEmpty() && kgs.none { near(it, claim.baselineKg) } && kgs.none { near(it, claim.currentKg) }) {
                add("WeightDelta: neither ${claim.currentKg} nor ${claim.baselineKg} appears in cited rows $kgs")
            }
        }
        is Claim.HistoricalSet -> buildList {
            val kgs = claim.evidence.mapNotNull { weightOf(it) }
            val reps = claim.evidence.mapNotNull { repsOf(it) }
            claim.weightKg?.let { w -> if (kgs.isNotEmpty() && kgs.none { near(it, w) }) add("HistoricalSet: weight $w not in cited rows $kgs") }
            claim.reps?.let { r -> if (reps.isNotEmpty() && r !in reps) add("HistoricalSet: reps $r not in cited rows $reps") }
        }
        Claim.NoHistory -> emptyList()
    }

    private fun near(a: Double, b: Double) = kotlin.math.abs(a - b) < 1e-6

    private fun repsOf(f: EpistemicFact): Int? =
        (f.value as? JsonObject)?.get("reps")?.jsonPrimitive?.intOrNull

    private fun weightOf(f: EpistemicFact): Double? =
        (f.value as? JsonObject)?.get("weightKg")?.jsonPrimitive?.doubleOrNull
}
