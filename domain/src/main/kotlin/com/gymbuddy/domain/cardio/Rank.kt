package com.gymbuddy.domain.cardio

import com.gymbuddy.domain.picker.PickContext
import com.gymbuddy.domain.picker.Reason
import com.gymbuddy.domain.programming.Goal

fun rank(
    goal: Goal,
    ctx: PickContext,
    minutes: Int,
    library: CardioLibrary,
): List<CardioPrescription> {
    val filtered = filterPairs(library, ctx)
    return filtered.kept
        .map { (m, p) -> prescribe(m, p, goal, minutes, ctx) }
        .sortedWith(
            compareByDescending<CardioPrescription> { score(it, goal, minutes) }
                .thenBy { it.modality.slug }
                .thenBy { it.protocol.slug },
        )
}

fun prescribe(
    m: CardioModality,
    p: CardioProtocol,
    goal: Goal,
    minutes: Int,
    ctx: PickContext,
): CardioPrescription {
    val duration = durationFor(p, minutes)
    return CardioPrescription(
        modality = m,
        protocol = p,
        durationMin = duration,
        effort = p.effortGuidance,
        why = reasons(m, p, goal, ctx),
    )
}

private fun durationFor(p: CardioProtocol, minutes: Int): Int {
    if (minutes <= 0) return p.durationMin
    return minutes.coerceIn(p.durationMin, p.durationMax)
}

private fun score(p: CardioPrescription, goal: Goal, minutes: Int): Double {
    var s = 0.0
    if (p.protocol.suitableGoals.any { mapsToGoal(it, goal) }) s += 3.0
    if (minutes in p.protocol.durationMin..p.protocol.durationMax) s += 1.0
    if (p.modality.skillDemand == "low") s += 0.25
    if (p.protocol.intensity == ProtocolIntensity.EASY &&
        (goal == Goal.STRENGTH || goal == Goal.HYPERTROPHY)
    ) s += 1.0
    if (p.protocol.hard && (goal == Goal.ENDURANCE)) s += 0.5
    return s
}

private fun reasons(
    m: CardioModality,
    p: CardioProtocol,
    goal: Goal,
    ctx: PickContext,
): List<Reason> {
    val out = mutableListOf<Reason>()
    if (unsatisfiedSlots(m.requiredEquipment, ctx.equipment) == null &&
        m.requiredEquipment.all { it.contains("none") }
    ) {
        out += Reason("equipment", 1.0, "You need no kit for this — a walk still counts.")
    } else {
        out += Reason("equipment", 1.0, "This uses kit you already have.")
    }
    if (p.suitableGoals.any { mapsToGoal(it, goal) }) {
        out += Reason("goal", 3.0, "This shape of session fits what you are training for.")
    }
    if (p.intensity == ProtocolIntensity.EASY) {
        out += Reason("effort", 1.0, "Easy enough to talk. That is the point today.")
    }
    if (m.impact == CardioImpact.LOW) {
        out += Reason("impact", 0.5, "Low impact — kinder on the joints than a run.")
    }
    if (out.isEmpty()) {
        out += Reason("default", 0.0, "A legal pairing of ${m.name} and ${p.name}.")
    }
    return out
}
