package com.gymbuddy.domain.intake

import com.gymbuddy.domain.catalog.Provenance

object IntakeConstants {
    const val REVIEWED = "2026-09-01"
    const val DAYS_PER_WEEK_MIN = 1
    const val DAYS_PER_WEEK_MAX = 7
    const val CLEARANCE_DATE_PATTERN = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$"
}

internal object IntakeProvenance {
    val SAFETY = Provenance(
        confidence = "high",
        basis = "judgement",
        source = "SAFETY REQUIREMENT — conservative screen; unknown is not no; app does not diagnose",
        reviewed = IntakeConstants.REVIEWED,
    )
    val PAIN_MOVEMENT = Provenance(
        confidence = "high",
        basis = "judgement",
        source = "SAFETY REQUIREMENT — user-reported sharp or radiating pain on this movement. Stop and modify; do not diagnose.",
        reviewed = IntakeConstants.REVIEWED,
    )
    val CLEARANCE = Provenance(
        confidence = "high",
        basis = "judgement",
        source = "SAFETY REQUIREMENT — clearance is the user's report with a date, never inferred",
        reviewed = IntakeConstants.REVIEWED,
    )
}
