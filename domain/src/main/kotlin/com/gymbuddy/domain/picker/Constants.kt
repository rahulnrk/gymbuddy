package com.gymbuddy.domain.picker

/**
 * Scoring weights and per-slot set counts. Every `const val` has a row in
 * DECISIONS_AND_THRESHOLDS.md. All are MVP HEURISTIC.
 */
object PickerConstants {
    const val WEIGHT_GOAL_FIT = 2.0
    const val WEIGHT_MUSCLE_COVERAGE = 2.0
    const val WEIGHT_SKILL_MISMATCH = 1.5
    const val WEIGHT_FRESHNESS = 3.0
    const val WEIGHT_VARIETY = 1.5
    const val WEIGHT_PREFERENCE = 0.8
    const val WEIGHT_ROLE_FIT = 1.0

    const val SESSION_SETS_PRIMARY_BEGINNER = 2
    const val SESSION_SETS_PRIMARY = 3
    const val SESSION_SETS_SECONDARY = 3
    const val SESSION_SETS_ACCESSORY = 2
    const val SESSION_SETS_ISOLATION = 2
    const val SESSION_SETS_CORE = 2
}
