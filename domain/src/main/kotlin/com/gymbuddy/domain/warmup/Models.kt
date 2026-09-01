package com.gymbuddy.domain.warmup

import com.gymbuddy.domain.catalog.Provenance
import kotlinx.serialization.Serializable

@Serializable
data class WarmupDrill(
    val slug: String,
    val id: String,
    val name: String,
    val method: String,
    val targetPatterns: List<String> = emptyList(),
    val targetMuscles: List<String> = emptyList(),
    val durationSec: Int,
    val reps: Int = 0,
    val perSide: Boolean = false,
    val requiredEquipment: List<List<String>>,
    val howTo: String,
    val commonErrors: String,
    val effortGuidance: String,
    val contraindications: List<String> = emptyList(),
    val provenanceDefault: Provenance,
    val provenance: Map<String, Provenance> = emptyMap(),
)

@Serializable
data class Stretch(
    val slug: String,
    val id: String,
    val name: String,
    val type: String,
    val targetMuscles: List<String>,
    val timing: String,
    val holdSec: Int,
    val repeats: Int,
    val perSide: Boolean = false,
    val requiredEquipment: List<List<String>>,
    val howTo: String,
    val intensityGuidance: String,
    val contraindications: List<String> = emptyList(),
    val provenanceDefault: Provenance,
    val provenance: Map<String, Provenance> = emptyMap(),
)

data class WarmupEntry(
    val drill: WarmupDrill,
    val why: String,
    val timesSeen: Int = 0,
)

data class RampSet(
    val reps: Int,
    val effortDescription: String,
    val liftName: String,
)

data class WarmupPlan(
    val pulse: WarmupEntry?,
    val drills: List<WarmupEntry>,
    val rampSets: List<RampSet>,
    val estimatedMinutes: Int,
    val notes: List<String> = emptyList(),
)

data class StretchPick(
    val stretch: Stretch,
    val why: String,
)

class WarmupLibrary(val drills: List<WarmupDrill>) {
    fun bySlug(slug: String): WarmupDrill? = drills.firstOrNull { it.slug == slug }
}

class StretchLibrary(val stretches: List<Stretch>) {
    fun bySlug(slug: String): Stretch? = stretches.firstOrNull { it.slug == slug }
}

/**
 * How many times this user has already heard a drill, and whether they have
 * heard the stretch-is-not-for-weakness line. Lives on the plan, not the catalog.
 */
data class MobilityHistory(
    val drillTimesSeen: Map<String, Int> = emptyMap(),
    val heardStretchDisclaimer: Boolean = false,
) {
    fun timesSeen(slug: String): Int = drillTimesSeen[slug] ?: 0

    fun after(plan: WarmupPlan, cooldownCount: Int): MobilityHistory {
        val next = drillTimesSeen.toMutableMap()
        fun bump(slug: String) {
            next[slug] = (next[slug] ?: 0) + 1
        }
        plan.pulse?.let { bump(it.drill.slug) }
        plan.drills.forEach { bump(it.drill.slug) }
        return copy(
            drillTimesSeen = next,
            heardStretchDisclaimer = heardStretchDisclaimer || cooldownCount > 0,
        )
    }

    fun withTimes(slugs: Collection<String>, times: Int, heardDisclaimer: Boolean = heardStretchDisclaimer) =
        copy(
            drillTimesSeen = drillTimesSeen + slugs.associateWith { times },
            heardStretchDisclaimer = heardDisclaimer,
        )
}
