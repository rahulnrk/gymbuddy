package com.gymbuddy.domain.cardio

import com.gymbuddy.domain.catalog.Provenance
import com.gymbuddy.domain.programming.Goal
import com.gymbuddy.domain.programming.IntRange2
import com.gymbuddy.domain.programming.TrainingAge

fun weeklyCardioTarget(goal: Goal, trainingAge: TrainingAge): IntRange2 {
    if (trainingAge == TrainingAge.BEGINNER) {
        return IntRange2(
            CardioConstants.WEEKLY_BEGINNER_MIN,
            CardioConstants.WEEKLY_BEGINNER_MAX,
            ACSM_BEGINNER,
        )
    }
    if (goal == Goal.STRENGTH || goal == Goal.HYPERTROPHY) {
        return IntRange2(
            CardioConstants.WEEKLY_STRENGTH_MIN,
            CardioConstants.WEEKLY_STRENGTH_MAX,
            ACSM_CONCURRENT,
        )
    }
    return IntRange2(
        CardioConstants.WEEKLY_MODERATE_MIN,
        CardioConstants.WEEKLY_MODERATE_MAX,
        ACSM_WEEKLY,
    )
}

private val ACSM_WEEKLY = Provenance(
    confidence = "moderate",
    basis = "consensus",
    source = "PUBLISHED GUIDELINE — ACSM: 150–300 minutes moderate, or 75–150 vigorous, per week",
    reviewed = CardioConstants.REVIEWED,
)

private val ACSM_BEGINNER = Provenance(
    confidence = "low",
    basis = "judgement",
    source = "GUIDELINE-DERIVED — ACSM adult floor is 150 minutes moderate. 75–150 is our beginner start.",
    reviewed = CardioConstants.REVIEWED,
)

private val ACSM_CONCURRENT = Provenance(
    confidence = "low",
    basis = "judgement",
    source = "GUIDELINE-DERIVED — concurrent training: keep cardio in the 75–150 band so lifting stays primary",
    reviewed = CardioConstants.REVIEWED,
)
