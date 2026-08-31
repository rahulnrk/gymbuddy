package com.gymbuddy.domain.loop

import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
import kotlin.math.abs

/**
 * Single source of truth for "is this set worth mentioning, and how".
 *
 * [ShouldSpeak] and [CoachingPhraser] both call this, so the decision to speak
 * and the sentence spoken are computed from the same numbers. Previously the
 * gate compared one thing and the sentence asserted another.
 *
 * Baseline priority (TECHNICAL_SPEC useful-delta):
 *   1. the immediately previous completed set in this session
 *   2. otherwise the corresponding set in the most recent stored session,
 *      and only when that fact is speech-allowed with stance=known
 */
data class DeltaAnalysis(
    val reps: Claim.RepDelta?,
    val weight: Claim.WeightDelta?,
) {
    val hasUsefulDelta: Boolean
        get() = (reps != null && reps.delta != 0) ||
            (weight != null && abs(weight.deltaKg) >= DeltaPhrase.MIN_KG)

    val claims: List<Claim> get() = listOfNotNull(reps, weight)
}

object Deltas {

    fun of(snapshot: WorkoutSessionSnapshot, contexts: FourContexts?): DeltaAnalysis {
        val live = snapshot.liveSetsOnCurrent().sortedBy { it.setNumber }
        val current = live.lastOrNull() ?: return DeltaAnalysis(null, null)

        if (live.size >= 2) {
            val prev = live[live.lastIndex - 1]
            val curFact = contexts?.fact("fact:workout:set:${current.setId.value}")
            val prevFact = contexts?.fact("fact:workout:set:${prev.setId.value}")
            val ev = listOfNotNull(curFact, prevFact)
            if (ev.size == 2) {
                return DeltaAnalysis(
                    reps = pairOrNull(current.reps, prev.reps)?.let { (c, p) ->
                        Claim.RepDelta(c, p, Baseline.PREVIOUS_SET_THIS_SESSION, ev).takeIf { it.delta != 0 }
                    },
                    weight = pairOrNull(current.weightKg, prev.weightKg)?.let { (c, p) ->
                        Claim.WeightDelta(c, p, Baseline.PREVIOUS_SET_THIS_SESSION, ev)
                            .takeIf { abs(it.deltaKg) >= DeltaPhrase.MIN_KG }
                    },
                )
            }
            return DeltaAnalysis(null, null)
        }

        val ctx = contexts ?: return DeltaAnalysis(null, null)
        val prior = Recommend.mostRecentPreviousSession(ctx) ?: return DeltaAnalysis(null, null)
        val priorSet = Recommend.priorCorrespondingSet(prior, current.setNumber) ?: return DeltaAnalysis(null, null)
        val curFact = ctx.fact("fact:workout:set:${current.setId.value}") ?: return DeltaAnalysis(null, null)
        val priorFact = ctx.fact("fact:hist:set:${priorSet.setId}") ?: return DeltaAnalysis(null, null)
        if (!priorFact.speechAllowed) return DeltaAnalysis(null, null)
        val ev = listOf(curFact, priorFact)

        return DeltaAnalysis(
            reps = pairOrNull(current.reps, priorSet.reps)?.let { (c, p) ->
                Claim.RepDelta(c, p, Baseline.PREVIOUS_SESSION, ev).takeIf { it.delta != 0 }
            },
            weight = pairOrNull(current.weightKg, priorSet.weightKg)?.let { (c, p) ->
                Claim.WeightDelta(c, p, Baseline.PREVIOUS_SESSION, ev)
                    .takeIf { abs(it.deltaKg) >= DeltaPhrase.MIN_KG }
            },
        )
    }

    private fun <T : Any> pairOrNull(a: T?, b: T?): Pair<T, T>? =
        if (a != null && b != null) a to b else null

    private fun FourContexts.fact(id: String) = facts.firstOrNull { it.id == id }
}
