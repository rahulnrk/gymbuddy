package com.gymbuddy.domain.warmup

import com.gymbuddy.domain.plan.PlannedSession
import com.gymbuddy.domain.plan.estimateMinutes

/**
 * One session as a user would hear it: warm-up, work, cooldown.
 */
fun speakSession(session: PlannedSession): String = buildString {
    appendLine(session.day)
    appendLine()
    appendLine("Before you lift:")
    val warmup = session.warmup
    if (warmup == null || (warmup.pulse == null && warmup.drills.isEmpty() && warmup.rampSets.isEmpty())) {
        appendLine("  No warm-up on file for this session.")
    } else {
        warmup.pulse?.let { appendLine("  ${WarmupCopy.drillLine(it)}") }
        warmup.drills.forEach { appendLine("  ${WarmupCopy.drillLine(it)}") }
        if (warmup.rampSets.isNotEmpty()) {
            val lift = warmup.rampSets.first().liftName
            appendLine("  Ramp sets on $lift, no invented load:")
            warmup.rampSets.forEach { appendLine("    ${WarmupCopy.rampLine(it)}") }
        }
        warmup.notes.forEach { appendLine("  $it") }
    }
    appendLine()
    appendLine("The work:")
    session.entries.forEach { e ->
        append("  ")
        append(e.exercise.name)
        append(" — ")
        append(e.sets)
        append(" × ")
        append(e.repRange.min)
        append("–")
        append(e.repRange.max)
        append(" reps, ")
        append(e.restSec.min)
        append(" seconds rest, load unassigned")
        appendLine()
    }
    appendLine()
    appendLine("After:")
    if (session.cooldown.isEmpty()) {
        appendLine("  No cooldown stretches for this session.")
    } else {
        session.cooldownDisclaimer?.let { appendLine("  $it") }
        session.cooldown.forEach { appendLine("  ${WarmupCopy.stretchLine(it)}") }
    }
    appendLine()
    append("About ${estimateMinutes(session)} minutes of warm-up and work.")
}
