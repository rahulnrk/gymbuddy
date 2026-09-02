package com.gymbuddy.domain.pain

import com.gymbuddy.domain.intake.RedFlag
import com.gymbuddy.domain.intake.ReferralCopy

/**
 * Mid-workout, slightly out of breath. Short. No condition names.
 */
object PainCopy {
    fun question(id: PainQuestionId): String = when (id) {
        PainQuestionId.SPOT ->
            "Where is it — one spot you can point to?"
        PainQuestionId.ONSET ->
            "Did it hit at a moment, or build up over the day?"
        PainQuestionId.SIDES ->
            "Both sides, or just one?"
        PainQuestionId.CHARACTER ->
            "Sharp, or more of an ache?"
        PainQuestionId.TRAVEL ->
            "Does it travel? Any pins and needles, or weakness?"
        PainQuestionId.AT_REST ->
            "Worse when you move, or aching even at rest?"
    }

    fun soreness(): String =
        "That's the kind of ache that builds after training. " +
            "Start light on this one and see how it feels — we can build up if it's fine."

    fun stopMovement(name: String, siblingName: String?): String {
        val swap = siblingName?.let { " $it would keep the session going." } ?: ""
        return "We'll skip the $name today.$swap Nothing changes until you say yes."
    }

    fun referOtherAreas(where: String = ""): String =
        "${travelsFrom(where)} We're stopping this pattern. " +
            "The rest of the session is still there if you want it."

    fun referNight(): String =
        "Pain that wakes you at night isn't something we train through. " +
            "We'll stop this pattern. Other work is still available if you want it."

    fun referOnly(): String =
        "We're stopping this pattern. Get this checked before we load it again."

    /**
     * In-session chest. Contract 30 keeps "You marked chest pain" for intake.
     * The urgency line is shared.
     */
    val IN_SESSION_CHEST =
        "Stop. Pain in the chest isn't something we work through. " +
            "Get this checked before we keep going."

    fun urgent(flags: List<RedFlag>): String {
        val unique = flags.distinct()
        return if (unique.singleOrNull() == RedFlag.CHEST_PAIN) {
            "$IN_SESSION_CHEST ${ReferralCopy.RECENCY_URGENCY}"
        } else {
            ReferralCopy.forFlags(flags)
        }
    }

    internal fun travelsFrom(where: String): String {
        val w = where.trim().lowercase().removePrefix("my ").removePrefix("the ")
        val path = when {
            LEG.any { it in w } -> "down the leg"
            ARM.any { it in w } -> "down the arm"
            BACK.any { it in w } -> "down the back"
            w.isBlank() -> return "Pain that travels isn't something to train through."
            else -> "from the $w"
        }
        return "Pain that travels $path isn't something to train through."
    }

    private val LEG = listOf(
        "leg", "thigh", "calf", "hamstring", "quad", "knee", "ankle", "foot", "shin", "glute",
    )
    private val ARM = listOf(
        "arm", "shoulder", "elbow", "wrist", "hand", "bicep", "tricep", "forearm",
    )
    private val BACK = listOf("back", "spine", "neck", "lumbar")

    fun followUp(where: String): String {
        val place = where.trim().lowercase().removePrefix("my ").removePrefix("the ").ifBlank { "that" }
        return "How's that $place today?"
    }
}
