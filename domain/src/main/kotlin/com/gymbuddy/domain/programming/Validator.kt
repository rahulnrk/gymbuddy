package com.gymbuddy.domain.programming

import com.gymbuddy.domain.catalog.Catalog
import com.gymbuddy.domain.catalog.Exercise

/**
 * Reports rule violations on a proposed session or week. Never mutates the
 * plan. BLOCK is reserved for safety (contraindicated exercise present).
 */
fun validateSession(
    session: PlannedSession,
    user: UserContext,
    catalog: Catalog,
): List<Violation> {
    val out = mutableListOf<Violation>()
    out += validateOrder(session.exercises.map { it.exercise })
    out += validateContraindications(session, user, catalog)
    out += validatePrescriptions(session, user)
    return out
}

fun validateWeek(
    week: List<PlannedSession>,
    user: UserContext,
    catalog: Catalog,
): List<Violation> {
    val out = mutableListOf<Violation>()
    week.forEach { out += validateSession(it, user, catalog) }
    out += validateWeeklyVolume(week, user, catalog)
    out += validatePatternBalance(week)
    out += validateFrequency(week, user, catalog)
    return out
}

/**
 * Hard sets per muscle this week. A unilateral logged set counts as one set
 * per side — it is not halved. A set counts toward a muscle when that
 * muscle's contribution is at least [ProgrammingConstants.PRIMARY_MUSCLE_CONTRIBUTION_MIN].
 */
fun hardSetsByMuscle(
    week: List<PlannedSession>,
    catalog: Catalog,
): Map<String, Int> {
    val tally = mutableMapOf<String, Int>()
    week.forEach { session ->
        session.exercises.forEach { planned ->
            musclesTrained(planned.exercise, catalog).forEach { muscleId ->
                tally[muscleId] = (tally[muscleId] ?: 0) + planned.hardSets
            }
        }
    }
    return tally
}

internal fun musclesTrained(exercise: Exercise, catalog: Catalog): List<String> {
    val contrib = catalog.musclesFor(exercise)
    return contrib.filter { it.value >= ProgrammingConstants.PRIMARY_MUSCLE_CONTRIBUTION_MIN }.keys.toList()
}

private fun validateContraindications(
    session: PlannedSession,
    user: UserContext,
    catalog: Catalog,
): List<Violation> {
    if (user.conditions.isEmpty()) return emptyList()
    val declared = user.conditions.toSet()
    return session.exercises.mapNotNull { planned ->
        val hit = planned.exercise.contraindications.firstOrNull { it in declared } ?: return@mapNotNull null
        val conditionName = catalog.conditions[hit]?.name ?: "that limitation"
        Violation(
            rule = "safety.contraindication",
            severity = Severity.BLOCK,
            message = "${planned.exercise.name} is a poor match with $conditionName. I'd swap it out.",
            provenance = ProgrammingProvenance.CONTRAINDICATION,
        )
    }
}

private fun validatePrescriptions(
    session: PlannedSession,
    user: UserContext,
): List<Violation> {
    val goalRestCompound = restSeconds(user.goal, ExerciseKind.COMPOUND)
    val goalRestIsolation = restSeconds(user.goal, ExerciseKind.ISOLATION)
    val goalReps = repRange(user.goal)
    val out = mutableListOf<Violation>()
    session.exercises.forEach { planned ->
        val ex = planned.exercise
        val catalogRest = ex.defaultRestSec
        val restGoal = if (ex.exerciseKind() == ExerciseKind.ISOLATION) {
            goalRestIsolation
        } else {
            goalRestCompound
        }
        val restSec = planned.prescribedRestSec
        if (restSec != null && restSec != catalogRest && !restGoal.contains(restSec)) {
            out += Violation(
                rule = "rest.out-of-range",
                severity = Severity.WARN,
                message = "The rest is $restSec seconds — outside the " +
                    "${restGoal.min} to ${restGoal.max} seconds this goal usually uses, " +
                    "and it isn't the catalog rest of $catalogRest seconds for ${ex.name}.",
                provenance = restGoal.provenance,
            )
        }
        val repMin = planned.prescribedRepMin
        val repMax = planned.prescribedRepMax
        if (repMin != null && repMax != null) {
            val catalogWindow = ex.repWindow
            val matchesCatalog = repMin == catalogWindow.min && repMax == catalogWindow.max
            val insideGoal = goalReps.contains(repMin) && goalReps.contains(repMax)
            if (!matchesCatalog && !insideGoal) {
                out += Violation(
                    rule = "reps.out-of-range",
                    severity = Severity.WARN,
                    message = "Those $repMin to $repMax reps sit outside the " +
                        "${goalReps.min} to ${goalReps.max} rep window this goal usually uses, " +
                        "and they're not the catalog window of ${catalogWindow.min} to " +
                        "${catalogWindow.max} reps for ${ex.name}.",
                    provenance = goalReps.provenance,
                )
            }
        }
    }
    return out
}

