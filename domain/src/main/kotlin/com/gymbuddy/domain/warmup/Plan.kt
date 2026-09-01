package com.gymbuddy.domain.warmup

import com.gymbuddy.domain.catalog.Catalog
import com.gymbuddy.domain.picker.PickContext
import com.gymbuddy.domain.picker.SlotRole
import com.gymbuddy.domain.plan.PlannedSession
import com.gymbuddy.domain.programming.setCredit

private val PULSE_PREFERENCE = listOf("easy-cycle", "easy-row", "skip-rope", "brisk-walk")

fun planWarmup(
    session: PlannedSession,
    ctx: PickContext,
    catalog: Catalog,
    library: WarmupLibrary,
    history: MobilityHistory = MobilityHistory(),
): WarmupPlan {
    if (session.entries.isEmpty() || library.drills.isEmpty()) {
        return WarmupPlan(pulse = null, drills = emptyList(), rampSets = emptyList(), estimatedMinutes = 0)
    }
    val notes = mutableListOf<String>()
    val sessionPatterns = session.entries.flatMap { listOf(it.slot.pattern, it.exercise.pattern) }.toSet()
    val primaryPatterns = session.entries
        .filter { it.slot.role == SlotRole.PRIMARY }
        .map { it.slot.pattern }
        .toSet()
        .ifEmpty { sessionPatterns }
    val primaryMuscles = session.entries
        .filter { it.slot.role == SlotRole.PRIMARY }
        .flatMap { entry ->
            catalog.musclesFor(entry.exercise).filter { setCredit(it.value) >= 0.5 }.keys
        }
        .toSet()
    val sessionMuscles = session.entries.flatMap { entry ->
        catalog.musclesFor(entry.exercise).filter { setCredit(it.value) >= 0.5 }.keys
    }.toSet()

    val usable = library.drills.filter { drillUsable(it, ctx) }
    val pulseDrill = pickPulse(usable)
    val pulse = pulseDrill?.let { drill ->
        val seen = history.timesSeen(drill.slug)
        WarmupEntry(
            drill = drill,
            why = if (seen <= 0) WarmupCopy.pulseWhy() else WarmupCopy.pulseShort(),
            timesSeen = seen,
        )
    }
    if (pulse == null) notes += WarmupCopy.noPulse()

    val ranked = usable
        .filter { it.method != "pulse" }
        .map { drill ->
            drill to drillScore(drill, sessionPatterns, primaryPatterns, primaryMuscles, sessionMuscles)
        }
        .filter { it.second > 0 }
        .sortedWith(
            compareByDescending<Pair<WarmupDrill, Int>> { it.second }
                .thenBy { it.first.slug },
        )
        .map { it.first }

    val picked = mutableListOf<WarmupDrill>()
    picked += takeDiverse(ranked.filter { it.method == "mobility" }, 2, skip = picked, regionPeers = emptyList(), fill = true)
    picked += takeDiverse(ranked.filter { it.method == "activation" }, 2, skip = picked, regionPeers = emptyList(), fill = true)
    picked += takeDiverse(ranked.filter { it.method == "rehearsal" }, 2, skip = picked, regionPeers = emptyList(), fill = true)
    picked += takeDiverse(
        ranked,
        WarmupConstants.DRILL_COUNT_MAX - picked.size,
        skip = picked,
        regionPeers = picked,
        fill = false,
    )

    var drills = picked.distinctBy { it.slug }.take(WarmupConstants.DRILL_COUNT_MAX).map { drill ->
        val seen = history.timesSeen(drill.slug)
        val matchedPatterns = drill.targetPatterns.intersect(sessionPatterns).ifEmpty { drill.targetPatterns }
        val matchedMuscles = drill.targetMuscles.intersect(sessionMuscles).ifEmpty { drill.targetMuscles }
        val nameOf = { id: String -> catalog.muscles[id]?.name?.lowercase() ?: "those muscles" }
        val why = if (seen <= 0) {
            when (drill.method) {
                "mobility" -> WarmupCopy.mobilityWhy(matchedPatterns)
                "activation" -> WarmupCopy.activationWhy(matchedMuscles, nameOf)
                else -> WarmupCopy.rehearsalWhy(matchedPatterns)
            }
        } else {
            when (drill.method) {
                "mobility" -> WarmupCopy.mobilityShort(matchedPatterns)
                "activation" -> WarmupCopy.activationShort(matchedMuscles, nameOf)
                else -> WarmupCopy.rehearsalShort(matchedPatterns)
            }
        }
        WarmupEntry(drill, why, seen)
    }

    val first = session.entries.firstOrNull { it.slot.role == SlotRole.PRIMARY } ?: session.entries.first()
    val ramps = rampSetsFor(first.exercise.name, ctx.user.trainingAge.name)
    val restMid = (first.restSec.min + first.restSec.max) / 2

    while (
        warmupMinutes(pulse, drills, ramps, restMid) > WarmupConstants.WARMUP_CAP_MIN &&
        drills.isNotEmpty()
    ) {
        drills = drills.dropLast(1)
        if (WarmupCopy.keptShort() !in notes) notes += WarmupCopy.keptShort()
    }

    return WarmupPlan(
        pulse = pulse,
        drills = drills,
        rampSets = ramps,
        estimatedMinutes = warmupMinutes(pulse, drills, ramps, restMid),
        notes = notes,
    )
}

