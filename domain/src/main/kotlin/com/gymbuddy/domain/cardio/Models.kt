package com.gymbuddy.domain.cardio

import com.gymbuddy.domain.catalog.Provenance
import com.gymbuddy.domain.picker.Reason
import com.gymbuddy.domain.programming.Goal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CardioImpact {
    @SerialName("low") LOW,
    @SerialName("moderate") MODERATE,
    @SerialName("high") HIGH,
}

@Serializable
enum class ProtocolType {
    @SerialName("recovery") RECOVERY,
    @SerialName("steady_state") STEADY_STATE,
    @SerialName("tempo") TEMPO,
    @SerialName("threshold") THRESHOLD,
    @SerialName("intervals") INTERVALS,
    @SerialName("sprint_intervals") SPRINT_INTERVALS,
}

@Serializable
enum class ProtocolIntensity {
    @SerialName("easy") EASY,
    @SerialName("moderate") MODERATE,
    @SerialName("hard") HARD,
}

@Serializable
data class CardioModality(
    val slug: String,
    val id: String,
    val name: String,
    val requiredEquipment: List<List<String>>,
    val impact: CardioImpact,
    val primaryDemand: List<String>,
    val skillDemand: String,
    val setting: String,
    val suitableProtocolTypes: List<ProtocolType>,
    val contraindications: List<String> = emptyList(),
    val provenanceDefault: Provenance,
    val provenance: Map<String, Provenance> = emptyMap(),
)

@Serializable
data class CardioProtocol(
    val slug: String,
    val id: String,
    val name: String,
    val type: ProtocolType,
    val intensity: ProtocolIntensity,
    val effortGuidance: String,
    val workSec: Int? = null,
    val recoverSec: Int? = null,
    val rounds: List<Int> = emptyList(),
    val typicalSessionMin: List<Int>,
    val suitableGoals: List<String>,
    val requiresAerobicBase: Boolean,
    val provenanceDefault: Provenance,
    val provenance: Map<String, Provenance> = emptyMap(),
) {
    val durationMin: Int get() = typicalSessionMin.first()
    val durationMax: Int get() = typicalSessionMin.last()
    val hard: Boolean get() = intensity == ProtocolIntensity.HARD
}

data class CardioPrescription(
    val modality: CardioModality,
    val protocol: CardioProtocol,
    val durationMin: Int,
    val effort: String,
    val why: List<Reason>,
)

sealed interface CardioExclusion {
    val modality: CardioModality
    val protocol: CardioProtocol
    val explanation: String

    data class ProtocolNotSuitable(
        override val modality: CardioModality,
        override val protocol: CardioProtocol,
    ) : CardioExclusion {
        override val explanation: String
            get() = "${protocol.name} does not belong on ${modality.name}."
    }

    data class MissingEquipment(
        override val modality: CardioModality,
        override val protocol: CardioProtocol,
        val slots: List<List<String>>,
    ) : CardioExclusion {
        override val explanation: String
            get() = "You do not have what ${modality.name} needs."
    }

    data class NeedsAerobicBase(
        override val modality: CardioModality,
        override val protocol: CardioProtocol,
    ) : CardioExclusion {
        override val explanation: String
            get() = "${protocol.name} waits until you have an easy aerobic base. Not a beginner session."
    }

    data class ImpactBlocked(
        override val modality: CardioModality,
        override val protocol: CardioProtocol,
        val condition: String,
    ) : CardioExclusion {
        override val explanation: String
            get() = "${modality.name} is too much impact with $condition."
    }

    data class Contraindicated(
        override val modality: CardioModality,
        override val protocol: CardioProtocol,
        val condition: String,
    ) : CardioExclusion {
        override val explanation: String
            get() = "${modality.name} is ruled out by $condition."
    }
}

data class CardioFilter(
    val kept: List<Pair<CardioModality, CardioProtocol>>,
    val exclusions: List<CardioExclusion>,
)

data class CardioProgression(
    val prescription: CardioPrescription,
    val changed: ProgressVariable,
    val reason: String,
)

enum class ProgressVariable {
    DURATION,
    FREQUENCY,
    INTENSITY,
    HOLD,
}

class CardioLibrary(
    val modalities: List<CardioModality>,
    val protocols: List<CardioProtocol>,
) {
    fun modality(slug: String): CardioModality? = modalities.firstOrNull { it.slug == slug }
    fun protocol(slug: String): CardioProtocol? = protocols.firstOrNull { it.slug == slug }
}

internal fun mapsToGoal(token: String, goal: Goal): Boolean {
    if (token == goal.name) return true
    if (token == "CARDIO_FITNESS" && goal == Goal.ENDURANCE) return true
    return false
}
