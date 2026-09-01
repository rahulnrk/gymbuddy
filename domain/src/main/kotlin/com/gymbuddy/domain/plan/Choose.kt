package com.gymbuddy.domain.plan

import com.gymbuddy.domain.programming.UserContext

/**
 * Pick a template by goal × days × training age. No match at the requested
 * days uses the nearest lower days-per-week and says so.
 */
fun chooseTemplate(templates: List<Template>, user: UserContext): TemplateChoice? {
    val days = user.daysPerWeek ?: PlanConstants.DEFAULT_DAYS_PER_WEEK
    val goal = user.goal.name
    val age = user.trainingAge.name
    val matching = templates.filter { goal in it.goals && age in it.trainingAges }
    val exact = matching.filter { it.daysPerWeek == days }.minByOrNull { it.id }
    if (exact != null) return TemplateChoice(exact)
    val lower = matching.filter { it.daysPerWeek < days }.maxByOrNull { it.daysPerWeek }
        ?: matching.filter { it.daysPerWeek > days }.minByOrNull { it.daysPerWeek }
    if (lower != null) {
        return TemplateChoice(
            lower,
            listOf(PlanCopy.nearerDays(days, lower.daysPerWeek, lower.name)),
        )
    }
    val anyGoal = templates.filter { goal in it.goals }
    val relaxed = anyGoal.filter { it.daysPerWeek <= days }.maxByOrNull { it.daysPerWeek }
        ?: anyGoal.minByOrNull { kotlin.math.abs(it.daysPerWeek - days) }
    return relaxed?.let {
        TemplateChoice(it, listOf(PlanCopy.nearerDays(days, it.daysPerWeek, it.name)))
    }
}
