package com.gymbuddy.domain.intake

import com.gymbuddy.domain.catalog.Equipment
import com.gymbuddy.domain.programming.Goal
import com.gymbuddy.domain.programming.RecoverySignals
import com.gymbuddy.domain.programming.TrainingAge

enum class Tier {
    REQUIRED_TO_SUGGEST,
    SAFETY,
    SHAPING,
}

/** Explicit. Never coerced to No. A skip is UNKNOWN, not a clean answer. */
enum class Answer {
    YES,
    NO,
    UNKNOWN,
}

enum class EquipmentPreset {
    FULL_GYM,
    HOME_LIMITED,
    BODYWEIGHT_ONLY,
}

enum class RedFlag {
    CHEST_PAIN,
    BREATHLESSNESS_AT_REST_OR_LIGHT_EXERTION,
    DIZZINESS_OR_FAINTING,
    PALPITATIONS_OR_IRREGULAR_HEARTBEAT,
    RECENT_SURGERY,
    UNEXPLAINED_NIGHT_PAIN,
    RADIATING_NUMBNESS_OR_WEAKNESS,
    PREGNANCY_COMPLICATION,
    UNCONTROLLED_DIAGNOSED_CONDITION,
    NEW_UNEXPLAINED_SWELLING_OR_PAIN,
}

enum class PainCharacter {
    SHARP,
    DULL,
    ACHE,
    RADIATING,
}

enum class QuestionId {
    GOAL,
    TRAINING_AGE,
    DAYS_PER_WEEK,
    SESSION_MINUTES,
    EQUIPMENT_PRESET,
    FLAG_CHEST_PAIN,
    FLAG_BREATHLESSNESS,
    FLAG_DIZZINESS,
    FLAG_PALPITATIONS,
    FLAG_RECENT_SURGERY,
    FLAG_NIGHT_PAIN,
    FLAG_RADIATING_NUMBNESS,
    FLAG_PREGNANCY_COMPLICATION,
    FLAG_UNCONTROLLED_CONDITION,
    FLAG_NEW_SWELLING_OR_PAIN,
    RECENCY_CHEST_PAIN,
    RECENCY_BREATHLESSNESS,
    RECENCY_DIZZINESS,
    RECENCY_PALPITATIONS,
    RECENCY_RADIATING_NUMBNESS,
    RECENCY_NEW_SWELLING_OR_PAIN,
    DECLARED_CONDITIONS,
    PAIN,
    TRAINS_WITH_PARTNER,
    RECOVERY,
    DISLIKES,
    PRIOR_INJURIES,
}

data class Requirement(
    val question: QuestionId,
    val tier: Tier,
)

data class EquipmentVocab(
    val byId: Map<String, Equipment>,
)

data class EquipmentAvailability(
    val preset: EquipmentPreset,
    val alsoHave: Set<String> = emptySet(),
    val doNotHave: Set<String> = emptySet(),
) {
    /**
     * Preset expands via equipment `tier`. [doNotHave] wins on conflict with
     * [alsoHave] and with the preset.
     */
    fun resolve(vocab: EquipmentVocab): Set<String> {
        val base = vocab.byId.filter { (_, eq) ->
            when (preset) {
                EquipmentPreset.FULL_GYM -> true
                EquipmentPreset.HOME_LIMITED -> eq.tier != "commercial"
                EquipmentPreset.BODYWEIGHT_ONLY -> eq.tier == "none"
            }
        }.keys
        return (base + alsoHave) - doNotHave
    }
}

data class EquipmentCoverage(
    val tokenCount: Int,
    val exerciseCount: Int,
)

sealed interface DeclaredCondition {
    data class Known(val id: String, val reportedText: String) : DeclaredCondition
    data class Unknown(val reportedText: String) : DeclaredCondition
}

data class PainReport(
    val where: String,
    val whenItHappens: String,
    val movements: Set<String>,
    val character: PainCharacter,
    val since: String? = null,
    /** 0–10 if they said a number. History only. Never a triage gate. */
    val intensity: Int? = null,
)

/**
 * User-reported clinician clearance. Not a medical fact. Requires a date.
 * No code path may invent this.
 */
data class ClinicalClearance(
    val userReportedDate: String,
    val flagsCleared: Set<RedFlag>,
)

data class HealthScreen(
    val flags: Map<RedFlag, Answer> = emptyMap(),
    val recency: Map<RedFlag, Answer> = emptyMap(),
    val hasListedCondition: Answer = Answer.UNKNOWN,
    val conditions: List<DeclaredCondition> = emptyList(),
    val hasPain: Answer = Answer.UNKNOWN,
    val painReports: List<PainReport> = emptyList(),
    val clearance: ClinicalClearance? = null,
)

data class Intake(
    val goal: Goal? = null,
    val trainingAge: TrainingAge? = null,
    val daysPerWeek: Int? = null,
    val typicalSessionMinutes: Int? = null,
    val equipment: EquipmentAvailability? = null,
    val healthScreen: HealthScreen? = null,
    val recovery: RecoverySignals? = null,
    val dislikes: Set<String> = emptySet(),
    val preferences: Set<String> = emptySet(),
    val trainsWithPartner: Boolean? = null,
    val priorInjuriesResolved: List<String> = emptyList(),
)

