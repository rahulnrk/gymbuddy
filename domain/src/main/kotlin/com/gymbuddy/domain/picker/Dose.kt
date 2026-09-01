package com.gymbuddy.domain.picker

import com.gymbuddy.domain.catalog.Exercise
import com.gymbuddy.domain.programming.IntRange2
import com.gymbuddy.domain.programming.TrainingAge
import com.gymbuddy.domain.programming.effort
import com.gymbuddy.domain.programming.exerciseKind
import com.gymbuddy.domain.programming.repRange
import com.gymbuddy.domain.programming.restSeconds
import com.gymbuddy.domain.programming.unspottedRepFloor

/**
 * Per-slot dose. Does not balance a week. Catalog rest is a point value,
 * so rest currently collapses to one number.
 */
fun dose(exercise: Exercise, slot: Slot, ctx: PickContext): Prescription {
    val cappedBy = mutableListOf<String>()
    val goalReps = repRange(ctx.user.goal)
    val catalogReps = IntRange2(
        exercise.repWindow.min,
        exercise.repWindow.max,
        goalReps.provenance,
    )
    var reps = intersect(goalReps, catalogReps)
    if (catalogReps.min != goalReps.min || catalogReps.max != goalReps.max) {
        cappedBy += "The catalog window for ${exercise.name} is ${exercise.repWindow.min} to " +
            "${exercise.repWindow.max} reps, which is more specific than the goal range."
    }
    val floor = unspottedRepFloor(exercise, ctx.equipment, ctx.user.trainsWithPartner)
    if (floor != null && reps.min < floor) {
        reps = IntRange2(floor, maxOf(reps.max, floor), reps.provenance)
        cappedBy += "Without a rack or a partner I would keep ${exercise.name} at $floor reps or more."
    }

    val goalRest = restSeconds(ctx.user.goal, exercise.exerciseKind(), ctx.user.trainingAge)
    val catalogRest = exercise.defaultRestSec
    val rest = IntRange2(catalogRest, catalogRest, goalRest.provenance)
    cappedBy += "The catalog lists $catalogRest seconds of rest for ${exercise.name}, " +
        "which is a single number rather than a range."

    return Prescription(
        sets = sessionSets(slot.role, ctx.user.trainingAge),
        repRange = reps,
        restSec = rest,
        effort = effort(ctx.user.goal, ctx.user.trainingAge),
        cappedBy = cappedBy,
    )
}

internal fun sessionSets(role: SlotRole, age: TrainingAge): Int = when (role) {
    SlotRole.PRIMARY -> if (age == TrainingAge.BEGINNER) {
        PickerConstants.SESSION_SETS_PRIMARY_BEGINNER
    } else {
        PickerConstants.SESSION_SETS_PRIMARY
    }
    SlotRole.SECONDARY -> PickerConstants.SESSION_SETS_SECONDARY
    SlotRole.ACCESSORY -> PickerConstants.SESSION_SETS_ACCESSORY
    SlotRole.ISOLATION -> PickerConstants.SESSION_SETS_ISOLATION
    SlotRole.CORE -> PickerConstants.SESSION_SETS_CORE
}

private fun intersect(goal: IntRange2, catalog: IntRange2): IntRange2 {
    val min = maxOf(goal.min, catalog.min)
    val max = minOf(goal.max, catalog.max)
    return if (min <= max) {
        IntRange2(min, max, catalog.provenance)
    } else {
        catalog
    }
}
