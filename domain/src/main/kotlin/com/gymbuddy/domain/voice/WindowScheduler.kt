package com.gymbuddy.domain.voice

import com.gymbuddy.domain.voice.VoiceInteractionConstants.OBSERVED_REST_RECENT
import com.gymbuddy.domain.voice.VoiceInteractionConstants.OBSERVED_REST_SAMPLE_MIN
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_MAX_SILENCE_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_MIN_GAP_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_SET_END_SLACK_MS

data class ObservedRest(
    val exerciseId: String?,
    val durationSec: Int,
)

data class RestHistory(
    val rests: List<ObservedRest> = emptyList(),
    val setsByExercise: Map<String, Int> = emptyMap(),
    val unscopedSets: Int = 0,
)

sealed interface RestChoice {
    val sec: Int

    data class Planned(override val sec: Int) : RestChoice
    data class Observed(
        override val sec: Int,
        val sampleCount: Int,
        val perExercise: Boolean,
    ) : RestChoice
}

sealed interface ScheduledWindow {
    data class Open(val atElapsedMs: Long, val reason: String) : ScheduledWindow
    data class Hold(val reason: String) : ScheduledWindow
}

fun recordRest(history: RestHistory, exerciseId: String?, durationSec: Int): RestHistory =
    history.copy(rests = history.rests + ObservedRest(exerciseId, durationSec))

fun recordCompletedSet(history: RestHistory, exerciseId: String?): RestHistory {
    if (exerciseId == null) return history.copy(unscopedSets = history.unscopedSets + 1)
    val n = (history.setsByExercise[exerciseId] ?: 0) + 1
    return history.copy(setsByExercise = history.setsByExercise + (exerciseId to n))
}

/**
 * Completed sets for one exercise. A rest follows a completed set.
 * n intra-exercise rests imply at least n+1 completed sets — a 3-set
 * exercise typically yields 2 rests.
 */
fun completedSetsForExercise(history: RestHistory, exerciseId: String?): Int {
    val marked = if (exerciseId == null) {
        history.unscopedSets
    } else {
        history.setsByExercise[exerciseId] ?: 0
    }
    val restCount = history.rests.count { it.exerciseId == exerciseId }
    val inferred = if (restCount == 0) 0 else restCount + 1
    return maxOf(marked, inferred)
}

fun completedSetsForUser(history: RestHistory): Int {
    val ids = buildSet {
        addAll(history.setsByExercise.keys)
        history.rests.mapNotNullTo(this) { it.exerciseId }
    }
    return ids.sumOf { completedSetsForExercise(history, it) } +
        completedSetsForExercise(history, null)
}

fun trustedRest(history: RestHistory, exerciseId: String?, planRestSec: Int): RestChoice {
    val perEx = history.rests.filter { it.exerciseId == exerciseId }.takeLast(OBSERVED_REST_RECENT)
    if (perEx.size >= OBSERVED_REST_SAMPLE_MIN) {
        return RestChoice.Observed(
            sec = medianSec(perEx.map { it.durationSec }),
            sampleCount = perEx.size,
            perExercise = true,
        )
    }
    val perUser = history.rests.takeLast(OBSERVED_REST_RECENT)
    if (perUser.size >= OBSERVED_REST_SAMPLE_MIN) {
        return RestChoice.Observed(
            sec = medianSec(perUser.map { it.durationSec }),
            sampleCount = perUser.size,
            perExercise = false,
        )
    }
    return RestChoice.Planned(planRestSec)
}

fun medianSec(values: List<Int>): Int {
    require(values.isNotEmpty())
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}

/**
 * Next scheduled listen window. Never mid-set. Emergency is not this function.
 */
fun nextScheduledWindow(
    prediction: SessionPrediction,
    nowElapsedMs: Long,
    lastWindowAtMs: Long?,
): ScheduledWindow {
    if (prediction.phase == PredictedPhase.LIFTING) {
        return ScheduledWindow.Hold("predicted lifting — a window here would land mid-set")
    }
    if (prediction.phase == PredictedPhase.IDLE) {
        return ScheduledWindow.Hold("no session in progress")
    }
    val sinceLast = lastWindowAtMs?.let { nowElapsedMs - it }
    if (sinceLast != null && sinceLast < WINDOW_MIN_GAP_MS) {
        return ScheduledWindow.Hold("minimum gap since the last window")
    }
    if (prediction.phase == PredictedPhase.RESTING &&
        prediction.phaseElapsedMs <= WINDOW_SET_END_SLACK_MS
    ) {
        return ScheduledWindow.Open(nowElapsedMs, "predicted set end")
    }
    if (sinceLast != null && sinceLast >= WINDOW_MAX_SILENCE_MS) {
        return ScheduledWindow.Open(nowElapsedMs, "quiet user — not abandoned")
    }
    if (lastWindowAtMs == null && prediction.phase == PredictedPhase.BETWEEN_EXERCISES) {
        if (nowElapsedMs >= WINDOW_MAX_SILENCE_MS) {
            return ScheduledWindow.Open(nowElapsedMs, "quiet user — not abandoned")
        }
    }
    return ScheduledWindow.Hold("waiting for a set-end gap")
}
