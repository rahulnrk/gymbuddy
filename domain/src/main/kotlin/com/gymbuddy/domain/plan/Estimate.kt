package com.gymbuddy.domain.plan

/**
 * Estimated session length: real warm-up plan plus working time per set
 * plus rest midpoint. Cooldown is after the session and is not counted.
 */
fun estimateMinutes(session: PlannedSession): Int =
    estimateMinutes(session.entries, session.warmup?.estimatedMinutes ?: 0)

fun estimateMinutes(entries: List<PlannedEntry>, warmupMin: Int = 0): Int {
    if (entries.isEmpty()) return warmupMin
    val workSec = entries.sumOf { entry ->
        val restMid = (entry.restSec.min + entry.restSec.max) / 2
        entry.sets * (PlanConstants.SECONDS_PER_WORKING_SET + restMid)
    }
    return (workSec + 30) / 60 + warmupMin
}

fun withinDurationWindow(estimated: Int, typical: Int): Boolean {
    val lo = typical - PlanConstants.DURATION_TOLERANCE_MIN
    val hi = typical + PlanConstants.DURATION_TOLERANCE_MIN
    return estimated in lo..hi
}
