package com.gymbuddy.domain.cardio

import com.gymbuddy.domain.picker.PickContext
import com.gymbuddy.domain.plan.PlannedSession
import com.gymbuddy.domain.programming.Goal
import com.gymbuddy.domain.programming.SessionSummary
import com.gymbuddy.domain.programming.cardioPlacement

/**
 * Wire [cardioPlacement] onto a lifting day. Hard intervals stay off a heavy
 * lower-body day. Easy aerobic is the default. When in doubt, easy work on a
 * separate day — conservative concurrent-training stance.
 */
fun placeOnSession(
    session: PlannedSession,
    goal: Goal,
    ctx: PickContext,
    library: CardioLibrary,
    minutes: Int,
): CardioPrescription? {
    val advice = cardioPlacement(sessionSummary(session), goal)
    val ranked = rank(goal, ctx, minutes, library)
    val allowed = if (advice.avoidHardIntervals) {
        ranked.filter { !it.protocol.hard }
    } else {
        ranked
    }
    return allowed.firstOrNull()
}

fun placeOnWeek(
    week: List<PlannedSession>,
    goal: Goal,
    ctx: PickContext,
    library: CardioLibrary,
    minutesPerLiftDay: Int,
): List<PlannedSession> {
    val withDays = week.map { day ->
        val pick = placeOnSession(day, goal, ctx, library, minutesPerLiftDay)
        if (pick == null) day else day.copy(cardio = pick)
    }
    return fillToWeeklyTarget(withDays, goal, ctx, library)
}

fun sessionSummary(session: PlannedSession): SessionSummary =
    SessionSummary(session.entries.map { it.slot.pattern })

private fun fillToWeeklyTarget(
    week: List<PlannedSession>,
    goal: Goal,
    ctx: PickContext,
    library: CardioLibrary,
): List<PlannedSession> {
    val target = weeklyCardioTarget(goal, ctx.user.trainingAge)
    var total = week.mapNotNull { it.cardio }.sumOf { it.durationMin }
    if (total >= target.min) return week
    val extra = mutableListOf<PlannedSession>()
    val easy = rank(goal, ctx, 30, library).firstOrNull { !it.protocol.hard }
        ?: rank(goal, ctx, 30, library).firstOrNull()
        ?: return week
    var n = 0
    while (total < target.min) {
        val cardioCount = week.count { it.cardio != null } + extra.size
        if (cardioCount >= CardioConstants.FREQUENCY_MAX) break
        n += 1
        val duration = easy.durationMin
        extra += PlannedSession(
            day = "Cardio $n",
            entries = emptyList(),
            cardio = easy.copy(durationMin = duration),
        )
        total += duration
    }
    return week + extra
}
