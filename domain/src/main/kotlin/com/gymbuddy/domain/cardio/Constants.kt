package com.gymbuddy.domain.cardio

/**
 * Cardio numbers. Every `const val` has a row in DECISIONS_AND_THRESHOLDS.md.
 */
object CardioConstants {
    const val REVIEWED = "2026-09-02"

    const val WEEKLY_MODERATE_MIN = 150
    const val WEEKLY_MODERATE_MAX = 300
    const val WEEKLY_VIGOROUS_MIN = 75
    const val WEEKLY_VIGOROUS_MAX = 150

    /** Easy (RPE 2–4, full sentences) does not count toward ACSM moderate or vigorous. */
    const val EASY_CREDIT = 0

    /** ACSM: 75 minutes vigorous ≡ 150 minutes moderate. */
    const val VIGOROUS_TO_MODERATE = 2

    const val DURATION_STEP_MIN = 5
    const val FREQUENCY_MIN = 2
    const val FREQUENCY_MAX = 5
}
