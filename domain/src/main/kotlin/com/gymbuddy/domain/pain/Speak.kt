package com.gymbuddy.domain.pain

import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.adapt.DrainOn
import com.gymbuddy.domain.adapt.SpokenProposal

fun queuePainTalk(phase: WorkoutSessionState, spoken: String): SpokenProposal =
    SpokenProposal(text = spoken, drainOn = DrainOn.RESTING).also {
        check(phase != WorkoutSessionState.SET_ACTIVE || it.drainOn == DrainOn.RESTING)
    }

fun maySpeakPainNow(phase: WorkoutSessionState): Boolean =
    phase != WorkoutSessionState.SET_ACTIVE
