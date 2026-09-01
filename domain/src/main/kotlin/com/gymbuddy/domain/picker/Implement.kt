package com.gymbuddy.domain.picker

import com.gymbuddy.domain.catalog.Exercise

/**
 * How the lift is loaded. Consequence and skillDemand tie-breaks apply only
 * inside one class. A machine and a dumbbell are not equivalent.
 *
 * Token lists are the contract, copied from `knowledge/vocab/equipment.json`
 * categories, not inferred from id strings at runtime.
 */
internal fun implementClass(exercise: Exercise): String {
    var machine = false
    var cable = false
    var free = false
    var body = false
    for (slot in exercise.requiredEquipment) {
        for (id in slot) {
            when (tokenImplement(id)) {
                "machine" -> machine = true
                "cable" -> cable = true
                "freeweight" -> free = true
                "bodyweight" -> body = true
            }
        }
    }
    return when {
        machine -> "machine"
        cable -> "cable"
        free -> "freeweight"
        body -> "bodyweight"
        else -> "other"
    }
}

internal fun tokenImplement(id: String): String = when (id) {
    "smith-machine",
    "pec-deck",
    "chest-press-machine",
    "shoulder-press-machine",
    "leg-press-machine",
    "hack-squat-machine",
    "leg-extension-machine",
    "leg-curl-machine",
    "calf-raise-machine",
    "assisted-pullup-machine",
    -> "machine"
    "cable-station",
    "lat-pulldown-station",
    "seated-row-station",
    -> "cable"
    "barbell",
    "ez-bar",
    "trap-bar",
    "dumbbell",
    "kettlebell",
    "plate",
    -> "freeweight"
    "none",
    "pull-up-bar",
    "dip-bars",
    "sturdy-table",
    "door-frame",
    -> "bodyweight"
    else -> "support"
}
