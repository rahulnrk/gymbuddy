package com.gymbuddy.domain.adapt

import com.gymbuddy.domain.catalog.Catalog
import com.gymbuddy.domain.picker.PickContext
import com.gymbuddy.domain.picker.SlotRole
import com.gymbuddy.domain.picker.rankSlot
import com.gymbuddy.domain.programming.PlannedSession
import com.gymbuddy.domain.programming.effectiveSlot

/**
 * One response, in trainer order. Substitution is fourth, not first.
 * expectedWaitMinutes == null means we have not asked; we ask, we do not guess.
 */
fun respondToUnavailable(
    slot: SlotIndex,
    session: PlannedSession,
    ctx: PickContext,
    catalog: Catalog,
    expectedWaitMinutes: Int?,
): UnavailableResponse {
    if (expectedWaitMinutes == null) {
        return UnavailableResponse.AskHowLong(AdaptCopy.ASK_HOW_LONG)
    }
    val item = session.exercises.getOrNull(slot)
        ?: return UnavailableResponse.Skip(AdaptCopy.SKIP_MISSING)
    val restSec = item.prescribedRestSec ?: item.exercise.defaultRestSec
    val waitSec = expectedWaitMinutes * 60
    if (waitSec <= restSec) {
        return UnavailableResponse.Wait(AdaptCopy.WAIT)
    }
    val role = item.effectiveSlot().role
    val later = laterSlotWithoutSameKit(slot, session, item.exercise.requiredEquipment)
    val protect = role == SlotRole.PRIMARY || role == SlotRole.SECONDARY || role == SlotRole.CORE
    // Protected work: defer if a later different-kit movement exists, else work in.
    // Isolation / accessory skip both and substitute (test 10).
    if (protect && later != null) {
        val nextName = session.exercises[later].exercise.name
        return UnavailableResponse.Defer(
            untilAfter = later,
            why = AdaptCopy.defer(nextName),
        )
    }
    if (protect) {
        return UnavailableResponse.WorkIn(AdaptCopy.WORK_IN)
    }
    val pick = item.effectiveSlot()
    val withoutOccupied = occupiedTokens(item.exercise.requiredEquipment, ctx.equipment)
    val altCtx = ctx.copy(
        equipment = ctx.equipment - withoutOccupied,
        dislikes = ctx.dislikes + item.exercise.slug,
    )
    val ranked = rankSlot(pick, catalog, altCtx).ranked.filter { it.exercise.slug != item.exercise.slug }
    val top = ranked.firstOrNull()
    return if (top != null) {
        UnavailableResponse.Substitute(
            alternative = top,
            why = AdaptCopy.substitute(top.exercise.name),
        )
    } else {
        UnavailableResponse.Skip(AdaptCopy.SKIP_NO_ALTERNATIVE)
    }
}

private fun laterSlotWithoutSameKit(
    current: Int,
    session: PlannedSession,
    required: List<List<String>>,
): Int? {
    val needed = required.flatten().toSet()
    return session.exercises.indices.firstOrNull { i ->
        if (i <= current) return@firstOrNull false
        val other = session.exercises[i].exercise.requiredEquipment.flatten().toSet()
        needed.intersect(other).isEmpty()
    }
}

private fun occupiedTokens(required: List<List<String>>, have: Set<String>): Set<String> =
    required.mapNotNull { slot -> slot.firstOrNull { it in have } }.toSet()
