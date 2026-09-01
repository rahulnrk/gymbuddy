package com.gymbuddy.domain.catalog

import kotlinx.serialization.Serializable

@Serializable
data class Muscle(
    val id: String,
    val name: String,
    val side: String,
    val group: String,
)

@Serializable
data class Pattern(
    val id: String,
    val muscles: Map<String, Double>,
)

@Serializable
data class Equipment(
    val id: String,
    val name: String,
    val category: String,
    val tier: String,
    val aliases: List<String> = emptyList(),
)

@Serializable
data class Condition(
    val id: String,
    val name: String,
    val note: String,
)

@Serializable
data class Provenance(
    val confidence: String,
    val basis: String,
    val source: String,
    val reviewed: String,
)

@Serializable
data class Alias(
    val text: String,
    val locale: String,
    val confidence: Double,
)

@Serializable
data class Spoken(
    val short: String,
    val tts: String,
)

@Serializable
data class RepWindow(
    val min: Int,
    val max: Int,
)

@Serializable
data class Exercise(
    val slug: String,
    val id: String,
    val name: String,
    val kind: String,
    val pattern: String,
    val requiredEquipment: List<String>,
    val optionalEquipment: List<String> = emptyList(),
    val skillDemand: String,
    val consequenceOfFailure: String,
    val fatigueCost: String,
    val damageMultiplier: Double,
    val unilateral: Boolean,
    val defaultRestSec: Int,
    val repWindow: RepWindow,
    val romNote: String? = null,
    val muscleOverrides: Map<String, Double>? = null,
    val contraindications: List<String> = emptyList(),
    val aliases: List<Alias> = emptyList(),
    val spoken: Spoken,
    val safeToSpeakMidSet: Boolean,
    val substitutes: List<String> = emptyList(),
    val legacyIds: List<String> = emptyList(),
    val provenanceDefault: Provenance,
    val provenance: Map<String, Provenance> = emptyMap(),
)

data class SpokenCandidate(
    val exercise: Exercise,
    val confidence: Double,
    val matchedOn: String,
)
