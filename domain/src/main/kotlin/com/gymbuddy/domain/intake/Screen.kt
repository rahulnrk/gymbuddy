package com.gymbuddy.domain.intake

/**
 * PAR-Q+ style filter. Not a diagnosis. Unknown is never Clear.
 */
fun screen(health: HealthScreen?): ScreenOutcome {
    if (health != null) {
        val live = liveRedFlags(health)
        if (live.isNotEmpty()) {
            return ScreenOutcome.ReferBeforeTraining(
                live,
                ReferralCopy.forFlags(live, health.recency),
            )
        }
    }
    val unanswered = unansweredScreenQuestions(health)
    if (unanswered.isNotEmpty()) return ScreenOutcome.Incomplete(unanswered)

    val notes = cautionNotes(health!!)
    return if (notes.isEmpty()) ScreenOutcome.Clear else ScreenOutcome.ProceedWithCaution(notes)
}

fun unansweredScreenQuestions(health: HealthScreen?): List<QuestionId> {
    if (health == null) {
        return FLAG_QUESTIONS.values.toList() + QuestionId.DECLARED_CONDITIONS + QuestionId.PAIN
    }
    val out = mutableListOf<QuestionId>()
    RedFlag.entries.forEach { flag ->
        val a = health.flags[flag] ?: Answer.UNKNOWN
        if (a == Answer.UNKNOWN) out += FLAG_QUESTIONS.getValue(flag)
        if (a == Answer.YES && flag in ACUTE_SYMPTOM_FLAGS) {
            val recency = health.recency[flag] ?: Answer.UNKNOWN
            if (recency == Answer.UNKNOWN) out += RECENCY_QUESTIONS.getValue(flag)
        }
    }
    if (health.hasListedCondition == Answer.UNKNOWN) out += QuestionId.DECLARED_CONDITIONS
    if (health.hasListedCondition == Answer.YES && health.conditions.isEmpty()) {
        out += QuestionId.DECLARED_CONDITIONS
    }
    if (health.hasPain == Answer.UNKNOWN) out += QuestionId.PAIN
    if (health.hasPain == Answer.YES && health.painReports.isEmpty()) out += QuestionId.PAIN
    return out
}

fun liveRedFlags(health: HealthScreen): List<RedFlag> {
    val cleared = health.clearance?.flagsCleared ?: emptySet()
    return RedFlag.entries.filter { flag ->
        if (health.flags[flag] != Answer.YES || flag in cleared) return@filter false
        if (flag in ACUTE_SYMPTOM_FLAGS) {
            val recency = health.recency[flag] ?: Answer.UNKNOWN
            recency != Answer.UNKNOWN
        } else {
            true
        }
    }
}

/**
 * The only writer for clearance. Requires an explicit date. Evaluate never
 * calls this.
 */
fun recordClearance(screen: HealthScreen, date: String, flags: Set<RedFlag>): HealthScreen {
    require(flags.isNotEmpty()) { "clearance must name at least one flag" }
    require(date.matches(Regex(IntakeConstants.CLEARANCE_DATE_PATTERN))) {
        "clearance date must be YYYY-MM-DD"
    }
    return screen.copy(
        clearance = ClinicalClearance(
            userReportedDate = date,
            flagsCleared = flags,
        ),
    )
}

fun declareCondition(reportedText: String, vocabIds: Set<String>, vocabNames: Map<String, String>): DeclaredCondition {
    val trimmed = reportedText.trim()
    require(trimmed.isNotEmpty()) { "reported text is required" }
    val byId = vocabIds.firstOrNull { it.equals(trimmed, ignoreCase = true) }
    if (byId != null) return DeclaredCondition.Known(byId, trimmed)
    val byName = vocabNames.entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) }
    if (byName != null) return DeclaredCondition.Known(byName.key, trimmed)
    return DeclaredCondition.Unknown(trimmed)
}

internal fun QuestionId.isScreenQuestion(): Boolean =
    this == QuestionId.DECLARED_CONDITIONS ||
        this == QuestionId.PAIN ||
        this in FLAG_QUESTIONS.values ||
        this in RECENCY_QUESTIONS.values

internal fun QuestionId.tierForAsk(): Tier = when (this) {
    QuestionId.GOAL,
    QuestionId.TRAINING_AGE,
    QuestionId.DAYS_PER_WEEK,
    QuestionId.SESSION_MINUTES,
    QuestionId.EQUIPMENT_PRESET,
    -> Tier.REQUIRED_TO_SUGGEST
    QuestionId.TRAINS_WITH_PARTNER,
    QuestionId.RECOVERY,
    QuestionId.DISLIKES,
    QuestionId.PRIOR_INJURIES,
    -> Tier.SHAPING
    else -> Tier.SAFETY
}

private fun cautionNotes(health: HealthScreen): List<String> {
    val notes = mutableListOf<String>()
    val clearance = health.clearance
    if (clearance != null && clearance.flagsCleared.isNotEmpty()) {
        notes += ReferralCopy.clearanceAcknowledgement(clearance.userReportedDate)
    }
    health.painReports.forEach { pain ->
        when (pain.character) {
            PainCharacter.SHARP, PainCharacter.RADIATING ->
                notes += "You said that movement hurts in a sharp or travelling way. " +
                    "We will keep it out of a plan until that settles or a clinician says otherwise."
            PainCharacter.DULL, PainCharacter.ACHE ->
                notes += "You mentioned a dull ache. We will keep the work easy on that area " +
                    "and we will not try to explain the ache."
        }
    }
    if (health.hasListedCondition == Answer.YES && health.conditions.any { it is DeclaredCondition.Unknown }) {
        notes += "You described something that is not in our list. We stored your words " +
            "and did not guess a match. A clinician is the right person to name it."
    }
    return notes
}
