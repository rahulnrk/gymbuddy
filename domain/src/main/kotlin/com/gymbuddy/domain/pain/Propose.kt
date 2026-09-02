package com.gymbuddy.domain.pain

import com.gymbuddy.domain.adapt.Adjustment
import com.gymbuddy.domain.adapt.DrainOn
import com.gymbuddy.domain.catalog.Catalog
import com.gymbuddy.domain.picker.PickContext
import com.gymbuddy.domain.picker.Slot
import com.gymbuddy.domain.picker.SlotRole
import com.gymbuddy.domain.picker.rank
import com.gymbuddy.domain.plan.PatternSiblings
import com.gymbuddy.domain.plan.PlannedSession

/**
 * Outcome to a proposal. Does not mutate [session].
 */
fun propose(
    interview: PainInterview,
    session: PlannedSession,
    ctx: PickContext,
    catalog: Catalog,
): PainProposal {
    val before = session.entries.toList()
    val outcome = triage(interview)
    val recorded = recordIntensity(interview.intensity)
    val result = when (outcome) {
        is PainOutcome.NeedMore -> PainProposal(
            outcome = outcome,
            spoken = PainCopy.question(outcome.nextQuestion),
            intensityRecorded = recorded,
        )
        is PainOutcome.LikelyTrainingSoreness -> {
            val slot = interview.slot ?: 0
            PainProposal(
                outcome = outcome,
                spoken = outcome.why,
                adjustment = Adjustment.LowerEffort(slot, 1, outcome.why),
                intensityRecorded = recorded,
                record = SessionPainRecord(interview.where, recorded, interview.currentSlug, "soreness"),
            )
        }
        is PainOutcome.StopThisMovement -> {
            val sibling = siblingFor(interview, ctx, catalog)
            val spoken = PainCopy.stopMovement(
                interview.currentName ?: "this one",
                sibling?.exercise?.name,
            )
            val slot = interview.slot ?: 0
            PainProposal(
                outcome = outcome.copy(why = spoken),
                spoken = spoken,
                adjustment = Adjustment.DropSlot(slot, spoken),
                sibling = sibling,
                excludeSlugs = setOfNotNull(interview.currentSlug),
                intensityRecorded = recorded,
                record = SessionPainRecord(interview.where, recorded, interview.currentSlug, "stop"),
            )
        }
        is PainOutcome.ReferBeforeContinuing -> {
            val reduced = if (outcome.mayTrainOtherAreas) {
                dropPattern(session, interview)
            } else {
                session.copy(entries = emptyList())
            }
            PainProposal(
                outcome = outcome,
                spoken = outcome.why,
                adjustment = Adjustment.DropSlot(interview.slot ?: 0, outcome.why),
                excludeSlugs = setOfNotNull(interview.currentSlug),
                reducedSession = reduced,
                intensityRecorded = recorded,
                record = SessionPainRecord(interview.where, recorded, interview.currentSlug, "refer"),
            )
        }
        is PainOutcome.UrgentReferral -> PainProposal(
            outcome = outcome,
            spoken = PainCopy.urgent(outcome.flags),
            reducedSession = session.copy(entries = emptyList()),
            intensityRecorded = recorded,
            record = SessionPainRecord(interview.where, recorded, interview.currentSlug, "urgent"),
        )
    }
    check(session.entries == before) { "triage mutated the session" }
    return result.copy(drainOn = DrainOn.RESTING)
}

fun followUp(record: SessionPainRecord): String = PainCopy.followUp(record.where)

fun withExclusions(ctx: PickContext, slugs: Set<String>): PickContext =
    ctx.copy(user = ctx.user.copy(sharpPainSlugs = ctx.user.sharpPainSlugs + slugs))

private fun siblingFor(
    interview: PainInterview,
    ctx: PickContext,
    catalog: Catalog,
): com.gymbuddy.domain.picker.ScoredExercise? {
    val pattern = interview.currentPattern ?: return null
    val role = SlotRole.PRIMARY
    for (sib in PatternSiblings.of(pattern)) {
        val top = rank(Slot(sib, role), catalog, ctx).firstOrNull() ?: continue
        if (top.exercise.slug == interview.currentSlug) continue
        return top
    }
    return null
}

private fun dropPattern(session: PlannedSession, interview: PainInterview): PlannedSession {
    val slug = interview.currentSlug
    val pattern = interview.currentPattern
    val kept = session.entries.filter { entry ->
        entry.exercise.slug != slug &&
            entry.slot.pattern != pattern &&
            entry.exercise.pattern != pattern
    }
    return session.copy(entries = kept)
}
