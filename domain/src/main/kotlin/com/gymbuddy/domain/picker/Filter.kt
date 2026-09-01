package com.gymbuddy.domain.picker

import com.gymbuddy.domain.catalog.Catalog
import com.gymbuddy.domain.catalog.Exercise
import com.gymbuddy.domain.intake.PainCharacter
import com.gymbuddy.domain.intake.PainReport

/**
 * Ordered filter for one slot. Safety exclusions are never overridable.
 * Dislike is the only soft exclusion: it cannot empty the slot.
 */
fun candidates(slot: Slot, catalog: Catalog, ctx: PickContext): CandidateSet {
    val exclusions = mutableListOf<ExclusionRecord>()
    val afterSafety = mutableListOf<Exercise>()
    catalog.exercises.forEach { ex ->
        val safety = safetyExclusion(ex, ctx)
        if (safety != null) {
            exclusions += ExclusionRecord(ex, safety)
            return@forEach
        }
        val equipment = unsatisfiedSlots(ex, ctx.equipment)
        if (equipment != null) {
            exclusions += ExclusionRecord(ex, Exclusion.MissingEquipment(equipment))
            return@forEach
        }
        if (ex.pattern != slot.pattern) {
            exclusions += ExclusionRecord(ex, Exclusion.WrongPattern(slot.pattern))
            return@forEach
        }
        afterSafety += ex
    }

    val disliked = afterSafety.filter { it.slug in ctx.dislikes }
    val liked = afterSafety.filter { it.slug !in ctx.dislikes }
    val (survivors, kept) = if (liked.isNotEmpty()) {
        disliked.forEach { exclusions += ExclusionRecord(it, Exclusion.Disliked(it.slug)) }
        liked to emptySet<String>()
    } else {
        afterSafety to disliked.map { it.slug }.toSet()
    }
    return CandidateSet(
        survivors = survivors.sortedBy { it.slug },
        exclusions = exclusions,
        keptDespiteDislike = kept,
    )
}

private fun safetyExclusion(ex: Exercise, ctx: PickContext): Exclusion? {
    val hit = ex.contraindications.firstOrNull { it in ctx.user.conditions }
    if (hit != null) return Exclusion.Contraindicated(hit)
    val fromReports = ctx.painReports.firstOrNull { report ->
        (report.character == PainCharacter.SHARP || report.character == PainCharacter.RADIATING) &&
            ex.slug in report.movements
    }
    if (fromReports != null) return Exclusion.PainfulMovement(fromReports)
    if (ex.slug in ctx.user.sharpPainSlugs) {
        return Exclusion.PainfulMovement(
            PainReport(
                where = "",
                whenItHappens = "",
                movements = setOf(ex.slug),
                character = PainCharacter.SHARP,
            ),
        )
    }
    return null
}

private fun unsatisfiedSlots(ex: Exercise, have: Set<String>): List<List<String>>? {
    val missing = ex.requiredEquipment.filter { slot -> slot.none { it in have } }
    return missing.takeIf { it.isNotEmpty() }
}
