package com.gymbuddy.domain.adapt

import com.gymbuddy.domain.picker.ScoredExercise

typealias SlotIndex = Int

enum class SelfReport {
    GOOD,
    OK,
    ROUGH,
    UNKNOWN,
}

data class Readiness(
    val muscleFatigue: Map<String, Double> = emptyMap(),
    val sleepHours: Double? = null,
    val soreness: Map<String, Double> = emptyMap(),
    val minutesAvailable: Int? = null,
    val selfReported: SelfReport? = SelfReport.UNKNOWN,
)

sealed interface Adjustment {
    data object AsPlanned : Adjustment
    data class LowerEffort(val slot: SlotIndex, val extraRir: Int, val why: String) : Adjustment
    data class ReduceSets(val slot: SlotIndex, val from: Int, val to: Int, val why: String) : Adjustment
    data class SwapPattern(val slot: SlotIndex, val to: String, val why: String) : Adjustment
    data class DropSlot(val slot: SlotIndex, val why: String) : Adjustment
    data class ShortenSession(val dropSlots: List<SlotIndex>, val why: String) : Adjustment
}

sealed interface UnavailableResponse {
    data class AskHowLong(val question: String) : UnavailableResponse
    data class Wait(val why: String) : UnavailableResponse
    data class WorkIn(val why: String) : UnavailableResponse
    data class Defer(val untilAfter: SlotIndex, val why: String) : UnavailableResponse
    data class Substitute(val alternative: ScoredExercise, val why: String) : UnavailableResponse
    data class Skip(val why: String) : UnavailableResponse
}

/** Queue coaching until RESTING. Contract 09: no TTS during SET_ACTIVE. */
enum class DrainOn { RESTING }

data class SpokenProposal(
    val text: String,
    val drainOn: DrainOn = DrainOn.RESTING,
)

fun Adjustment.spoken(): String? = when (this) {
    Adjustment.AsPlanned -> null
    is Adjustment.LowerEffort -> why
    is Adjustment.ReduceSets -> why
    is Adjustment.SwapPattern -> why
    is Adjustment.DropSlot -> why
    is Adjustment.ShortenSession -> why
}

fun UnavailableResponse.spoken(): String = when (this) {
    is UnavailableResponse.AskHowLong -> question
    is UnavailableResponse.Wait -> why
    is UnavailableResponse.WorkIn -> why
    is UnavailableResponse.Defer -> why
    is UnavailableResponse.Substitute -> why
    is UnavailableResponse.Skip -> why
}
