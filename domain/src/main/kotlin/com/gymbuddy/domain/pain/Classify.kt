package com.gymbuddy.domain.pain

fun classifyLocation(where: String): PainLocationKind {
    val w = where.lowercase()
    if (w.isBlank()) return PainLocationKind.UNKNOWN
    if (CHEST.any { it in w }) return PainLocationKind.CHEST
    if (HEAD.any { it in w }) return PainLocationKind.HEAD
    if (SPINE.any { it in w }) return PainLocationKind.SPINE
    if (JOINT.any { it in w }) return PainLocationKind.JOINT
    if (MUSCLE.any { it in w }) return PainLocationKind.MUSCLE
    return PainLocationKind.UNKNOWN
}

fun recordIntensity(raw: Int?): Int? {
    val n = raw ?: return null
    if (n < PainConstants.INTENSITY_MIN || n > PainConstants.INTENSITY_MAX) return null
    return n
}

private val CHEST = listOf("chest", "sternum", "heart", "breastbone")
private val HEAD = listOf("head", "skull", "temple", "forehead")
private val SPINE = listOf("spine", "low back", "lower back", "upper back", "neck", "vertebra")
private val JOINT = listOf("knee", "shoulder", "elbow", "wrist", "hip", "ankle", "joint")
private val MUSCLE = listOf(
    "quad", "hamstring", "glute", "calf", "bicep", "tricep", "pec",
    "lat", "trap", "thigh", "muscle",
)
