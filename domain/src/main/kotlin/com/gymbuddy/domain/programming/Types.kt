package com.gymbuddy.domain.programming

import com.gymbuddy.domain.catalog.Exercise
import com.gymbuddy.domain.catalog.Provenance
import com.gymbuddy.domain.picker.Slot
import com.gymbuddy.domain.picker.SlotRole

enum class Goal {
    STRENGTH,
    HYPERTROPHY,
    ENDURANCE,
    GENERAL_FITNESS,
    FAT_LOSS,
}

enum class TrainingAge {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
}

enum class ExerciseKind {
    COMPOUND,
    ISOLATION,
}

enum class Severity {
    INFO,
    WARN,
    BLOCK,
}

enum class EffortConfidence {
    TRUST,
    LOW,
}

enum class PatternGroup {
    PUSH,
    PULL,
    LEGS,
    CORE,
    OTHER,
}

/** Reps in reserve. Inclusive. */
data class Effort(
    val rirMin: Int,
    val rirMax: Int,
    val provenance: Provenance,
) {
    init {
        require(rirMin <= rirMax) { "Effort.rirMin ($rirMin) > rirMax ($rirMax)" }
    }
}

/** Inclusive integer range. Every public range carries provenance. */
data class IntRange2(
    val min: Int,
    val max: Int,
    val provenance: Provenance,
) {
    init {
        require(min <= max) { "IntRange2.min ($min) > max ($max)" }
    }

    fun contains(value: Int): Boolean = value in min..max
}

data class Landmark(
    val min: Int,
    val max: Int,
    val basis: Provenance,
) {
    init {
        require(min <= max) { "Landmark.min ($min) > max ($max)" }
    }

    fun contains(value: Int): Boolean = value in min..max
}

sealed interface ProgressionDecision {
    data object Hold : ProgressionDecision
    data class AddReps(val to: Int) : ProgressionDecision
    data class AddLoadKg(val delta: Double) : ProgressionDecision
    data class AddSet(val to: Int) : ProgressionDecision
    data object Deload : ProgressionDecision
    data object RegressExercise : ProgressionDecision
}

data class SetPerformance(
    val reps: Int,
    val loadKg: Double? = null,
    val rir: Int? = null,
    val techniqueBroken: Boolean = false,
)

data class Prescription(
    val reps: IntRange2,
    val sets: Int,
    val effort: Effort,
    val loadStepKg: Double,
)

/**
 * Only signals the app can actually observe today. Null means unobserved;
 * [deloadDue] must still work when every field is null.
 */
data class RecoverySignals(
    val sleepPoor: Boolean? = null,
    val painReported: Boolean? = null,
    val motivationLow: Boolean? = null,
) {
    val poorObservable: Boolean
        get() = sleepPoor == true || painReported == true || motivationLow == true
}

data class SessionSummary(
    val patternIds: List<String>,
)

data class CardioAdvice(
    val avoidHardIntervals: Boolean,
    val message: String,
    val provenance: Provenance,
)

data class EffortReport(
    val confidence: EffortConfidence,
    val provenance: Provenance,
)

data class Violation(
    val rule: String,
    val severity: Severity,
    val message: String,
    val provenance: Provenance,
)

/**
 * Programming-time user. Not [com.gymbuddy.domain.context.UserStateContext]
 * (the frozen four-context snapshot).
 */
data class UserContext(
    val goal: Goal,
    val trainingAge: TrainingAge,
    val conditions: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val trainsWithPartner: Boolean? = null,
    val sharpPainSlugs: Set<String> = emptySet(),
    val daysPerWeek: Int? = null,
    val typicalDurationMin: Int? = null,
)

data class PlannedExercise(
    val exercise: Exercise,
    val hardSets: Int,
    val prescribedRepMin: Int? = null,
    val prescribedRepMax: Int? = null,
    val prescribedRestSec: Int? = null,
    val slot: Slot? = null,
)

fun PlannedExercise.effectiveSlot(): Slot = slot ?: Slot(
    pattern = exercise.pattern,
    role = when {
        exercise.pattern == "core" -> SlotRole.CORE
        exercise.kind == "isolation" -> SlotRole.ISOLATION
        else -> SlotRole.PRIMARY
    },
)

data class PlannedSession(
    val exercises: List<PlannedExercise>,
)

fun Exercise.exerciseKind(): ExerciseKind =
    if (kind == "compound") ExerciseKind.COMPOUND else ExerciseKind.ISOLATION
