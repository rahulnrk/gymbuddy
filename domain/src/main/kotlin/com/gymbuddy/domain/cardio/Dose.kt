package com.gymbuddy.domain.cardio

import com.gymbuddy.domain.catalog.Provenance
import com.gymbuddy.domain.plan.PlannedSession
import com.gymbuddy.domain.programming.Goal
import com.gymbuddy.domain.programming.IntRange2
import com.gymbuddy.domain.programming.TrainingAge

fun weeklyCardioTarget(
    @Suppress("UNUSED_PARAMETER") goal: Goal,
    @Suppress("UNUSED_PARAMETER") trainingAge: TrainingAge,
): IntRange2 = IntRange2(
    CardioConstants.WEEKLY_MODERATE_MIN,
    CardioConstants.WEEKLY_MODERATE_MAX,
    ACSM_WEEKLY,
)

/**
 * Minutes that count toward ACSM 150–300 moderate-equivalent.
 *
 * Easy work (RPE 2–4, full sentences / easy conversation) is light activity.
 * It is not ACSM moderate ("talk but not sing") and it is not vigorous.
 * Credit is [CardioConstants.EASY_CREDIT] — zero.
 *
 * Tempo counts 1:1 as moderate. Hard work counts as vigorous, converted
 * with [CardioConstants.VIGOROUS_TO_MODERATE] (75 vigorous ≡ 150 moderate).
 */
fun acsmModerateEquivalentMinutes(p: CardioPrescription): Double = when (p.protocol.intensity) {
    ProtocolIntensity.EASY -> CardioConstants.EASY_CREDIT.toDouble()
    ProtocolIntensity.MODERATE -> p.durationMin.toDouble()
    ProtocolIntensity.HARD -> p.durationMin.toDouble() * CardioConstants.VIGOROUS_TO_MODERATE
}

fun weekAcsmMinutes(week: List<PlannedSession>): Double =
    week.mapNotNull { it.cardio }.sumOf { acsmModerateEquivalentMinutes(it) }

private val ACSM_WEEKLY = Provenance(
    confidence = "moderate",
    basis = "consensus",
    source = "PUBLISHED GUIDELINE — ACSM: 150–300 minutes moderate, or 75–150 vigorous, per week",
    reviewed = CardioConstants.REVIEWED,
)


