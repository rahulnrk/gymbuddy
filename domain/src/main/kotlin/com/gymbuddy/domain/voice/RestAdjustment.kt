package com.gymbuddy.domain.voice

import com.gymbuddy.domain.programming.IntRange2
import com.gymbuddy.domain.programming.SetPerformance
import com.gymbuddy.domain.voice.VoiceInteractionConstants.REST_DECAY_EXTENSION_SEC
import kotlin.math.max

data class RestAdjustment(
    val restSec: Int,
    val extraSec: Int,
    val reason: String?,
)

/**
 * Within-session rest tweak. Extends on a declining set. Never shortens
 * below [restFloorSec] (the goal's published minimum).
 */
fun restAdjustment(
    setsThisExercise: List<SetPerformance>,
    target: IntRange2,
    currentRestSec: Int,
    restFloorSec: Int,
): RestAdjustment {
    val decayed = decliningSet(setsThisExercise, target)
    val extra = if (decayed != null) REST_DECAY_EXTENSION_SEC else 0
    val restSec = max(restFloorSec, currentRestSec + extra)
    val reason = if (decayed != null) {
        "Take another $extra seconds — your last set dropped to ${decayed.reps} reps, " +
            "so let's recover properly before we go again."
    } else {
        null
    }
    return RestAdjustment(restSec = restSec, extraSec = extra, reason = reason)
}

private fun decliningSet(
    sets: List<SetPerformance>,
    target: IntRange2,
): SetPerformance? {
    if (sets.size < 2) return null
    val last = sets.last()
    val prev = sets[sets.size - 2]
    if (last.reps >= prev.reps) return null
    if (last.reps >= target.max) return null
    return last
}
