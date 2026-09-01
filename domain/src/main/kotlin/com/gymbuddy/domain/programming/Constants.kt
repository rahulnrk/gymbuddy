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
    const val HYPERTROPHY_BEGINNER_HARD_SETS_MIN = 4
    const val HYPERTROPHY_BEGINNER_HARD_SETS_MAX = 10
    const val STRENGTH_BEGINNER_HARD_SETS_MIN = 3
    const val STRENGTH_BEGINNER_HARD_SETS_MAX = 8
    const val STRENGTH_HARD_SETS_MIN = 4
    const val STRENGTH_HARD_SETS_MAX = 10
    const val ENDURANCE_HARD_SETS_MIN = 8
    const val ENDURANCE_HARD_SETS_MAX = 16
    const val GENERAL_FITNESS_HARD_SETS_MIN = 8
    const val GENERAL_FITNESS_HARD_SETS_MAX = 16

    const val MOST_WORK_RIR_MIN = 2
    const val MOST_WORK_RIR_MAX = 4
    const val RIR_TRUST_MAX = 3
    const val RIR_TRUST_REPS_MAX = 12

    const val STRENGTH_REPS_MIN = 4
    const val STRENGTH_REPS_MAX = 8
    const val HYPERTROPHY_REPS_MIN = 6
    const val HYPERTROPHY_REPS_MAX = 12
    const val ENDURANCE_REPS_MIN = 12
    const val ENDURANCE_REPS_MAX = 20
    const val GENERAL_FITNESS_REPS_MIN = 8
    const val GENERAL_FITNESS_REPS_MAX = 15

    const val STRENGTH_REST_SEC_MIN = 180
    const val STRENGTH_REST_SEC_MAX = 300
    const val STRENGTH_REST_SEC_BEGINNER_MIN = 60
    const val STRENGTH_REST_SEC_BEGINNER_MAX = 120
    const val HYPERTROPHY_REST_SEC_MIN = 90
    const val HYPERTROPHY_REST_SEC_MAX = 180
    const val ENDURANCE_REST_SEC_MIN = 30
    const val ENDURANCE_REST_SEC_MAX = 90
    const val STRENGTH_ISOLATION_REST_SEC_MIN = 120
    const val STRENGTH_ISOLATION_REST_SEC_MAX = 180
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

    const val FRACTIONAL_SET_PRIMARY_MIN = 0.75
    const val FRACTIONAL_SET_INDIRECT_MIN = 0.30
    const val FRACTIONAL_SET_INDIRECT_WEIGHT = 0.5
}

internal object ProgrammingProvenance {
    val GUIDE_43 = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "Coach & AI Programming Guide §4.3",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val ACSM_2026 = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "Currier et al. (2026). ACSM Position Stand: Resistance Training Prescription. Med Sci Sports Exerc.",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val ACSM_BAZ_SCHOENFELD = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "Currier et al. (2026) ACSM Position Stand; Baz-Valle et al. (2022) J Hum Kinet; Schoenfeld et al. (2017) J Sports Sci.",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val BEHM_PETERSON = Provenance(
        confidence = "low",
        basis = "consensus",
        source = "Behm et al. (2023) Sports Med; Peterson et al. (2005) J Strength Cond Res; Uğurlu et al. (2024) Medicine.",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val ACSM_RALSTON = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "Currier et al. (2026) ACSM Position Stand; Ralston et al. (2017) Sports Med.",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val RHEA_PETERSON = Provenance(
        confidence = "low",
        basis = "consensus",
        source = "Rhea et al. (2003) Med Sci Sports Exerc; Peterson et al. (2005) J Strength Cond Res.",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val ROTH_2022 = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "Roth et al. (2022). Resistance training volume and lean-mass preservation in a deficit. Scand J Med Sci Sports; Eur J Appl Physiol.",
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
        source = "Currier et al. (2026) ACSM Position Stand: strength at ≥80% 1RM (~8RM). Taken as 4–8.",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val CARLSON_2022 = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "Carlson et al. (2022). Training load during dietary intervention and fat loss. Res Q Exerc Sport.",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val SENNA_2016 = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "Senna et al. (2016). Interset rest: multi-joint 3–5 min, single-joint 2 min sufficient. J Strength Cond Res.",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val GRGIC_2017 = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "Grgic et al. (2017). Rest interval duration and strength: 60–120 s sufficient for untrained. Sports Med.",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val SINGER_2024 = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "Singer et al. (2024). Inter-set rest and hypertrophy: floor ~90 s. Front Sports Act Living.",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val PELLAND_2025 = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "Pelland et al. (2025) Sports Med; Hamaïde et al. (2025); Joachim et al. (2025). Fractional set counting.",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val ZOURDOS_2019 = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "Zourdos et al. (2019). Proximity to failure and reps per set drive RIR accuracy. J Strength Cond Res.",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val NSCA_ORDER = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "NSCA Essentials (exercise order); ACE exam outline; Currier et al. (2026) ACSM Position Stand (strength work first).",
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
        source = "MVP HEURISTIC — named papers silent",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val FRACTIONAL_BAND = Provenance(
        confidence = "low",
        basis = "judgement",
        source = "MVP HEURISTIC — Pelland et al. (2025) classify direct/indirect by judgement, not a numeric cutoff",
        reviewed = ProgrammingConstants.REVIEWED,
    )
    val CATALOG_WINS = Provenance(
        confidence = "moderate",
        basis = "consensus",
        source = "catalog defaultRestSec / repWindow win over goal defaults (contract 29)",
        reviewed = ProgrammingConstants.REVIEWED,
    )
}
