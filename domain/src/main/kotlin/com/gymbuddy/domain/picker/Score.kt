package com.gymbuddy.domain.picker

import com.gymbuddy.domain.catalog.Catalog
import com.gymbuddy.domain.catalog.Exercise
import com.gymbuddy.domain.programming.Goal
import com.gymbuddy.domain.programming.TrainingAge
import com.gymbuddy.domain.programming.setCredit

/**
 * Rank survivors for one slot. Same inputs, same order. Score ties break
 * toward lower consequenceOfFailure, then lower skillDemand, only when both
 * lifts share an implement class. Across classes, slug. Never returns a session.
 */
fun rank(slot: Slot, catalog: Catalog, ctx: PickContext): List<ScoredExercise> =
    rankSlot(slot, catalog, ctx).ranked

fun rankSlot(slot: Slot, catalog: Catalog, ctx: PickContext): RankedSlot {
    val set = candidates(slot, catalog, ctx)
    val ranked = set.survivors
        .map { score(it, slot, catalog, ctx, set.keptDespiteDislike) }
        .sortedWith(scoredComparator)
    return RankedSlot(ranked, slotReadiness(slot, catalog, ctx, set.survivors))
}

internal val scoredComparator = Comparator<ScoredExercise> { a, b ->
    val byScore = b.score.compareTo(a.score)
    if (byScore != 0) return@Comparator byScore
    if (implementClass(a.exercise) == implementClass(b.exercise)) {
        val byCof = demandRank(a.exercise.consequenceOfFailure)
            .compareTo(demandRank(b.exercise.consequenceOfFailure))
        if (byCof != 0) return@Comparator byCof
        val bySkill = demandRank(a.exercise.skillDemand)
            .compareTo(demandRank(b.exercise.skillDemand))
        if (bySkill != 0) return@Comparator bySkill
    }
    a.exercise.slug.compareTo(b.exercise.slug)
}

internal fun demandRank(level: String): Int = when (level) {
    "low" -> 0
    "moderate" -> 1
    "high" -> 2
    else -> 1
}

fun slotReadiness(
    slot: Slot,
    catalog: Catalog,
    ctx: PickContext,
    survivors: List<Exercise> = candidates(slot, catalog, ctx).survivors,
): SlotReadiness {
    val worstByExercise = survivors.map { worstPrimaryFatigue(it, catalog, ctx.fatigue) }
    val worstTarget = slot.targetMuscles.maxOfOrNull { ctx.fatigue[it] ?: 0.0 }
        ?: worstByExercise.maxOrNull()
        ?: 0.0
    val allCooked = survivors.isNotEmpty() &&
        worstByExercise.all { it >= PickerConstants.FATIGUE_COOKED }
    val muscleName = slot.targetMuscles.firstOrNull()
        ?.let { catalog.muscles[it]?.name?.lowercase() }
        ?: survivors.firstOrNull()?.let { ex ->
            catalog.musclesFor(ex).maxByOrNull { it.value }?.key
                ?.let { catalog.muscles[it]?.name?.lowercase() }
        }
        ?: "those muscles"
    val explanation = if (allCooked) {
        "Everything left for this loads your $muscleName, and they're still cooked."
    } else if (worstTarget >= PickerConstants.FATIGUE_MILD) {
        "Your $muscleName still carry some fatigue, which is normal training."
    } else {
        "This exercise looks fresh enough to do as planned."
    }
    return SlotReadiness(allCooked, worstTarget, explanation)
}

internal fun worstPrimaryFatigue(exercise: Exercise, catalog: Catalog, fatigue: Map<String, Double>): Double {
    val primaries = catalog.musclesFor(exercise).filter { setCredit(it.value) >= 1.0 }
    return primaries.maxOfOrNull { (id, _) -> fatigue[id] ?: 0.0 } ?: 0.0
}

internal fun score(
    exercise: Exercise,
    slot: Slot,
    catalog: Catalog,
    ctx: PickContext,
    keptDespiteDislike: Set<String>,
): ScoredExercise {
    val reasons = mutableListOf<Reason>()
    reasons += goalFit(exercise, ctx.user.goal)
    reasons += muscleCoverage(exercise, slot, catalog)
    reasons += skillMatch(exercise, ctx.user.trainingAge)
    reasons += freshness(exercise, catalog, ctx.fatigue)
    reasons += variety(exercise, ctx.recentSlugs)
    reasons += preference(exercise, ctx.acceptedSubstitutions)
    reasons += roleFit(exercise, slot)
    if (exercise.slug in keptDespiteDislike) {
        reasons += Reason(
            factor = "preference",
            delta = 0.0,
            explanation = "You asked to skip this, but it is the only lift that still fits this slot.",
        )
    }
    return ScoredExercise(
        exercise = exercise,
        score = reasons.sumOf { it.delta },
        reasons = reasons,
    )
}

private fun goalFit(exercise: Exercise, goal: Goal): Reason {
    val listed = exercise.bestFitGoals
    val matches = listed.isEmpty() || goal.name in listed
    val delta = if (matches) PickerConstants.WEIGHT_GOAL_FIT else PickerConstants.WEIGHT_GOAL_FIT * 0.25
    val explanation = if (matches) {
        "This is the kind of work that matches the goal you picked."
    } else {
        "This still works, but it is not the main kind of work for the goal you picked."
    }
    return Reason("goal fit", delta, explanation)
}