sealed interface ScreenOutcome {
    data object Clear : ScreenOutcome
    data class ProceedWithCaution(val notes: List<String>) : ScreenOutcome
    data class ReferBeforeTraining(val flags: List<RedFlag>, val message: String) : ScreenOutcome
    data class Incomplete(val unanswered: List<QuestionId>) : ScreenOutcome
}

sealed interface IntakeResult {
    data class Ready(val context: com.gymbuddy.domain.programming.UserContext) : IntakeResult
    data class NeedMore(val missing: List<Requirement>, val nextQuestion: QuestionId) : IntakeResult
    data class Refer(val outcome: ScreenOutcome.ReferBeforeTraining) : IntakeResult
}

internal val ASK_ORDER: List<QuestionId> = listOf(
    QuestionId.GOAL,
    QuestionId.TRAINING_AGE,
    QuestionId.DAYS_PER_WEEK,
    QuestionId.SESSION_MINUTES,
    QuestionId.EQUIPMENT_PRESET,
    QuestionId.FLAG_CHEST_PAIN,
    QuestionId.RECENCY_CHEST_PAIN,
    QuestionId.FLAG_BREATHLESSNESS,
    QuestionId.RECENCY_BREATHLESSNESS,
    QuestionId.FLAG_DIZZINESS,
    QuestionId.RECENCY_DIZZINESS,
    QuestionId.FLAG_PALPITATIONS,
    QuestionId.RECENCY_PALPITATIONS,
    QuestionId.FLAG_RECENT_SURGERY,
    QuestionId.FLAG_NIGHT_PAIN,
    QuestionId.FLAG_RADIATING_NUMBNESS,
    QuestionId.RECENCY_RADIATING_NUMBNESS,
    QuestionId.FLAG_PREGNANCY_COMPLICATION,
    QuestionId.FLAG_UNCONTROLLED_CONDITION,
    QuestionId.FLAG_NEW_SWELLING_OR_PAIN,
    QuestionId.RECENCY_NEW_SWELLING_OR_PAIN,
    QuestionId.DECLARED_CONDITIONS,
    QuestionId.PAIN,
)

internal val FLAG_QUESTIONS: Map<RedFlag, QuestionId> = mapOf(
    RedFlag.CHEST_PAIN to QuestionId.FLAG_CHEST_PAIN,
    RedFlag.BREATHLESSNESS_AT_REST_OR_LIGHT_EXERTION to QuestionId.FLAG_BREATHLESSNESS,
    RedFlag.DIZZINESS_OR_FAINTING to QuestionId.FLAG_DIZZINESS,
    RedFlag.PALPITATIONS_OR_IRREGULAR_HEARTBEAT to QuestionId.FLAG_PALPITATIONS,
    RedFlag.RECENT_SURGERY to QuestionId.FLAG_RECENT_SURGERY,
    RedFlag.UNEXPLAINED_NIGHT_PAIN to QuestionId.FLAG_NIGHT_PAIN,
    RedFlag.RADIATING_NUMBNESS_OR_WEAKNESS to QuestionId.FLAG_RADIATING_NUMBNESS,
    RedFlag.PREGNANCY_COMPLICATION to QuestionId.FLAG_PREGNANCY_COMPLICATION,
    RedFlag.UNCONTROLLED_DIAGNOSED_CONDITION to QuestionId.FLAG_UNCONTROLLED_CONDITION,
    RedFlag.NEW_UNEXPLAINED_SWELLING_OR_PAIN to QuestionId.FLAG_NEW_SWELLING_OR_PAIN,
)

/** Acute symptoms get a recency follow-up. Urgency is the user's stated recency. */
val ACUTE_SYMPTOM_FLAGS: Set<RedFlag> = setOf(
    RedFlag.CHEST_PAIN,
    RedFlag.BREATHLESSNESS_AT_REST_OR_LIGHT_EXERTION,
    RedFlag.DIZZINESS_OR_FAINTING,
    RedFlag.PALPITATIONS_OR_IRREGULAR_HEARTBEAT,
    RedFlag.RADIATING_NUMBNESS_OR_WEAKNESS,
    RedFlag.NEW_UNEXPLAINED_SWELLING_OR_PAIN,
)

val RECENCY_QUESTIONS: Map<RedFlag, QuestionId> = mapOf(
    RedFlag.CHEST_PAIN to QuestionId.RECENCY_CHEST_PAIN,
    RedFlag.BREATHLESSNESS_AT_REST_OR_LIGHT_EXERTION to QuestionId.RECENCY_BREATHLESSNESS,
    RedFlag.DIZZINESS_OR_FAINTING to QuestionId.RECENCY_DIZZINESS,
    RedFlag.PALPITATIONS_OR_IRREGULAR_HEARTBEAT to QuestionId.RECENCY_PALPITATIONS,
    RedFlag.RADIATING_NUMBNESS_OR_WEAKNESS to QuestionId.RECENCY_RADIATING_NUMBNESS,
    RedFlag.NEW_UNEXPLAINED_SWELLING_OR_PAIN to QuestionId.RECENCY_NEW_SWELLING_OR_PAIN,
)
