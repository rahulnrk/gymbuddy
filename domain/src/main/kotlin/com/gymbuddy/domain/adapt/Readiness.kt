package com.gymbuddy.domain.adapt

import com.gymbuddy.domain.catalog.Catalog
import com.gymbuddy.domain.picker.PickContext
import com.gymbuddy.domain.picker.PickerConstants
import com.gymbuddy.domain.picker.SlotRole
import com.gymbuddy.domain.picker.rankSlot
import com.gymbuddy.domain.programming.PlannedSession
import com.gymbuddy.domain.programming.effectiveSlot
import com.gymbuddy.domain.programming.patternGroup

/**
 * Propose adjustments for today's readiness. Never mutates [session].
 * UNKNOWN is not fine; with no signal we still propose AsPlanned and stay quiet.
 */
fun adjustForToday(
    session: PlannedSession,
    readiness: Readiness,
    ctx: PickContext,
    catalog: Catalog,
): List<Adjustment> {
    val before = session.exercises.toList()
    val out = mutableListOf<Adjustment>()
    if (!hasActionableSignal(readiness)) {
        return listOf(Adjustment.AsPlanned)
    }

    val sleepModifies = readiness.sleepHours != null && readiness.sleepHours < 6.0
    val rough = readiness.selfReported == SelfReport.ROUGH
    val fatiguedCtx = ctx.copy(fatigue = readiness.muscleFatigue)

    session.exercises.forEachIndexed { i, planned ->
        val slot = planned.effectiveSlot()
        val ranked = rankSlot(slot, catalog, fatiguedCtx)
        val cooked = ranked.readiness.allCandidatesFatigued ||
            ranked.readiness.worstTargetFatigue >= PickerConstants.FATIGUE_COOKED
        val sore = catalog.musclesFor(planned.exercise).any { (id, w) ->
            w >= 1.0 && (readiness.soreness[id] ?: 0.0) >= PickerConstants.FATIGUE_COOKED
        }
        if (!cooked && !rough && !sore) {
            return@forEachIndexed
        }

        val muscle = spokenMuscle(slot, ranked, catalog)

        out += Adjustment.LowerEffort(
            slot = i,
            extraRir = AdaptConstants.EXTRA_RIR_WHEN_COOKED,
            why = if (sleepModifies) AdaptCopy.LOWER_EFFORT_SHORT_NIGHT else AdaptCopy.LOWER_EFFORT,
        )
        if (planned.hardSets > 1) {
            val to = (planned.hardSets - 1).coerceAtLeast(1)
            out += Adjustment.ReduceSets(
                slot = i,
                from = planned.hardSets,
                to = to,
                why = AdaptCopy.reduceSets(to, planned.hardSets),
            )
        }
        if (ranked.readiness.allCandidatesFatigued) {
            val alt = alternatePattern(slot.pattern, catalog)
            if (alt != null && alt != slot.pattern) {
                val named = rankSlot(slot.copy(pattern = alt), catalog, fatiguedCtx).ranked.firstOrNull()
                if (named != null) {
                    out += Adjustment.SwapPattern(
                        slot = i,
                        to = alt,
                        why = AdaptCopy.swapToExercise(muscle, named.exercise.name),
                    )
                }
            }
        }
    }

    val isolationDrop = session.exercises.indices.filter { i ->
        val role = session.exercises[i].effectiveSlot().role
        role == SlotRole.ISOLATION || role == SlotRole.ACCESSORY
    }
    val hasCheaper = out.any { it is Adjustment.LowerEffort || it is Adjustment.ReduceSets }
    isolationDrop.forEach { i ->
        val ranked = rankSlot(
            session.exercises[i].effectiveSlot(),
            catalog,
            fatiguedCtx,
        )
        if (ranked.readiness.allCandidatesFatigued && hasCheaper) {
            out += Adjustment.DropSlot(slot = i, why = AdaptCopy.DROP_EXTRA)
        }
    }

    val canDropIsolation = isolationDrop.isNotEmpty()
    if (!hasCheaper && !canDropIsolation) {
        session.exercises.indices
            .filter { session.exercises[it].effectiveSlot().role == SlotRole.PRIMARY }
            .forEach { i ->
                val ranked = rankSlot(session.exercises[i].effectiveSlot(), catalog, fatiguedCtx)
                if (ranked.readiness.allCandidatesFatigued) {
                    out += Adjustment.DropSlot(slot = i, why = AdaptCopy.DROP_TIRED)
                }
            }
    }

    if (readiness.minutesAvailable != null) {
        val keep = (readiness.minutesAvailable / AdaptConstants.MINUTES_PER_SLOT).coerceAtLeast(1)
        if (keep < session.exercises.size) {
            out += Adjustment.ShortenSession(
                dropSlots = session.exercises.indices.drop(keep),
                why = AdaptCopy.shorten(readiness.minutesAvailable),
            )
        }
    }

    require(session.exercises == before) { "adjustForToday must not mutate the session" }
    if (out.isEmpty()) return listOf(Adjustment.AsPlanned)
    return out.sortedWith(compareBy({ preferenceRank(it) }, { slotOf(it) }))
}

private fun preferenceRank(adj: Adjustment): Int = when (adj) {
    Adjustment.AsPlanned -> 0
    is Adjustment.LowerEffort -> 1
    is Adjustment.ReduceSets -> 2
    is Adjustment.SwapPattern -> 3
    is Adjustment.DropSlot -> 4
    is Adjustment.ShortenSession -> 5
}

private fun slotOf(adj: Adjustment): Int = when (adj) {
    Adjustment.AsPlanned -> -1
    is Adjustment.LowerEffort -> adj.slot
    is Adjustment.ReduceSets -> adj.slot
    is Adjustment.SwapPattern -> adj.slot
    is Adjustment.DropSlot -> adj.slot
    is Adjustment.ShortenSession -> adj.dropSlots.firstOrNull() ?: Int.MAX_VALUE
}

private fun spokenMuscle(
    slot: com.gymbuddy.domain.picker.Slot,
    ranked: com.gymbuddy.domain.picker.RankedSlot,
    catalog: Catalog,
): String {
    val fromSlot = slot.targetMuscles.firstOrNull()?.let { catalog.muscles[it]?.name?.lowercase() }
    if (!fromSlot.isNullOrBlank()) return fromSlot
    return ranked.readiness.explanation
        .substringAfter("your ", "")
        .substringBefore(",")
        .substringBefore(" still")
        .ifBlank { "those muscles" }
}

private fun hasActionableSignal(r: Readiness): Boolean {
    if (r.selfReported == SelfReport.ROUGH) return true
    if (r.minutesAvailable != null) return true
    if (r.muscleFatigue.values.any { it >= PickerConstants.FATIGUE_MILD }) return true
    if (r.soreness.values.any { it >= PickerConstants.FATIGUE_MILD }) return true
    if (r.sleepHours != null && r.sleepHours < 6.0) return true
    return false
}

internal fun alternatePattern(pattern: String, catalog: Catalog): String? {
    val group = patternGroup(pattern)
    return catalog.exercises.map { it.pattern }.distinct()
        .firstOrNull { it != pattern && patternGroup(it) == group }
}
