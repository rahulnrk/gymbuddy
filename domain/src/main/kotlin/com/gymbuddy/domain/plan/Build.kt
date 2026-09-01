package com.gymbuddy.domain.plan

import com.gymbuddy.domain.adapt.Adjustment
import com.gymbuddy.domain.adapt.Readiness
import com.gymbuddy.domain.adapt.adjustForToday
import com.gymbuddy.domain.adapt.spoken
import com.gymbuddy.domain.catalog.Catalog
import com.gymbuddy.domain.picker.PickContext
import com.gymbuddy.domain.picker.ScoredExercise
import com.gymbuddy.domain.picker.Slot
import com.gymbuddy.domain.picker.SlotRole
import com.gymbuddy.domain.picker.dose
import com.gymbuddy.domain.picker.rank
import com.gymbuddy.domain.programming.PlannedExercise
import com.gymbuddy.domain.programming.validateWeek
import com.gymbuddy.domain.warmup.MobilityHistory
import com.gymbuddy.domain.warmup.StretchLibrary
import com.gymbuddy.domain.warmup.WarmupLibrary
import com.gymbuddy.domain.warmup.planWarmup
import com.gymbuddy.domain.warmup.suggestStretches
import com.gymbuddy.domain.programming.PlannedSession as ProgrammingSession

/**
 * Assemble a week from a template. Never writes events. Never mutates [ctx].
 * [prescribedWeightKg] on every entry is null.
 */
fun buildWeek(
    template: Template,
    ctx: PickContext,
    catalog: Catalog,
    readiness: Readiness? = null,
    warmups: WarmupLibrary = WarmupLibrary(emptyList()),
    stretches: StretchLibrary = StretchLibrary(emptyList()),
    history: MobilityHistory = MobilityHistory(),
): PlanResult {
    val typical = ctx.user.typicalDurationMin ?: PlanConstants.DEFAULT_DURATION_MIN
    val days = mutableListOf<PlannedSession>()
    var recent = ctx.recentSlugs
    var mobility = history
    val droppedUnlocks = mutableListOf<String>()

    for (day in template.days) {
        val notes = mutableListOf<String>()
        val filled = mutableListOf<PlannedEntry>()
        var dayCtx = ctx.copy(recentSlugs = recent)
        for (tSlot in day.slots) {
            val slot = tSlot.asSlot()
            val already = filled.flatMap { listOf(it.slot.pattern, it.exercise.pattern) }.toSet()
            val picked = pickForSlot(slot, catalog, dayCtx, already)
            if (picked == null) {
                notes += PlanCopy.droppedSlot(PlanCopy.jobForPattern(tSlot.pattern))
                unlockNote(slot.pattern, catalog, ctx, dayCtx)?.let {
                    notes += it
                    droppedUnlocks += it
                }
                continue
            }
            val (top, fillSlot) = picked
            if (fillSlot.pattern != slot.pattern) {
                notes += PlanCopy.usedSibling(
                    PlanCopy.jobForPattern(fillSlot.pattern),
                    PlanCopy.jobForPattern(slot.pattern),
                )
            }
            val prescription = dose(top.exercise, fillSlot, dayCtx)
            filled += PlannedEntry(
                exercise = top.exercise,
                slot = fillSlot,
                sets = prescription.sets,
                repRange = prescription.repRange,
                restSec = prescription.restSec,
                effort = prescription.effort,
                why = top.reasons,
                priority = tSlot.priority,
                prescribedWeightKg = null,
            )
            dayCtx = dayCtx.copy(recentSlugs = listOf(top.exercise.slug) + dayCtx.recentSlugs)
        }
        if (filled.size < PlanConstants.MIN_EXERCISES_PER_DAY) {
            val reasons = mutableListOf(PlanCopy.cannotBuild())
            reasons += notes
            reasons += droppedUnlocks.distinct()
            return PlanResult.CannotBuild(reasons.distinct())
        }
        val draft = PlannedSession(day = day.name, entries = filled)
        val draftWarmup = planWarmup(draft, ctx, catalog, warmups, mobility)
        val clamped = clampToTime(filled, typical, notes, draftWarmup.estimatedMinutes)
        recent = (clamped.map { it.exercise.slug } + recent)
        val planned = PlannedSession(day = day.name, entries = clamped)
        val warmup = planWarmup(planned, ctx, catalog, warmups, mobility)
        val (cooldown, disclaimer) = suggestStretches(planned, ctx, catalog, stretches, mobility)
        days += planned.copy(
            notes = notes + warmup.notes,
            warmup = warmup,
            cooldown = cooldown,
            cooldownDisclaimer = disclaimer,
        )
        mobility = mobility.after(warmup, cooldown.size)
    }

    val programmingWeek = days.map { it.toProgramming() }
    val warnings = validateWeek(programmingWeek, ctx.user, catalog)
    val proposals = if (readiness != null && days.isNotEmpty()) {
        adjustForToday(days.first().toProgramming(), readiness, ctx, catalog)
            .filter { it !is Adjustment.AsPlanned }
    } else {
        emptyList()
    }
    val withProposals = if (proposals.isEmpty()) {
        days
    } else {
        days.mapIndexed { i, session ->
            if (i != 0) session
            else session.copy(notes = session.notes + proposals.mapNotNull { it.spoken() })
        }
    }
    return PlanResult.Built(withProposals, warnings, proposals)
}

