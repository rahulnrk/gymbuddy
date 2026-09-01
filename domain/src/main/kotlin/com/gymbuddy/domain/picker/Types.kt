package com.gymbuddy.domain.picker

import com.gymbuddy.domain.catalog.Exercise
import com.gymbuddy.domain.intake.PainReport
import com.gymbuddy.domain.programming.Effort
import com.gymbuddy.domain.programming.IntRange2
import com.gymbuddy.domain.programming.UserContext

enum class SlotRole {
    PRIMARY,
    SECONDARY,
    ACCESSORY,
    ISOLATION,
    CORE,
}

data class Slot(
    val pattern: String,
    val role: SlotRole,
    val targetMuscles: Set<String> = emptySet(),
)

data class PickContext(
    val user: UserContext,
    val equipment: Set<String>,
    val fatigue: Map<String, Double> = emptyMap(),
    val recentSlugs: List<String> = emptyList(),
    val dislikes: Set<String> = emptySet(),
    val acceptedSubstitutions: Set<String> = emptySet(),
    val painReports: List<PainReport> = emptyList(),
)

sealed interface Exclusion {
    data class Contraindicated(val condition: String) : Exclusion
    data class PainfulMovement(val report: PainReport) : Exclusion
    data class MissingEquipment(val unsatisfiedSlots: List<List<String>>) : Exclusion
    data class WrongPattern(val expected: String) : Exclusion
    data class Disliked(val slug: String) : Exclusion
}

data class ExclusionRecord(
    val exercise: Exercise,
    val exclusion: Exclusion,
)

data class CandidateSet(
    val survivors: List<Exercise>,
    val exclusions: List<ExclusionRecord>,
    val keptDespiteDislike: Set<String> = emptySet(),
)

data class Reason(
    val factor: String,
    val delta: Double,
    val explanation: String,
)

data class ScoredExercise(
    val exercise: Exercise,
    val score: Double,
    val reasons: List<Reason>,
)

/**
 * Per-slot dose. Not [com.gymbuddy.domain.programming.Prescription]
 * (that one is progression). Weekly volume is the validator's job.
 */
data class Prescription(
    val sets: Int,
    val repRange: IntRange2,
    val restSec: IntRange2,
    val effort: Effort,
    val cappedBy: List<String>,
)
