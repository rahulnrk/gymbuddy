package com.gymbuddy.domain.pain

import com.gymbuddy.domain.adapt.Adjustment
import com.gymbuddy.domain.adapt.DrainOn
import com.gymbuddy.domain.adapt.SlotIndex
import com.gymbuddy.domain.intake.PainCharacter
import com.gymbuddy.domain.intake.RedFlag
import com.gymbuddy.domain.picker.ScoredExercise
import com.gymbuddy.domain.plan.PlannedSession

enum class PainLocationKind {
    CHEST,
    HEAD,
    SPINE,
    JOINT,
    MUSCLE,
    UNKNOWN,
}

enum class PainQuestionId {
    SPOT,
    ONSET,
    SIDES,
    CHARACTER,
    TRAVEL,
    AT_REST,
}

enum class SpotAnswer { ONE_SPOT, SPREAD }
enum class OnsetAnswer { SUDDEN, BUILT_UP }
enum class SidesAnswer { BOTH, ONE }
enum class TravelAnswer { NO, TRAVELS, PINS, WEAKNESS }
enum class AtRestAnswer { WORSE_WITH_MOVE, ACHES_AT_REST }

/**
 * Answers so far. Null is unanswered. [intensity] is stored, never routed on.
 */
data class PainInterview(
    val intensity: Int? = null,
    val where: String = "",
    val spot: SpotAnswer? = null,
    val onset: OnsetAnswer? = null,
    val sides: SidesAnswer? = null,
    val character: PainCharacter? = null,
    val travel: TravelAnswer? = null,
    val atRest: AtRestAnswer? = null,
    val nightWakes: Boolean? = null,
    val urgent: RedFlag? = null,
    val currentSlug: String? = null,
    val currentName: String? = null,
    val currentPattern: String? = null,
    val slot: SlotIndex? = null,
)

sealed interface PainOutcome {
    data class LikelyTrainingSoreness(val why: String) : PainOutcome
    data class StopThisMovement(val slug: String, val why: String) : PainOutcome
    data class ReferBeforeContinuing(val why: String, val mayTrainOtherAreas: Boolean) : PainOutcome
    data class UrgentReferral(val flags: List<RedFlag>) : PainOutcome
    data class NeedMore(val nextQuestion: PainQuestionId) : PainOutcome
}

data class PainProposal(
    val outcome: PainOutcome,
    val spoken: String,
    val drainOn: DrainOn = DrainOn.RESTING,
    val adjustment: Adjustment? = null,
    val sibling: ScoredExercise? = null,
    val excludeSlugs: Set<String> = emptySet(),
    val reducedSession: PlannedSession? = null,
    val intensityRecorded: Int? = null,
    val record: SessionPainRecord? = null,
)

/**
 * What next session's readiness check reads. Not a diagnosis.
 */
data class SessionPainRecord(
    val where: String,
    val intensity: Int?,
    val stoppedSlug: String?,
    val kind: String,
)