fun suggestStretches(
    session: PlannedSession,
    ctx: PickContext,
    catalog: Catalog,
    library: StretchLibrary,
    history: MobilityHistory = MobilityHistory(),
): Pair<List<StretchPick>, String?> {
    if (session.entries.isEmpty() || library.stretches.isEmpty()) return emptyList<StretchPick>() to null
    val worked = mutableMapOf<String, Double>()
    session.entries.forEach { entry ->
        catalog.musclesFor(entry.exercise).forEach { (id, weight) ->
            val credit = setCredit(weight) * entry.sets
            if (credit > 0.0) worked[id] = (worked[id] ?: 0.0) + credit
        }
    }
    val usable = library.stretches.filter { stretch ->
        stretch.type == "static" &&
            stretch.timing == "post-session" &&
            stretchUsable(stretch, ctx) &&
            stretch.targetMuscles.any { (worked[it] ?: 0.0) > 0.0 }
    }
    val ranked = usable.sortedWith(
        compareByDescending<Stretch> { s -> s.targetMuscles.sumOf { worked[it] ?: 0.0 } }
            .thenBy { it.slug },
    )
    val out = mutableListOf<StretchPick>()
    val covered = mutableSetOf<String>()
    for (stretch in ranked) {
        if (out.size >= WarmupConstants.STRETCH_COUNT) break
        val novel = stretch.targetMuscles.filter { it !in covered }
        if (novel.isEmpty()) continue
        covered += stretch.targetMuscles
        val why = WarmupCopy.stretchWhy(novel) { id ->
            catalog.muscles[id]?.name?.lowercase() ?: "those muscles"
        }
        out += StretchPick(stretch, why)
    }
    val disclaimer = if (!history.heardStretchDisclaimer && out.isNotEmpty()) {
        WarmupCopy.stretchDisclaimer()
    } else {
        null
    }
    return out to disclaimer
}

internal fun drillUsable(drill: WarmupDrill, ctx: PickContext): Boolean {
    if (drill.contraindications.any { it in ctx.user.conditions }) return false
    return drill.requiredEquipment.all { slot -> slot.any { it in ctx.equipment } }
}

internal fun stretchUsable(stretch: Stretch, ctx: PickContext): Boolean {
    if (stretch.contraindications.any { it in ctx.user.conditions }) return false
    return stretch.requiredEquipment.all { slot -> slot.any { it in ctx.equipment } }
}

internal fun warmupMinutes(
    pulse: WarmupEntry?,
    drills: List<WarmupEntry>,
    ramps: List<RampSet>,
    firstRestMid: Int,
): Int {
    val pulseSec = pulse?.drill?.durationSec ?: 0
    val drillSec = drills.sumOf { it.drill.durationSec }
    val rampWork = ramps.size * WarmupConstants.RAMP_SET_SEC
    val between = if (ramps.size <= 1) 0 else (ramps.size - 1) * (firstRestMid / 2).coerceAtLeast(30)
    return (pulseSec + drillSec + rampWork + between + 59) / 60
}

