package com.gymbuddy.domain.intake

import com.gymbuddy.domain.catalog.Catalog
import com.gymbuddy.domain.programming.UserContext

/**
 * Completeness gate. Refer outranks everything. NeedMore returns exactly one
 * next question. Ready is the programming UserContext — not a workout.
 */
fun evaluate(intake: Intake, vocab: EquipmentVocab): IntakeResult {
    val screened = screen(intake.healthScreen)
    if (screened is ScreenOutcome.ReferBeforeTraining) {
        return IntakeResult.Refer(screened)
    }

    val missing = missingRequirements(intake)
    if (missing.isNotEmpty()) {
        val next = ASK_ORDER.first { id -> missing.any { it.question == id } }
        return IntakeResult.NeedMore(missing, next)
    }

    val screenReady = intake.healthScreen!!
    val equipmentIds = intake.equipment!!.resolve(vocab).toList()
    val knownConditions = screenReady.conditions.mapNotNull { d ->
        (d as? DeclaredCondition.Known)?.id
    }
    val sharpSlugs = if (screenReady.hasPain == Answer.YES) {
        screenReady.painReports
            .filter { it.character == PainCharacter.SHARP || it.character == PainCharacter.RADIATING }
            .flatMap { it.movements }
            .toSet()
    } else {
        emptySet()
    }
    return IntakeResult.Ready(
        UserContext(
            goal = intake.goal!!,
            trainingAge = intake.trainingAge!!,
            conditions = knownConditions,
            equipment = equipmentIds,
            trainsWithPartner = intake.trainsWithPartner,
            sharpPainSlugs = sharpSlugs,
        ),
    )
}

fun missingRequirements(intake: Intake): List<Requirement> {
    val out = mutableListOf<Requirement>()
    if (intake.goal == null) out += req(QuestionId.GOAL)
    if (intake.trainingAge == null) out += req(QuestionId.TRAINING_AGE)
    if (intake.daysPerWeek == null ||
        intake.daysPerWeek !in IntakeConstants.DAYS_PER_WEEK_MIN..IntakeConstants.DAYS_PER_WEEK_MAX
    ) {
        out += req(QuestionId.DAYS_PER_WEEK)
    }
    if (intake.typicalSessionMinutes == null || intake.typicalSessionMinutes <= 0) {
        out += req(QuestionId.SESSION_MINUTES)
    }
    if (intake.equipment == null) out += req(QuestionId.EQUIPMENT_PRESET)
    unansweredScreenQuestions(intake.healthScreen).forEach { q ->
        out += Requirement(q, Tier.SAFETY)
    }
    return out.distinctBy { it.question }.sortedBy { ASK_ORDER.indexOf(it.question) }
}

fun equipmentCoverage(catalog: Catalog, tokens: Collection<String>): EquipmentCoverage {
    val have = tokens.toSet()
    return EquipmentCoverage(
        tokenCount = have.size,
        exerciseCount = catalog.trainableWith(have).size,
    )
}

private fun req(id: QuestionId) = Requirement(id, id.tierForAsk())
