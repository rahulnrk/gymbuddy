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
    val notes = mutableListOf<String>()
    val liftDays = placed.filter { it.entries.isNotEmpty() }
    val heavyDays = liftDays.count {
        cardioPlacement(sessionSummary(it), goal).avoidHardIntervals
    }
    val allHeavy = liftDays.isNotEmpty() && heavyDays == liftDays.size
    val credit = weekAcsmMinutes(placed)
    val target = weeklyCardioTarget(goal, ctx.user.trainingAge)
    val daysUsed = ctx.user.daysPerWeek ?: placed.size
    val easyMinutes = placed.mapNotNull { it.cardio }
        .filter { it.protocol.intensity == ProtocolIntensity.EASY }
        .sumOf { it.durationMin }
    if (credit < target.min) {
        notes += shortfallNote(
            credit = credit,
            targetMin = target.min,
            daysUsed = daysUsed,
            easyMinutes = easyMinutes,
            allLiftDaysHeavy = allHeavy,
        )
    } else if (allHeavy) {
        notes += noRoomForIntervalsNote(liftDays.size, addedSeparateDay = false)
    }
    if (notes.isEmpty()) return placed
    return placed.map { day -> day.copy(notes = day.notes + notes) }
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

fun shortfallNote(
    @Suppress("UNUSED_PARAMETER") credit: Double,
    targetMin: Int,
    daysUsed: Int,
    easyMinutes: Int = 0,
    allLiftDaysHeavy: Boolean = false,
): String {
    val ack = when {
        easyMinutes > 0 && allLiftDaysHeavy ->
            "Easy cardio this week is base work. It fits alongside heavy legs."
        easyMinutes > 0 ->
            "Easy cardio this week is base work. It fits the days you already train."
        else ->
            "This week's cardio stayed inside the days you already train."
    }
    val guideline =
        "Separately, the health guideline wants about $targetMin minutes a week at an effort where you can talk but not sing."
    val offer = "A ${ordinal(daysUsed + 1)} day would get you there."
    return "$ack $guideline $offer"
}

internal fun ordinal(n: Int): String = when (n) {
    1 -> "first"
    2 -> "second"
    3 -> "third"
    4 -> "fourth"
    5 -> "fifth"
    6 -> "sixth"
    7 -> "seventh"
    else -> "$n-th"
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
