package com.gymbuddy.domain.pain

/**
 * Pain triage numbers. Intensity is recorded, never a route gate.
 * Every `const val` has a row in DECISIONS_AND_THRESHOLDS.md.
 */
object PainConstants {
    const val REVIEWED = "2026-09-02"

    /** Inclusive bounds for a spoken 0–10. Out of range is dropped from history, not used to route. */
    const val INTENSITY_MIN = 0
    const val INTENSITY_MAX = 10
}
