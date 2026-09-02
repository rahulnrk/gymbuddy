package com.gymbuddy.domain.cardio

import com.gymbuddy.domain.picker.PickContext
import com.gymbuddy.domain.programming.TrainingAge

private val KNEE_CONDITIONS = setOf("anterior-knee-pain", "recent-knee-surgery")
private val HIGH_IMPACT_BLOCKED = KNEE_CONDITIONS + setOf("pregnancy")
private val MODERATE_IMPACT_BLOCKED = setOf("recent-knee-surgery", "pregnancy")

fun compatible(m: CardioModality, p: CardioProtocol, ctx: PickContext): Boolean =
    incompatibility(m, p, ctx) == null

fun incompatibility(
    m: CardioModality,
    p: CardioProtocol,
    ctx: PickContext,
): CardioExclusion? {
    if (p.type !in m.suitableProtocolTypes) {
        return CardioExclusion.ProtocolNotSuitable(m, p)
    }
    val missing = unsatisfiedSlots(m.requiredEquipment, ctx.equipment)
    if (missing != null) {
        return CardioExclusion.MissingEquipment(m, p, missing)
    }
    val impactHit = impactBlockedBy(m.impact, ctx.user.conditions)
    if (impactHit != null) {
        return CardioExclusion.ImpactBlocked(m, p, impactHit)
    }
    val hit = m.contraindications.firstOrNull { it in ctx.user.conditions }
    if (hit != null) {
        return CardioExclusion.Contraindicated(m, p, hit)
    }
    if (p.requiresAerobicBase && lacksAerobicBase(ctx)) {
        return CardioExclusion.NeedsAerobicBase(m, p)
    }
    return null
}

fun filterPairs(library: CardioLibrary, ctx: PickContext): CardioFilter {
    val kept = mutableListOf<Pair<CardioModality, CardioProtocol>>()
    val excluded = mutableListOf<CardioExclusion>()
    library.modalities.forEach { m ->
        library.protocols.forEach { p ->
            val why = incompatibility(m, p, ctx)
            if (why == null) kept += m to p else excluded += why
        }
    }
    return CardioFilter(kept = kept, exclusions = excluded)
}

internal fun lacksAerobicBase(ctx: PickContext): Boolean =
    ctx.user.trainingAge == TrainingAge.BEGINNER || !ctx.hasCardioHistory

internal fun unsatisfiedSlots(
    required: List<List<String>>,
    have: Set<String>,
): List<List<String>>? {
    val missing = required.filter { slot ->
        if (slot.contains("none")) false
        else slot.none { it in have }
    }
    return missing.takeIf { it.isNotEmpty() }
}

/**
 * Impact is a safety field. High-impact work is never offered when a listed
 * condition is present. Not a ranking penalty — construction-time exclusion.
 */
internal fun impactBlockedBy(impact: CardioImpact, conditions: List<String>): String? {
    val hitHigh = conditions.firstOrNull { it in HIGH_IMPACT_BLOCKED }
    if (impact == CardioImpact.HIGH && hitHigh != null) return hitHigh
    val hitMod = conditions.firstOrNull { it in MODERATE_IMPACT_BLOCKED }
    if (impact == CardioImpact.MODERATE && hitMod != null) return hitMod
    return null
}
