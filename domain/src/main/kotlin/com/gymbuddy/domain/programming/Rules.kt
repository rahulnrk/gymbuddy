package com.gymbuddy.domain.programming

import com.gymbuddy.domain.catalog.Exercise
import com.gymbuddy.domain.catalog.Provenance

/**
 * A hard set is a working set taken close enough to failure to drive
 * adaptation. Warm-up and ramp sets do not count.
 *
 * These functions return ranges. They do not produce a workout.
 */
fun weeklyHardSets(goal: Goal, age: TrainingAge): Landmark {
    return when (goal) {
        Goal.HYPERTROPHY -> when (age) {
            TrainingAge.BEGINNER -> landmark(
                ProgrammingConstants.HYPERTROPHY_BEGINNER_HARD_SETS_MIN,
                ProgrammingConstants.HYPERTROPHY_BEGINNER_HARD_SETS_MAX,
                ProgrammingProvenance.BEHM_PETERSON,
            )
            TrainingAge.INTERMEDIATE, TrainingAge.ADVANCED -> landmark(
                ProgrammingConstants.HYPERTROPHY_INTERMEDIATE_HARD_SETS_MIN,
                ProgrammingConstants.HYPERTROPHY_INTERMEDIATE_HARD_SETS_MAX,
                ProgrammingProvenance.ACSM_BAZ_SCHOENFELD,
            )
        }
        Goal.STRENGTH -> when (age) {
            TrainingAge.BEGINNER -> landmark(
                ProgrammingConstants.STRENGTH_BEGINNER_HARD_SETS_MIN,
                ProgrammingConstants.STRENGTH_BEGINNER_HARD_SETS_MAX,
                ProgrammingProvenance.RHEA_PETERSON,
            )
            TrainingAge.INTERMEDIATE, TrainingAge.ADVANCED -> landmark(
                ProgrammingConstants.STRENGTH_HARD_SETS_MIN,
                ProgrammingConstants.STRENGTH_HARD_SETS_MAX,
                ProgrammingProvenance.ACSM_RALSTON,
            )
        }
        Goal.ENDURANCE -> landmark(
            ProgrammingConstants.ENDURANCE_HARD_SETS_MIN,
            ProgrammingConstants.ENDURANCE_HARD_SETS_MAX,
            ProgrammingProvenance.HEURISTIC,
        )
        Goal.FAT_LOSS -> {
            val hypert = weeklyHardSets(Goal.HYPERTROPHY, age)
            Landmark(hypert.min, hypert.max, ProgrammingProvenance.ROTH_2022)
        }
        Goal.GENERAL_FITNESS -> landmark(
            ProgrammingConstants.GENERAL_FITNESS_HARD_SETS_MIN,
            ProgrammingConstants.GENERAL_FITNESS_HARD_SETS_MAX,
            ProgrammingProvenance.HEURISTIC,
        )
    }
}

fun effort(
    @Suppress("UNUSED_PARAMETER") goal: Goal,
    @Suppress("UNUSED_PARAMETER") age: TrainingAge,
): Effort {
    // Training age does not change the target. Novices estimate RIR as well as
    // trained lifters (Bermúdez Droguett 2025; Zourdos 2019). Chronological age
    // would, but the app has no older-adult input today.
    return Effort(
        ProgrammingConstants.MOST_WORK_RIR_MIN,
        ProgrammingConstants.MOST_WORK_RIR_MAX,
        ProgrammingProvenance.ACSM_2026,
    )
}

/**
 * Trust a spoken RIR only when it is close to failure and the set is not long.
 * Low-confidence reports must not drive a load change on their own.
 */
fun reportedEffortConfidence(reportedRir: Int, repsInSet: Int): EffortReport {
    val trusted = reportedRir <= ProgrammingConstants.RIR_TRUST_MAX &&
        repsInSet <= ProgrammingConstants.RIR_TRUST_REPS_MAX
    return EffortReport(
        confidence = if (trusted) EffortConfidence.TRUST else EffortConfidence.LOW,
        provenance = ProgrammingProvenance.ZOURDOS_2019,
    )
}

