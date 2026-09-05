package com.gymbuddy.domain.pain

import com.gymbuddy.domain.intake.PainCharacter
import com.gymbuddy.domain.intake.RedFlag

private val TRAVEL_RED = setOf(TravelAnswer.TRAVELS, TravelAnswer.PINS, TravelAnswer.WEAKNESS)

private val ASK_ORDER = listOf(
    PainQuestionId.SPOT,
    PainQuestionId.ONSET,
    PainQuestionId.SIDES,
    PainQuestionId.CHARACTER,
    PainQuestionId.TRAVEL,
    PainQuestionId.AT_REST,
)

fun nextQuestion(interview: PainInterview): PainQuestionId? {
    if (interview.urgent != null) return null
    val loc = classifyLocation(interview.where)
    if (loc == PainLocationKind.CHEST) return null
    if (loc == PainLocationKind.HEAD && interview.onset == OnsetAnswer.SUDDEN) return null
    if (loc == PainLocationKind.HEAD && interview.onset == null) return PainQuestionId.ONSET
    ASK_ORDER.forEach { q ->
        if (!answered(interview, q)) return q
    }
    return null
}

fun triage(interview: PainInterview): PainOutcome {
    interview.urgent?.let { return PainOutcome.UrgentReferral(listOf(it)) }
    val loc = classifyLocation(interview.where)
    if (loc == PainLocationKind.CHEST) {
        return PainOutcome.UrgentReferral(listOf(RedFlag.CHEST_PAIN))
    }
    if (loc == PainLocationKind.HEAD && interview.onset == OnsetAnswer.SUDDEN) {
        return PainOutcome.UrgentReferral(listOf(RedFlag.SUDDEN_SEVERE_HEADACHE))
    }
    if (loc == PainLocationKind.HEAD && interview.onset == null) {
        return PainOutcome.NeedMore(PainQuestionId.ONSET)
    }

    if (interview.travel in TRAVEL_RED || interview.character == PainCharacter.RADIATING) {
        return PainOutcome.ReferBeforeContinuing(PainCopy.referOtherAreas(interview.where), mayTrainOtherAreas = true)
    }
    if (interview.nightWakes == true) {
        return PainOutcome.ReferBeforeContinuing(PainCopy.referNight(), mayTrainOtherAreas = true)
    }

    val stopReady = interview.sides == SidesAnswer.ONE &&
        interview.onset == OnsetAnswer.SUDDEN &&
        interview.character == PainCharacter.SHARP
    if (stopReady) {
        if (interview.travel == null) return PainOutcome.NeedMore(PainQuestionId.TRAVEL)
        if (interview.travel == TravelAnswer.NO) {
            val slug = interview.currentSlug ?: "this-movement"
            val name = interview.currentName ?: "this one"
            return PainOutcome.StopThisMovement(slug, PainCopy.stopMovement(name, null))
        }
    }

    if (sorenessCluster(interview)) {
        if (interview.travel == null) return PainOutcome.NeedMore(PainQuestionId.TRAVEL)
        if (interview.travel == TravelAnswer.NO && allSixAnswered(interview)) {
            return when (classifyLocation(interview.where)) {
                PainLocationKind.MUSCLE, PainLocationKind.JOINT, PainLocationKind.SPINE ->
                    PainOutcome.LikelyTrainingSoreness(PainCopy.soreness())
                PainLocationKind.CHEST ->
                    PainOutcome.UrgentReferral(listOf(RedFlag.CHEST_PAIN))
                PainLocationKind.HEAD, PainLocationKind.UNKNOWN ->
                    PainOutcome.ReferBeforeContinuing(PainCopy.referOnly(), mayTrainOtherAreas = true)
            }
        }
    }

    nextQuestion(interview)?.let { return PainOutcome.NeedMore(it) }

    // All asked, not soreness, not the sharp-sudden-one cluster.
    return PainOutcome.ReferBeforeContinuing(PainCopy.referOnly(), mayTrainOtherAreas = true)
}

internal fun sorenessCluster(interview: PainInterview): Boolean =
    interview.sides == SidesAnswer.BOTH &&
        interview.onset == OnsetAnswer.BUILT_UP &&
        (interview.character == PainCharacter.DULL || interview.character == PainCharacter.ACHE)

internal fun allSixAnswered(interview: PainInterview): Boolean =
    ASK_ORDER.all { answered(interview, it) }

private fun answered(interview: PainInterview, q: PainQuestionId): Boolean = when (q) {
    PainQuestionId.SPOT -> interview.spot != null
    PainQuestionId.ONSET -> interview.onset != null
    PainQuestionId.SIDES -> interview.sides != null
    PainQuestionId.CHARACTER -> interview.character != null
    PainQuestionId.TRAVEL -> interview.travel != null
    PainQuestionId.AT_REST -> interview.atRest != null
}