fun buildWeek(
    templates: List<Template>,
    ctx: PickContext,
    catalog: Catalog,
    readiness: Readiness? = null,
    warmups: WarmupLibrary = WarmupLibrary(emptyList()),
    stretches: StretchLibrary = StretchLibrary(emptyList()),
    history: MobilityHistory = MobilityHistory(),
): PlanResult {
    val choice = chooseTemplate(templates, ctx.user)
        ?: return PlanResult.CannotBuild(listOf(PlanCopy.cannotBuild()))
    return when (val built = buildWeek(choice.template, ctx, catalog, readiness, warmups, stretches, history)) {
        is PlanResult.CannotBuild -> built
        is PlanResult.Built -> built.copy(
            week = built.week.mapIndexed { i, session ->
                if (i != 0) session
                else session.copy(notes = choice.notes + session.notes)
            },
        )
    }
}

internal fun clampToTime(
    entries: List<PlannedEntry>,
    typicalMin: Int,
    notes: MutableList<String>,
    warmupMin: Int = 0,
): List<PlannedEntry> {
    val hi = typicalMin + PlanConstants.DURATION_TOLERANCE_MIN
    var current = entries
    while (estimateMinutes(current, warmupMin) > hi) {
        val cut = current
            .filter { it.slot.role != SlotRole.PRIMARY }
            .maxWithOrNull(compareBy<PlannedEntry> { it.priority }.thenBy { it.exercise.slug })
            ?: break
        current = current.filter { it !== cut }
        notes += PlanCopy.cutForTime(cut.exercise.name, typicalMin)
    }
    while (estimateMinutes(current, warmupMin) > hi) {
        val reducible = current
            .filter { it.sets > PlanConstants.MIN_SETS }
            .sortedWith(
                compareBy<PlannedEntry> { if (it.slot.role == SlotRole.PRIMARY) 0 else 1 }
                    .thenByDescending { it.priority }
                    .thenBy { it.exercise.slug },
            )
            .lastOrNull()
            ?: break
        current = current.map {
            if (it === reducible) it.copy(sets = it.sets - 1) else it
        }
        notes += PlanCopy.reducedSets(reducible.exercise.name, reducible.sets - 1, typicalMin)
    }
    val estimated = estimateMinutes(current, warmupMin)
    if (estimated > hi) {
        notes += PlanCopy.stillOver(estimated)
    }
    return current
}

internal fun PlannedSession.toProgramming(): ProgrammingSession =
    ProgrammingSession(
        exercises = entries.map { entry ->
            PlannedExercise(
                exercise = entry.exercise,
                hardSets = entry.sets,
                prescribedRepMin = entry.repRange.min,
                prescribedRepMax = entry.repRange.max,
                prescribedRestSec = entry.restSec.min,
                slot = entry.slot,
            )
        },
    )

/**
 * Rank the written pattern. If it is empty, try each listed sibling once.
 * Skip a sibling already filled that day. Do not chain through a sibling's
 * own siblings.
 */
internal fun pickForSlot(
    slot: Slot,
    catalog: Catalog,
    dayCtx: PickContext,
    alreadyFilled: Set<String>,
): Pair<ScoredExercise, Slot>? {
    rank(slot, catalog, dayCtx).firstOrNull()?.let { return it to slot }
    for (sib in PatternSiblings.of(slot.pattern)) {
        if (sib in alreadyFilled) continue
        val sibSlot = Slot(sib, slot.role, slot.targetMuscles)
        rank(sibSlot, catalog, dayCtx).firstOrNull()?.let { return it to sibSlot }
    }
    return null
}

private fun unlockNote(
    pattern: String,
    catalog: Catalog,
    original: PickContext,
    rankedCtx: PickContext,
): String? {
    val full = rankedCtx.copy(equipment = catalog.equipment.keys)
    val top = rank(com.gymbuddy.domain.picker.Slot(pattern, SlotRole.PRIMARY), catalog, full).firstOrNull()
        ?: return null
    val missing = missingTokens(top.exercise, original.equipment)
    if (missing.isEmpty()) return null
    return PlanCopy.unlockWith(top.exercise, missing)
}

fun formatWeek(result: PlanResult): String = when (result) {
    is PlanResult.CannotBuild ->
        (listOf("CANNOT BUILD") + result.reasons).joinToString("\n")
    is PlanResult.Built -> buildString {
        result.week.forEach { session ->
            appendLine(session.day)
            session.warmup?.let { wu ->
                wu.pulse?.let { appendLine("  warmup: ${it.drill.name}") }
                wu.drills.forEach { appendLine("  warmup: ${it.drill.name}") }
                wu.rampSets.forEach { appendLine("  ramp: ${it.reps} reps, ${it.effortDescription}") }
            }
            session.entries.forEach { e ->
                append("  ")
                append(e.exercise.name)
                append(" — ")
                append(e.sets)
                append(" × ")
                append(e.repRange.min)
                append("–")
                append(e.repRange.max)
                append(" reps, ")
                append(e.restSec.min)
                append(" seconds rest")
                if (e.prescribedWeightKg != null) append(", ${e.prescribedWeightKg} kg")
                else append(", load unassigned")
                appendLine()
            }
            session.cooldownDisclaimer?.let { appendLine("  cooldown: $it") }
            session.cooldown.forEach { appendLine("  cooldown: ${it.stretch.name}") }
            session.notes.forEach { appendLine("  note: $it") }
            appendLine("  about ${estimateMinutes(session)} minutes")
            appendLine()
        }
        if (result.warnings.isNotEmpty()) {
            appendLine("warnings:")
            result.warnings.forEach { appendLine("  ${it.severity} ${it.message}") }
        }
        if (result.proposals.isNotEmpty()) {
            appendLine("proposals (not applied):")
            result.proposals.forEach { p -> p.spoken()?.let { appendLine("  $it") } }
        }
    }
}
