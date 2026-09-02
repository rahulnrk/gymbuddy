package com.gymbuddy.domain.voice

import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.plan.PlannedSession
import com.gymbuddy.domain.voice.VoiceInteractionConstants.CONFIDENCE_HORIZON_MS

enum class AnchorKind {
    USER_UTTERANCE,
    LOGGED_EVENT,
}

data class CoachClock(
    val workoutElapsedMs: Long,
    val lastAnchorMs: Long,
    val anchorKind: AnchorKind,
    val resyncOffered: Boolean = false,
) {
    val sinceAnchorMs: Long get() = workoutElapsedMs - lastAnchorMs

    fun at(elapsedMs: Long): CoachClock = copy(workoutElapsedMs = elapsedMs)

    fun anchored(atMs: Long = workoutElapsedMs, kind: AnchorKind): CoachClock =
        copy(
            workoutElapsedMs = atMs,
            lastAnchorMs = atMs,
            anchorKind = kind,
            resyncOffered = false,
        )

    fun markResyncOffered(): CoachClock = copy(resyncOffered = true)
}

sealed interface CoachAct {
    val text: String
    val reason: String

    data class RestComplete(
        override val text: String,
        override val reason: String,
    ) : CoachAct

    data class ReSync(
        override val text: String,
        override val reason: String,
    ) : CoachAct
}

fun maySpeakProactively(
    clock: CoachClock,
    session: WorkoutSessionState,
    safetyHigh: Boolean = false,
): Boolean {
    if (session == WorkoutSessionState.SET_ACTIVE) return safetyHigh
    return clock.sinceAnchorMs < CONFIDENCE_HORIZON_MS
}

/**
 * At most a rest-complete prompt, one re-sync at the horizon, or null.
 * Null is the common case. Safety speech during a loaded set is not this function.
 */
fun proactiveUtterance(
    clock: CoachClock,
    session: WorkoutSessionState,
    plan: PlannedSession,
    restElapsedSec: Int = 0,
    safetyHigh: Boolean = false,
): CoachAct? {
    if (session == WorkoutSessionState.SET_ACTIVE && !safetyHigh) return null
    if (session == WorkoutSessionState.SET_ACTIVE) return null
    if (clock.sinceAnchorMs >= CONFIDENCE_HORIZON_MS) {
        if (clock.resyncOffered) return null
        val minutes = CONFIDENCE_HORIZON_MS / 60_000L
        return CoachAct.ReSync(
            text = "Ready when you are?",
            reason = "I have not heard from you in $minutes minutes, so I am waiting.",
        )
    }
    if (session != WorkoutSessionState.RESTING) return null
    val restMin = plan.entries.firstOrNull()?.restSec?.min ?: return null
    if (restElapsedSec < restMin) return null
    return CoachAct.RestComplete(
        text = "That's your rest. Ready for the next set?",
        reason = "The prescribed rest of $restMin seconds is up.",
    )
}
