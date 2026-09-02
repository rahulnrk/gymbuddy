package com.gymbuddy.domain.cardio

import com.gymbuddy.domain.picker.PickContext
import com.gymbuddy.domain.picker.Reason
import com.gymbuddy.domain.plan.PlannedSession
import com.gymbuddy.domain.programming.Goal
import com.gymbuddy.domain.programming.SessionSummary
import com.gymbuddy.domain.programming.cardioPlacement

/**
 * Wire [cardioPlacement] onto a lifting day. Hard intervals stay off a heavy
 * lower-body day. Easy aerobic is the default on those days. Variety: a new
 * modality before a new protocol. When every lifting day is heavy lower, say
 * so — do not silently stack easy work and call the ACSM target met.
 */
fun placeOnSession(
    session: PlannedSession,
    goal: Goal,
    ctx: PickContext,
    library: CardioLibrary,
    minutes: Int,
    usedModalities: Collection<String> = emptyList(),
    usedProtocols: Collection<String> = emptyList(),
): CardioPrescription? {
    val advice = cardioPlacement(sessionSummary(session), goal)
    val ranked = rank(goal, ctx, minutes, library)
    val allowed = if (advice.avoidHardIntervals) {
        ranked.filter { it.protocol.intensity == ProtocolIntensity.EASY }
    } else {
        ranked
    }
    return pickVaried(allowed, usedModalities, usedProtocols)
}

fun placeOnWeek(
    week: List<PlannedSession>,
    goal: Goal,
    ctx: PickContext,
    library: CardioLibrary,
    minutesPerLiftDay: Int,
): List<PlannedSession> {
    val usedMod = mutableListOf<String>()
    val usedProt = mutableListOf<String>()
    val placed = week.map { day ->
        val pick = placeOnSession(day, goal, ctx, library, minutesPerLiftDay, usedMod, usedProt)
        if (pick == null) {
            day
        } else {
            usedMod += pick.modality.slug
            usedProt += pick.protocol.slug
            day.copy(cardio = pick)
        }
    }
    val filled = fillAcsmGap(placed, goal, ctx, library, usedMod, usedProt)
    val liftDays = filled.filter { it.entries.isNotEmpty() }
    val heavyDays = liftDays.count {
        cardioPlacement(sessionSummary(it), goal).avoidHardIntervals
    }
    if (liftDays.isEmpty() || heavyDays != liftDays.size) return filled
    val addedSeparate = filled.any { it.entries.isEmpty() && it.cardio != null }
    val note = noRoomForIntervalsNote(liftDays.size, addedSeparate)
    return filled.map { day ->
        if (day.entries.isEmpty()) day else day.copy(notes = day.notes + note)
    }
}

fun sessionSummary(session: PlannedSession): SessionSummary =
    SessionSummary(session.entries.map { it.slot.pattern })

fun noRoomForIntervalsNote(liftDays: Int, addedSeparateDay: Boolean = false): String {
    val base = "Heavy legs all $liftDays days, so no room for intervals"
    return if (addedSeparateDay) {
        "$base on the lifting days."
    } else {
        "$base — a fourth day would let us add them."
    }
}

/**
 * Prefer a modality not yet used this week, then a protocol not yet used.
 * Same bug as Upper A copying Upper B: three identical air-bike recoveries.
 */
fun pickVaried(
    candidates: List<CardioPrescription>,
    usedModalities: Collection<String>,
    usedProtocols: Collection<String>,
): CardioPrescription? {
    if (candidates.isEmpty()) return null
    val freshMod = candidates.filter { it.modality.slug !in usedModalities }
    val pool = freshMod.ifEmpty { candidates }
    val freshProt = pool.filter { it.protocol.slug !in usedProtocols }
    val pick = (freshProt.ifEmpty { pool }).first()
    if (pick.modality.slug in usedModalities) return pick
    val variety = Reason(
        "variety",
        2.0,
        "A different machine than earlier this week.",
    )
    return if (usedModalities.isEmpty()) pick else pick.copy(why = pick.why + variety)
}

private fun fillAcsmGap(
    week: List<PlannedSession>,
    goal: Goal,
    ctx: PickContext,
    library: CardioLibrary,
    usedMod: MutableList<String>,
    usedProt: MutableList<String>,
): List<PlannedSession> {
    val target = weeklyCardioTarget(goal, ctx.user.trainingAge)
    val extra = mutableListOf<PlannedSession>()
    var n = 0
    while (weekAcsmMinutes(week + extra) < target.min) {
        val cardioCount = (week + extra).count { it.cardio != null }
        if (cardioCount >= CardioConstants.FREQUENCY_MAX) break
        val ranked = rank(goal, ctx, 30, library)
        val countable = ranked.filter { it.protocol.intensity != ProtocolIntensity.EASY }
        val pick = pickVaried(countable, usedMod, usedProt) ?: break
        val filled = pick.copy(durationMin = pick.protocol.durationMax)
        usedMod += filled.modality.slug
        usedProt += filled.protocol.slug
        n += 1
        extra += PlannedSession(
            day = "Cardio $n",
            entries = emptyList(),
            cardio = filled,
        )
    }
    return week + extra
}
