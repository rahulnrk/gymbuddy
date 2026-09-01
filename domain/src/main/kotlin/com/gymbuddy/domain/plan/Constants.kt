package com.gymbuddy.domain.plan

object PlanConstants {
    /** Seconds of lifting plus unracking, loading and getting set for one working set. */
    const val SECONDS_PER_WORKING_SET = 90

    /** Contract 16: estimated duration must land in typical ± this many minutes. */
    const val DURATION_TOLERANCE_MIN = 15

    /** Used when intake did not record a session length. */
    const val DEFAULT_DURATION_MIN = 60

    /** Used when intake did not record days per week. */
    const val DEFAULT_DAYS_PER_WEEK = 3

    /** Below this many remaining exercises, a day is not a useful session. */
    const val MIN_EXERCISES_PER_DAY = 2

    const val MIN_SETS = 1
}