fun repRange(goal: Goal): IntRange2 = when (goal) {
    Goal.STRENGTH -> range(
        ProgrammingConstants.STRENGTH_REPS_MIN,
        ProgrammingConstants.STRENGTH_REPS_MAX,
        ProgrammingProvenance.STRENGTH_REPS,
    )
    Goal.HYPERTROPHY -> range(
        ProgrammingConstants.HYPERTROPHY_REPS_MIN,
        ProgrammingConstants.HYPERTROPHY_REPS_MAX,
        ProgrammingProvenance.NASM_OPT_42,
    )
    Goal.ENDURANCE -> range(
        ProgrammingConstants.ENDURANCE_REPS_MIN,
        ProgrammingConstants.ENDURANCE_REPS_MAX,
        ProgrammingProvenance.NASM_OPT_42,
    )
    Goal.FAT_LOSS -> {
        val hypert = repRange(Goal.HYPERTROPHY)
        IntRange2(hypert.min, hypert.max, ProgrammingProvenance.CARLSON_2022)
    }
    Goal.GENERAL_FITNESS -> range(
        ProgrammingConstants.GENERAL_FITNESS_REPS_MIN,
        ProgrammingConstants.GENERAL_FITNESS_REPS_MAX,
        ProgrammingProvenance.HEURISTIC,
    )
}

fun restSeconds(
    goal: Goal,
    kind: ExerciseKind,
    age: TrainingAge = TrainingAge.INTERMEDIATE,
): IntRange2 {
    if (goal == Goal.STRENGTH && age == TrainingAge.BEGINNER) {
        return range(
            ProgrammingConstants.STRENGTH_REST_SEC_BEGINNER_MIN,
            ProgrammingConstants.STRENGTH_REST_SEC_BEGINNER_MAX,
            ProgrammingProvenance.GRGIC_2017,
        )
    }
    if (goal == Goal.STRENGTH && kind == ExerciseKind.ISOLATION) {
        return range(
            ProgrammingConstants.STRENGTH_ISOLATION_REST_SEC_MIN,
            ProgrammingConstants.STRENGTH_ISOLATION_REST_SEC_MAX,
            ProgrammingProvenance.SENNA_2016,
        )
    }
    return when (goal) {
        Goal.STRENGTH -> range(
            ProgrammingConstants.STRENGTH_REST_SEC_MIN,
            ProgrammingConstants.STRENGTH_REST_SEC_MAX,
            ProgrammingProvenance.SENNA_2016,
        )
        Goal.HYPERTROPHY -> range(
            ProgrammingConstants.HYPERTROPHY_REST_SEC_MIN,
            ProgrammingConstants.HYPERTROPHY_REST_SEC_MAX,
            ProgrammingProvenance.SINGER_2024,
        )
        Goal.ENDURANCE -> range(
            ProgrammingConstants.ENDURANCE_REST_SEC_MIN,
            ProgrammingConstants.ENDURANCE_REST_SEC_MAX,
            ProgrammingProvenance.GUIDE_43,
        )
        Goal.FAT_LOSS -> {
            val hypert = restSeconds(Goal.HYPERTROPHY, kind, age)
            IntRange2(hypert.min, hypert.max, ProgrammingProvenance.PRODUCT_FAT_LOSS_REST)
        }
        Goal.GENERAL_FITNESS -> range(
            ProgrammingConstants.GENERAL_FITNESS_REST_SEC_MIN,
            ProgrammingConstants.GENERAL_FITNESS_REST_SEC_MAX,
            ProgrammingProvenance.HEURISTIC,
        )
    }
}

/**
 * Catalog `defaultRestSec` wins over [restSeconds] when an exercise is known.
 * The catalog stores a point, so the range collapses to that point.
 */
fun effectiveRestSeconds(exercise: Exercise, @Suppress("UNUSED_PARAMETER") goal: Goal): IntRange2 {
    return IntRange2(
        exercise.defaultRestSec,
        exercise.defaultRestSec,
        catalogFieldProvenance(exercise, "defaultRestSec"),
    )
}

/**
 * Catalog `repWindow` wins over [repRange] when an exercise is known.
 * A high-consequence lift that is caught by a rack is then floored at
 * [ProgrammingConstants.STRENGTH_UNSPOTTED_REP_FLOOR] unless the user has
 * a rack, cage, or Smith. The validator reports; this cap is the rule a
 * later template engine should ask, not a silent rewrite.
 */
fun effectiveRepRange(
    exercise: Exercise,
    @Suppress("UNUSED_PARAMETER") goal: Goal,
    equipmentIds: Collection<String> = emptyList(),
    trainsWithPartner: Boolean? = null,
): IntRange2 {
    val catalogRange = IntRange2(
        exercise.repWindow.min,
        exercise.repWindow.max,
        catalogFieldProvenance(exercise, "repWindow"),
    )
    val floor = unspottedRepFloor(exercise, equipmentIds, trainsWithPartner) ?: return catalogRange
    if (catalogRange.min >= floor) return catalogRange
    val max = maxOf(catalogRange.max, floor)
    return IntRange2(floor, max, ProgrammingProvenance.UNSPOTTED_REP_FLOOR)
}