private fun pickPulse(usable: List<WarmupDrill>): WarmupDrill? {
    val pulses = usable.filter { it.method == "pulse" }
    PULSE_PREFERENCE.forEach { slug -> pulses.firstOrNull { it.slug == slug }?.let { return it } }
    return pulses.minByOrNull { it.slug }
}

private val LOWER_PATTERNS = setOf("squat", "lunge", "hinge", "quads", "hamstrings", "lower back", "calves", "carry")
private val PUSH_PATTERNS = setOf(
    "horizontal push", "incline push", "decline push", "vertical push",
    "chest isolation", "delt isolation", "triceps",
)
private val PULL_PATTERNS = setOf("horizontal pull", "vertical pull", "lats", "traps", "rear delt", "biceps")

internal fun drillRegion(drill: WarmupDrill): String {
    if (drill.targetMuscles == listOf("neck") || drill.slug == "chin-tuck") return "neck"
    if (drill.targetPatterns.any { it in LOWER_PATTERNS }) return "lower"
    if (drill.targetPatterns.any { it in PUSH_PATTERNS }) return "push"
    if (drill.targetPatterns.any { it in PULL_PATTERNS }) return "pull"
    if (drill.targetPatterns.any { it == "core" }) return "core"
    if (drill.targetMuscles.any { it in setOf("quad", "glute", "hamstring", "calf", "adductor", "abductor") }) return "lower"
    if (drill.targetMuscles.any { it in setOf("chest_mid", "chest_upper", "delt_front", "serratus", "triceps") }) return "push"
    if (drill.targetMuscles.any { it in setOf("lat", "trap_mid", "trap_upper", "delt_rear", "biceps") }) return "pull"
    return "other"
}

private fun takeDiverse(
    candidates: List<WarmupDrill>,
    n: Int,
    skip: List<WarmupDrill>,
    regionPeers: List<WarmupDrill>,
    fill: Boolean,
): List<WarmupDrill> {
    if (n <= 0) return emptyList()
    val out = mutableListOf<WarmupDrill>()
    val regions = regionPeers.map { drillRegion(it) }.toMutableSet()
    for (drill in candidates) {
        if (out.size >= n) break
        if (drill in skip || drill in out) continue
        val region = drillRegion(drill)
        if (region in regions) continue
        regions += region
        out += drill
    }
    if (fill) {
        for (drill in candidates) {
            if (out.size >= n) break
            if (drill in skip || drill in out) continue
            out += drill
        }
    }
    return out
}

private fun drillScore(
    drill: WarmupDrill,
    sessionPatterns: Set<String>,
    primaryPatterns: Set<String>,
    primaryMuscles: Set<String>,
    sessionMuscles: Set<String>,
): Int {
    val patternHits = drill.targetPatterns.count { it in sessionPatterns }
    val primaryHits = drill.targetPatterns.count { it in primaryPatterns }
    val primaryMuscleHits = drill.targetMuscles.count { it in primaryMuscles }
    val muscleHits = drill.targetMuscles.count { it in sessionMuscles }
    return when (drill.method) {
        "rehearsal" -> primaryHits * 4 + patternHits * 2
        "activation" -> primaryMuscleHits * 4 + primaryHits * 3 + muscleHits
        "mobility" -> primaryHits * 4 + patternHits * 2 + muscleHits
        else -> 0
    }
}

private fun rampSetsFor(liftName: String, trainingAge: String): List<RampSet> {
    val n = WarmupConstants.RAMP_SETS.coerceIn(WarmupConstants.RAMP_SETS_MIN, WarmupConstants.RAMP_SETS_MAX)
    val efforts = listOf(
        "about half your working weight",
        "a little under your working weight",
        "a notch under your working weight",
    )
    val reps = if (trainingAge == "BEGINNER") listOf(5, 5, 3) else listOf(5, 3, 2)
    return (0 until n).map { i ->
        RampSet(reps = reps[i], effortDescription = efforts[i], liftName = liftName)
    }
}
