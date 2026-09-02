package com.gymbuddy.domain.plan

import com.gymbuddy.domain.adapt.Adjustment
import com.gymbuddy.domain.catalog.Exercise
import com.gymbuddy.domain.catalog.Provenance
import com.gymbuddy.domain.picker.Reason
import com.gymbuddy.domain.picker.Slot
import com.gymbuddy.domain.picker.SlotRole
import com.gymbuddy.domain.programming.Effort
import com.gymbuddy.domain.programming.Goal
import com.gymbuddy.domain.programming.IntRange2
import com.gymbuddy.domain.programming.TrainingAge
import com.gymbuddy.domain.programming.Violation
import com.gymbuddy.domain.cardio.CardioPrescription
import com.gymbuddy.domain.warmup.StretchPick
import com.gymbuddy.domain.warmup.WarmupPlan
import kotlinx.serialization.Serializable

@Serializable
data class TemplateSlot(
    val pattern: String,
    val role: String,
    val priority: Int,
) {
    fun slotRole(): SlotRole = SlotRole.valueOf(role)
    fun asSlot(): Slot = Slot(pattern, slotRole())
}

@Serializable
data class TemplateDay(
    val name: String,
    val slots: List<TemplateSlot>,
)

@Serializable
data class Template(
    val id: String,
    val name: String,
    val goals: List<String>,
    val trainingAges: List<String>,
    val daysPerWeek: Int,
    val days: List<TemplateDay>,
    val provenanceDefault: Provenance,
    val provenance: Map<String, Provenance> = emptyMap(),
) {
    fun goalEnums(): List<Goal> = goals.map { Goal.valueOf(it) }
    fun ageEnums(): List<TrainingAge> = trainingAges.map { TrainingAge.valueOf(it) }
}

data class PlannedEntry(
    val exercise: Exercise,
    val slot: Slot,
    val sets: Int,
    val repRange: IntRange2,
    val restSec: IntRange2,
    val effort: Effort,
    val why: List<Reason>,
    val priority: Int,
    val prescribedWeightKg: Double? = null,
) {
    init {
        require(prescribedWeightKg == null) {
            "the app does not guess what someone can lift"
        }
    }
}

data class PlannedSession(
    val day: String,
    val entries: List<PlannedEntry>,
    val notes: List<String> = emptyList(),
    val warmup: WarmupPlan? = null,
    val cooldown: List<StretchPick> = emptyList(),
    val cooldownDisclaimer: String? = null,
    val cardio: CardioPrescription? = null,
)

data class TemplateChoice(
    val template: Template,
    val notes: List<String> = emptyList(),
)

sealed interface PlanResult {
    data class Built(
        val week: List<PlannedSession>,
        val warnings: List<Violation>,
        val proposals: List<Adjustment> = emptyList(),
    ) : PlanResult

    data class CannotBuild(val reasons: List<String>) : PlanResult
}