private fun muscleCoverage(exercise: Exercise, slot: Slot, catalog: Catalog): Reason {
    val contrib = catalog.musclesFor(exercise)
    val targets = slot.targetMuscles.ifEmpty {
        contrib.filter { setCredit(it.value) >= 1.0 }.keys
    }
    val covered = targets.sumOf { id -> setCredit(contrib[id] ?: 0.0) }
    val delta = PickerConstants.WEIGHT_MUSCLE_COVERAGE * covered
    val names = targets.map { catalog.muscles[it]?.name?.lowercase() ?: "that muscle" }
    val explanation = if (names.isEmpty()) {
        "This matches the movement you asked for."
    } else {
        "This trains ${names.joinToString(" and ")} for this slot."
    }
    return Reason("muscle coverage", delta, explanation)
}

private fun skillMatch(exercise: Exercise, age: TrainingAge): Reason {
    val high = exercise.skillDemand == "high"
    val delta = when {
        age == TrainingAge.BEGINNER && high -> -PickerConstants.WEIGHT_SKILL_MISMATCH
        age == TrainingAge.ADVANCED && high -> PickerConstants.WEIGHT_SKILL_MISMATCH * 0.3
        else -> 0.0
    }
    val explanation = when {
        age == TrainingAge.BEGINNER && high ->
            "This one takes more practice than I would start a beginner on, so I would keep it later in the list."
        age == TrainingAge.ADVANCED && high ->
            "This is a skilled lift, which fits someone who already owns the pattern."
        else ->
            "The skill demand of this lift is a fair match for where you are."
    }
    return Reason("skill match", delta, explanation)
}

private fun freshness(exercise: Exercise, catalog: Catalog, fatigue: Map<String, Double>): Reason {
    val contrib = catalog.musclesFor(exercise)
    val primaries = contrib.filter { setCredit(it.value) >= 1.0 }
    val worst = primaries.maxOfOrNull { (id, _) -> fatigue[id] ?: 0.0 } ?: 0.0
    val delta = -PickerConstants.WEIGHT_FRESHNESS * worst
    val tired = primaries.maxByOrNull { (id, _) -> fatigue[id] ?: 0.0 }
    val name = tired?.let { catalog.muscles[it.key]?.name?.lowercase() }
    val explanation = if (worst <= 0.0 || name == null) {
        "The muscles this trains are fresh enough to work."
    } else {
        "Fatigue is still sitting on your $name, so I would rather not make this the main work."
    }
    return Reason("freshness", delta, explanation)
}

private fun variety(exercise: Exercise, recentSlugs: List<String>): Reason {
    val index = recentSlugs.indexOf(exercise.slug)
    // Full penalty for any hit. Dividing by index made Monday's pick a
    // rounding error by Thursday, so Upper B copied Upper A.
    val delta = if (index < 0) 0.0 else -PickerConstants.WEIGHT_VARIETY
    val explanation = when (index) {
        -1 -> "You have not done this one lately."
        0 -> "You did this last time, so I would rather pick something else."
        else -> "You already did this one, so I would rather pick something else."
    }
    return Reason("variety", delta, explanation)
}

private fun preference(exercise: Exercise, accepted: Set<String>): Reason {
    val liked = exercise.slug in accepted
    val delta = if (liked) PickerConstants.WEIGHT_PREFERENCE else 0.0
    val explanation = if (liked) {
        "You have chosen this as a swap before, so I would keep offering it."
    } else {
        "No stored swap preference for this lift."
    }
    return Reason("preference", delta, explanation)
}

private fun roleFit(exercise: Exercise, slot: Slot): Reason {
    val compound = exercise.kind == "compound"
    val isolation = exercise.kind == "isolation"
    val core = exercise.pattern == "core"
    val delta = when (slot.role) {
        SlotRole.PRIMARY -> if (compound) PickerConstants.WEIGHT_ROLE_FIT else -PickerConstants.WEIGHT_ROLE_FIT * 0.5
        SlotRole.SECONDARY -> if (compound) PickerConstants.WEIGHT_ROLE_FIT * 0.5 else 0.0
        SlotRole.ACCESSORY -> if (isolation) PickerConstants.WEIGHT_ROLE_FIT * 0.5 else 0.0
        SlotRole.ISOLATION -> if (isolation) PickerConstants.WEIGHT_ROLE_FIT else -PickerConstants.WEIGHT_ROLE_FIT * 0.5
        SlotRole.CORE -> if (core) PickerConstants.WEIGHT_ROLE_FIT else 0.0
    }
    val explanation = when (slot.role) {
        SlotRole.PRIMARY ->
            if (compound) "This is a compound lift, which belongs in a main slot."
            else "This is isolation work, which is a weaker fit for a main slot."
        SlotRole.ISOLATION ->
            if (isolation) "This is isolation work, which is what this slot is for."
            else "This is a compound lift in an isolation slot."
        SlotRole.CORE ->
            if (core) "This is core work, which is what this slot is for."
            else "This is not a core drill."
        else -> "This is a fair fit for the role of this slot."
    }
    return Reason("role fit", delta, explanation)
}
