package com.gymbuddy.domain.plan

import com.gymbuddy.domain.catalog.Catalog
import com.gymbuddy.domain.catalog.Exercise

object PlanCopy {
    fun droppedSlot(job: String): String =
        "I couldn't find $job you can do with the equipment you've got."

    fun usedSibling(usedJob: String, askedJob: String): String =
        "I used $usedJob instead of $askedJob, with the equipment you've got."

    fun unlockWith(exercise: Exercise, gear: List<String>): String {
        val kit = speakGear(gear)
        return if (kit == null) {
            "Nothing in the catalog fills this with other equipment either."
        } else {
            "$kit would let us use ${exercise.name}."
        }
    }

    fun cutForTime(name: String, targetMin: Int): String =
        "I took out $name so this session fits in about $targetMin minutes."

    fun reducedSets(name: String, to: Int, targetMin: Int): String =
        "I cut $name to ${sets(to)} so this session fits in about $targetMin minutes."

    fun stillOver(minutes: Int): String =
        "Even the main lifts take about $minutes minutes. I kept them rather than gutting the session."

    fun nearerDays(asked: Int, used: Int, name: String): String =
        "You asked for $asked days. I'll use $name, which is $used days — the closest I have."

    fun cannotBuild(): String =
        "I can't build a useful week from what you have."

    fun jobForPattern(pattern: String): String = when (pattern) {
        "squat" -> "a squat"
        "hinge" -> "a hinge"
        "lunge" -> "a lunge"
        "horizontal push" -> "a press"
        "incline push" -> "an incline press"
        "decline push" -> "a decline press"
        "vertical push" -> "an overhead press"
        "horizontal pull" -> "a row"
        "vertical pull" -> "a pulldown or pull-up"
        "chest isolation" -> "chest isolation work"
        "delt isolation" -> "side-delt work"
        "rear delt" -> "rear-delt work"
        "triceps" -> "triceps work"
        "biceps" -> "biceps work"
        "lats" -> "lat work"
        "traps" -> "shrug work"
        "quads" -> "quad isolation work"
        "hamstrings" -> "hamstring work"
        "lower back" -> "lower-back work"
        "calves" -> "calf work"
        "core" -> "core work"
        "carry" -> "a carry"
        else -> "that work"
    }

    fun speakGear(ids: List<String>): String? {
        if (ids.isEmpty()) return null
        val names = ids.map { prettyGear(it) }
        return when (names.size) {
            1 -> names[0].replaceFirstChar { it.uppercase() }
            2 -> "${names[0].replaceFirstChar { it.uppercase() }} and ${names[1]}"
            else -> names.dropLast(1).joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } } +
                ", and ${names.last()}"
        }
    }

    fun prettyGear(id: String): String = when (id) {
        "none" -> "just the floor"
        "bench-flat" -> "a flat bench"
        "bench-adjustable" -> "an adjustable bench"
        "squat-rack" -> "a squat rack"
        "power-rack" -> "a power rack"
        "pull-up-bar" -> "a pull-up bar"
        "cable-station" -> "a cable station"
        "lat-pulldown-station" -> "a lat pulldown"
        "dumbbell" -> "dumbbells"
        "kettlebell" -> "a kettlebell"
        "barbell" -> "a barbell"
        "plate" -> "plates"
        else -> id.replace('-', ' ')
    }

    fun sets(n: Int): String = if (n == 1) "1 set" else "$n sets"
}

internal fun missingTokens(exercise: Exercise, have: Set<String>): List<String> =
    exercise.requiredEquipment.mapNotNull { slot ->
        if (slot.any { it in have }) null else slot.first()
    }

internal fun catalogName(catalog: Catalog, id: String): String =
    catalog.equipment[id]?.name?.lowercase() ?: PlanCopy.prettyGear(id)