fun hasCatchForFailure(
    equipmentIds: Collection<String>,
    trainsWithPartner: Boolean? = null,
): Boolean =
    equipmentIds.any { it in ProgrammingConstants.CATCH_EQUIPMENT_IDS } ||
        trainsWithPartner == true

/**
 * Floor of 6 reps when a miss needs a catch and the user has none.
 * Only lifts that list a rack/cage/Smith in required or optional gear —
 * a deadlift miss is a drop, not a crush. A human spotter is
 * [trainsWithPartner]; null is not yes.
 */
fun unspottedRepFloor(
    exercise: Exercise,
    equipmentIds: Collection<String>,
    trainsWithPartner: Boolean? = null,
): Int? {
    if (exercise.consequenceOfFailure != "high") return null
    val listed = exercise.requiredEquipment.flatten() + exercise.optionalEquipment
    if (listed.none { it in ProgrammingConstants.CATCH_EQUIPMENT_IDS }) return null
    if (hasCatchForFailure(equipmentIds, trainsWithPartner)) return null
    return ProgrammingConstants.STRENGTH_UNSPOTTED_REP_FLOOR
}

fun weeklyFrequency(
    @Suppress("UNUSED_PARAMETER") goal: Goal,
    @Suppress("UNUSED_PARAMETER") age: TrainingAge,
): IntRange2 {
    return range(
        ProgrammingConstants.WEEKLY_FREQUENCY_MIN,
        ProgrammingConstants.WEEKLY_FREQUENCY_MAX,
        ProgrammingProvenance.GUIDE_43,
    )
}

/**
 * Higher means earlier in the session. High-skill, high-fatigue, compound
 * work outranks isolation and core.
 */
fun orderScore(exercise: Exercise): Int {
    val skill = when (exercise.skillDemand) {
        "high" -> ProgrammingConstants.ORDER_SKILL_HIGH
        "moderate" -> ProgrammingConstants.ORDER_SKILL_MODERATE
        else -> ProgrammingConstants.ORDER_SKILL_LOW
    }
    val fatigue = when (exercise.fatigueCost) {
        "high" -> ProgrammingConstants.ORDER_FATIGUE_HIGH
        "moderate" -> ProgrammingConstants.ORDER_FATIGUE_MODERATE
        else -> ProgrammingConstants.ORDER_FATIGUE_LOW
    }
    val kindBonus = if (exercise.exerciseKind() == ExerciseKind.COMPOUND) {
        ProgrammingConstants.ORDER_COMPOUND_BONUS
    } else {
        0
    }
    val corePenalty = if (patternGroup(exercise.pattern) == PatternGroup.CORE) {
        ProgrammingConstants.ORDER_CORE_PENALTY
    } else {
        0
    }
    return skill + fatigue + kindBonus - corePenalty
}

fun validateOrder(session: List<Exercise>): List<Violation> {
    val out = mutableListOf<Violation>()
    for (i in 0 until session.size - 1) {
        val current = session[i]
        val next = session[i + 1]
        if (orderScore(next) > orderScore(current)) {
            out += Violation(
                rule = "order.freshness",
                severity = Severity.WARN,
                message = "${next.name} is up before ${current.name}. " +
                    "The heavy, high-skill work belongs while you're fresh.",
                provenance = ProgrammingProvenance.NSCA_ORDER,
            )
        }
    }
    return out
}

fun patternGroup(patternId: String): PatternGroup = when (patternId) {
    "horizontal push", "incline push", "decline push", "vertical push",
    "chest isolation", "delt isolation", "triceps",
    -> PatternGroup.PUSH
    "horizontal pull", "vertical pull", "lats", "traps", "rear delt", "biceps",
    -> PatternGroup.PULL
    "squat", "lunge", "quads", "hinge", "hamstrings", "lower back", "calves", "carry",
    -> PatternGroup.LEGS
    "core" -> PatternGroup.CORE
    else -> PatternGroup.OTHER
}

fun isHeavyLower(patternId: String): Boolean = patternId in setOf(
    "squat", "lunge", "hinge", "quads", "hamstrings", "lower back",
)

/**
 * Double progression: fill the rep range at the target effort, then add load
 * and drop back to the bottom of the range. Load increment comes from
 * [Prescription.loadStepKg] — never hardcoded. Technique degradation outranks
 * progression. At most one variable changes per decision.
 */