private fun validateWeeklyVolume(
    week: List<PlannedSession>,
    user: UserContext,
    catalog: Catalog,
): List<Violation> {
    val landmark = weeklyHardSets(user.goal, user.trainingAge)
    val tally = hardSetsByMuscle(week, catalog)
    return tally.mapNotNull { (muscleId, sets) ->
        val name = catalog.muscles[muscleId]?.name ?: muscleId.replace('_', ' ')
        when {
            sets > landmark.max -> Violation(
                rule = "volume.weekly-hard-sets.high",
                severity = Severity.WARN,
                message = "That's $sets hard sets for $name this week — more than most people " +
                    "recover from. I'd cut it to about ${landmark.max} hard sets.",
                provenance = landmark.basis,
            )
            sets < landmark.min -> Violation(
                rule = "volume.weekly-hard-sets.low",
                severity = Severity.INFO,
                message = "That's $sets hard sets for $name this week — on the light side of " +
                    "the ${landmark.min} to ${landmark.max} hard sets most people grow on. Fine if you're easing in.",
                provenance = landmark.basis,
            )
            else -> null
        }
    }
}

private fun validatePatternBalance(week: List<PlannedSession>): List<Violation> {
    val groups = week.flatMap { session ->
        session.exercises.map { patternGroup(it.exercise.pattern) }
    }.toSet()
    val hasPush = PatternGroup.PUSH in groups
    val hasPull = PatternGroup.PULL in groups
    if (hasPush && !hasPull) {
        return listOf(
            Violation(
                rule = "pattern.imbalance.push-only",
                severity = Severity.WARN,
                message = "This week is all pushing. I'd add a pull so the same joints " +
                    "aren't doing the same job every day.",
                provenance = ProgrammingProvenance.NSCA_ORDER,
            ),
        )
    }
    if (hasPull && !hasPush) {
        return listOf(
            Violation(
                rule = "pattern.imbalance.pull-only",
                severity = Severity.WARN,
                message = "This week is all pulling. I'd add a press so the front of the " +
                    "shoulder isn't left out.",
                provenance = ProgrammingProvenance.NSCA_ORDER,
            ),
        )
    }
    return emptyList()
}

private fun validateFrequency(
    week: List<PlannedSession>,
    user: UserContext,
    catalog: Catalog,
): List<Violation> {
    val freq = weeklyFrequency(user.goal, user.trainingAge)
    val daysByMuscle = mutableMapOf<String, MutableSet<Int>>()
    week.forEachIndexed { day, session ->
        session.exercises.forEach { planned ->
            musclesTrained(planned.exercise, catalog).forEach { muscleId ->
                daysByMuscle.getOrPut(muscleId) { mutableSetOf() }.add(day)
            }
        }
    }
    if (week.size < freq.min) {
        return listOf(
            Violation(
                rule = "frequency.pattern-low",
                severity = Severity.INFO,
                message = "This week has ${week.size} training day${if (week.size == 1) "" else "s"}. " +
                    "Hitting a muscle about ${freq.min} to ${freq.max} times a week usually " +
                    "works better than one long session.",
                provenance = freq.provenance,
            ),
        )
    }
    return daysByMuscle.mapNotNull { (muscleId, days) ->
        if (days.size >= freq.min) return@mapNotNull null
        val name = catalog.muscles[muscleId]?.name ?: muscleId.replace('_', ' ')
        Violation(
            rule = "frequency.pattern-low",
            severity = Severity.INFO,
            message = "$name only shows up ${days.size} time${if (days.size == 1) "" else "s"} this week. " +
                "Hitting a muscle about ${freq.min} to ${freq.max} times a week usually " +
                "works better than one long session.",
            provenance = freq.provenance,
        )
    }
}
