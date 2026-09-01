package com.gymbuddy.domain.programming

import com.gymbuddy.domain.catalog.Provenance

/**
 * Named numbers for the programming rules. Every `const val` here must have a
 * row in `docs/living-spec/DECISIONS_AND_THRESHOLDS.md`. A constant without a
 * row fails the build.
 */
object ProgrammingConstants {
    const val REVIEWED = "2026-09-01"

    const val HYPERTROPHY_INTERMEDIATE_HARD_SETS_MIN = 10
    const val HYPERTROPHY_INTERMEDIATE_HARD_SETS_MAX = 20
    const val HYPERTROPHY_BEGINNER_HARD_SETS_MIN = 6
    const val HYPERTROPHY_BEGINNER_HARD_SETS_MAX = 12
    const val STRENGTH_BEGINNER_HARD_SETS_MIN = 4
    const val STRENGTH_BEGINNER_HARD_SETS_MAX = 8
    const val STRENGTH_HARD_SETS_MIN = 6
    const val STRENGTH_HARD_SETS_MAX = 12
    const val ENDURANCE_HARD_SETS_MIN = 8
    const val ENDURANCE_HARD_SETS_MAX = 16
    const val GENERAL_FITNESS_HARD_SETS_MIN = 8
    const val GENERAL_FITNESS_HARD_SETS_MAX = 16

    const val MOST_WORK_RIR_MIN = 2
    const val MOST_WORK_RIR_MAX = 4
    const val BEGINNER_RIR_MIN = 3
    const val BEGINNER_RIR_MAX = 5

    const val STRENGTH_REPS_MIN = 3
    const val STRENGTH_REPS_MAX = 6
    const val HYPERTROPHY_REPS_MIN = 6
    const val HYPERTROPHY_REPS_MAX = 12
    const val ENDURANCE_REPS_MIN = 12
    const val ENDURANCE_REPS_MAX = 20
    const val FAT_LOSS_REPS_MIN = 8
    const val FAT_LOSS_REPS_MAX = 15
    const val GENERAL_FITNESS_REPS_MIN = 8
    const val GENERAL_FITNESS_REPS_MAX = 15

    const val STRENGTH_REST_SEC_MIN = 180
    const val STRENGTH_REST_SEC_MAX = 300
    const val HYPERTROPHY_REST_SEC_MIN = 60
    const val HYPERTROPHY_REST_SEC_MAX = 180
    const val ENDURANCE_REST_SEC_MIN = 30
    const val ENDURANCE_REST_SEC_MAX = 90
    const val STRENGTH_ISOLATION_REST_SEC_MIN = 60
    const val STRENGTH_ISOLATION_REST_SEC_MAX = 180
    const val FAT_LOSS_REST_SEC_MIN = 30
    const val FAT_LOSS_REST_SEC_MAX = 90
    const val GENERAL_FITNESS_REST_SEC_MIN = 60
    const val GENERAL_FITNESS_REST_SEC_MAX = 180

    const val WEEKLY_FREQUENCY_MIN = 2
    const val WEEKLY_FREQUENCY_MAX = 3

    const val DELOAD_EARLIEST_WEEK = 4
    const val DELOAD_LATEST_WEEK = 8
    const val DELOAD_CUT_PERCENT_MIN = 40
    const val DELOAD_CUT_PERCENT_MAX = 60

    const val ORDER_SKILL_HIGH = 300
    const val ORDER_SKILL_MODERATE = 200
    const val ORDER_SKILL_LOW = 100
    const val ORDER_FATIGUE_HIGH = 30
    const val ORDER_FATIGUE_MODERATE = 20
    const val ORDER_FATIGUE_LOW = 10
    const val ORDER_COMPOUND_BONUS = 3
    const val ORDER_CORE_PENALTY = 50

    const val PRIMARY_MUSCLE_CONTRIBUTION_MIN = 0.5
}

internal object ProgrammingProvenance {
    val GUIDE_43 = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "Coach & AI Programming Guide §4.3",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val NASM_OPT_42 = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "NASM OPT model, Coach & AI Programming Guide §4.2",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val STRENGTH_REPS = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "NASM OPT §4.2 Maximal Strength 1–5; Guide §4.3 true 1RMs are rare. Taken as 3–6.",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val NSCA_ORDER = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "NSCA Essentials (exercise order); ACE exam outline",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val PROGRESSION = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "ACSM; NASM OPT; ACE — programming_rules Progression; Guide §4.1 one thing at a time",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val TECHNIQUE = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "NSCA; technique/safety review (programming_rules Technique degradation)",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val CONTRAINDICATION = Provenance(
        confidence = "high",
        basis = "consensus",
        source = "catalog contraindications; Guide §3 / §5 (regress and refer, do not diagnose)",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val HEURISTIC = Provenance(
        confidence = "low",
        basis = "judgement",
        source = "MVP HEURISTIC — research folder silent",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val CATALOG_WINS = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "catalog defaultRestSec / repWindow win over goal defaults (contract 29)",
        reviewed = ProgrammingConstants.REVIEWED,
    )
}