fun nextSession(history: List<SetPerformance>, target: Prescription): ProgressionDecision {
    if (history.isEmpty()) return ProgressionDecision.Hold
    val broken = history.count { it.techniqueBroken }
    if (broken > 0) {
        return if (broken * 2 >= history.size) {
            ProgressionDecision.RegressExercise
        } else {
            ProgressionDecision.Hold
        }
    }
    val tooHard = history.any { perf ->
        val rir = perf.rir
        rir != null &&
            rir < target.effort.rirMin &&
            reportedEffortConfidence(rir, perf.reps).confidence == EffortConfidence.TRUST
    }
    if (tooHard) return ProgressionDecision.Hold

    val allFilled = history.all { it.reps >= target.reps.max }
    if (allFilled) {
        val step = target.loadStepKg
        return if (step > 0.0) ProgressionDecision.AddLoadKg(step) else ProgressionDecision.Hold
    }
    val lagging = history.minOf { it.reps }
    if (lagging < target.reps.max) {
        val to = (lagging + 1).coerceAtMost(target.reps.max)
        return ProgressionDecision.AddReps(to)
    }
    return ProgressionDecision.Hold
}

/**
 * Fires by week [ProgrammingConstants.DELOAD_LATEST_WEEK] with no signals.
 * From week [ProgrammingConstants.DELOAD_EARLIEST_WEEK], any observed poor
 * signal also fires. Null signals are treated as unobserved, not as "fine".
 *
 * A deload cuts volume by [ProgrammingConstants.DELOAD_CUT_PERCENT_MIN]–
 * [ProgrammingConstants.DELOAD_CUT_PERCENT_MAX] percent, not intensity to zero.
 */
fun deloadDue(weeksAccumulated: Int, signals: RecoverySignals): Boolean {
    if (weeksAccumulated >= ProgrammingConstants.DELOAD_LATEST_WEEK) return true
    if (weeksAccumulated >= ProgrammingConstants.DELOAD_EARLIEST_WEEK && signals.poorObservable) {
        return true
    }
    return false
}

fun deloadCutPercent(): IntRange2 = range(
    ProgrammingConstants.DELOAD_CUT_PERCENT_MIN,
    ProgrammingConstants.DELOAD_CUT_PERCENT_MAX,
    ProgrammingProvenance.GUIDE_43,
)

fun cardioPlacement(
    strengthDay: SessionSummary,
    @Suppress("UNUSED_PARAMETER") goal: Goal,
): CardioAdvice {
    val heavyLower = strengthDay.patternIds.any { isHeavyLower(it) }
    return if (heavyLower) {
        CardioAdvice(
            avoidHardIntervals = true,
            message = "Skip the hard intervals on a heavy lower-body day. " +
                "Easy conversational cardio is fine; stack the sprints on another day.",
            provenance = ProgrammingProvenance.GUIDE_43,
        )
    } else {
        CardioAdvice(
            avoidHardIntervals = false,
            message = "Easy cardio is fine today. Keep hard intervals away from " +
                "a heavy squat or hinge day.",
            provenance = ProgrammingProvenance.GUIDE_43,
        )
    }
}

/**
 * Fractional set credit for one logged hard set toward one muscle.
 * >= 0.75 → 1.0; 0.30–0.75 → 0.5; below 0.30 → 0.
 */
fun setCredit(contribution: Double): Double = when {
    contribution >= ProgrammingConstants.FRACTIONAL_SET_PRIMARY_MIN -> 1.0
    contribution >= ProgrammingConstants.FRACTIONAL_SET_INDIRECT_MIN ->
        ProgrammingConstants.FRACTIONAL_SET_INDIRECT_WEIGHT
    else -> 0.0
}

internal fun landmark(min: Int, max: Int, provenance: Provenance): Landmark =
    Landmark(min, max, provenance)

internal fun range(min: Int, max: Int, provenance: Provenance): IntRange2 =
    IntRange2(min, max, provenance)

internal fun catalogFieldProvenance(exercise: Exercise, field: String): Provenance {
    val fieldProv = exercise.provenance[field] ?: exercise.provenanceDefault
    return Provenance(
        confidence = fieldProv.confidence,
        basis = fieldProv.basis,
        source = "catalog.${exercise.slug}.$field wins over goal default",
        reviewed = fieldProv.reviewed,
    )
}

fun assertCompleteProvenance(p: Provenance) {
    require(p.basis.isNotBlank()) { "Provenance.basis must not be blank" }
    require(p.source.isNotBlank()) { "Provenance.source must not be blank" }
    require(p.confidence.isNotBlank()) { "Provenance.confidence must not be blank" }
    require(p.reviewed.isNotBlank()) { "Provenance.reviewed must not be blank" }
}
