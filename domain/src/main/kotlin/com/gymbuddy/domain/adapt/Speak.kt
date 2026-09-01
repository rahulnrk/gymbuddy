package com.gymbuddy.domain.adapt

import com.gymbuddy.domain.WorkoutSessionState

/**
 * Contract 09: no TTS during SET_ACTIVE except safety ≥ high.
 * Adaptation talk is queued and drained on RESTING.
 */
fun queueIfSetActive(phase: WorkoutSessionState, text: String): SpokenProposal {
    return SpokenProposal(
        text = text,
        drainOn = DrainOn.RESTING,
    ).also {
        check(phase != WorkoutSessionState.SET_ACTIVE || it.drainOn == DrainOn.RESTING)
    }
}

fun maySpeakNow(phase: WorkoutSessionState): Boolean =
    phase != WorkoutSessionState.SET_ACTIVE
