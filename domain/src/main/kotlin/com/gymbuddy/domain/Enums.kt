package com.gymbuddy.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class EventSource {
    @SerialName("voice") VOICE,
    @SerialName("manual") MANUAL,
    @SerialName("imported") IMPORTED,
    @SerialName("ai-generated") AI_GENERATED,
    @SerialName("system") SYSTEM,
}

@Serializable
enum class WeightUnit {
    @SerialName("kg") KG,
    @SerialName("lb") LB,
}

@Serializable
enum class LoggingMode {
    @SerialName("live") LIVE,
    @SerialName("catch_up") CATCH_UP,
}

@Serializable
enum class CandidateType {
    SET_COMPLETED,
    SET_CORRECTED,
    SET_DELETED,
    WEIGHT_CHANGED,
    REPS_CHANGED,
    EXERCISE_CHANGED,
    SUBSTITUTION_PROPOSE,
    USER_NOTE,
    USER_FEEDBACK,
    USER_QUESTION,
    COMMAND,
    SAFETY,
}

@Serializable
enum class CommittedEventType {
    SESSION_INITIALIZED,
    WORKOUT_STARTED,
    SESSION_REHYDRATED,
    EXERCISE_STARTED,
    SET_STARTED,
    SET_COMPLETED,
    SET_CORRECTED,
    SET_DELETED,
    WEIGHT_CHANGED,
    REPS_CHANGED,
    EXERCISE_CHANGED,
    EXERCISE_SKIPPED,
    EXERCISE_DEFERRED,
    PLANNED_SETS_CHANGED,
    REST_STARTED,
    REST_ENDED,
    WORKOUT_PAUSED,
    WORKOUT_RESUMED,
    WORKOUT_COMPLETED,
    USER_NOTE_CREATED,
    USER_FEEDBACK,
    USER_QUESTION,
    COACHING_RESPONSE,
    SAFETY_CONCERN,
    CLARIFICATION_REQUESTED,
    INTENT_REJECTED,
    SUBSTITUTION_APPLIED,
    LANGUAGE_DETECTED,
    BARGE_IN,
    TURN_ENDED,
    AUDIO_FOCUS_CHANGED,
    MEMORY_UPDATED,
    SYNC_ACKNOWLEDGED,
}

@Serializable
enum class WorkoutSessionState {
    IDLE,
    WORKOUT_STARTING,
    READY_FOR_EXERCISE,
    EXERCISE_ACTIVE,
    SET_ACTIVE,
    SET_COMPLETED,
    RESTING,
    PAUSED,
    EXERCISE_COMPLETE,
    WORKOUT_COMPLETE,
    ERROR_RECOVERY,
}

@Serializable
enum class VoiceTurnState {
    /** Idle / no active turn. Spec name for the user's "IDLE". */
    SILENT,
    /** VAD armed, awaiting speech. Mic is on only while a conversation window is open. */
    LISTENING,
    USER_SPEAKING,
    PROCESSING,
    AI_SPEAKING,
    /** Transient barge-in; next state is USER_SPEAKING. */
    INTERRUPTED,
    /** Voice session stopped. Orthogonal to workout STOP / IDLE. */
    STOPPED,
}

@Serializable
enum class EpistemicStance {
    @SerialName("known") KNOWN,
    @SerialName("estimated") ESTIMATED,
    @SerialName("inferred") INFERRED,
    @SerialName("uncertain") UNCERTAIN,
    @SerialName("none") NONE,
}

@Serializable
enum class CommandKind {
    @SerialName("pause") PAUSE,
    @SerialName("resume") RESUME,
    @SerialName("skip") SKIP,
    @SerialName("extra_set") EXTRA_SET,
    @SerialName("end_workout") END_WORKOUT,
}

@Serializable
enum class FeedbackKind {
    @SerialName("easy") EASY,
    @SerialName("hard") HARD,
    @SerialName("dislike_exercise") DISLIKE_EXERCISE,
    @SerialName("fatigue") FATIGUE,
    @SerialName("shoulder_discomfort") SHOULDER_DISCOMFORT,
    @SerialName("other") OTHER,
}

@Serializable
enum class QuestionTopic {
    @SerialName("technique") TECHNIQUE,
    @SerialName("history") HISTORY,
    @SerialName("plan") PLAN,
    @SerialName("nutrition") NUTRITION,
    @SerialName("recovery") RECOVERY,
    @SerialName("other") OTHER,
}

@Serializable
enum class SafetySeverity {
    @SerialName("low") LOW,
    @SerialName("medium") MEDIUM,
    @SerialName("high") HIGH,
    @SerialName("critical") CRITICAL,
}

@Serializable
enum class SafetyAction {
    @SerialName("advise") ADVISE,
    @SerialName("pause") PAUSE,
    @SerialName("stop_recommend") STOP_RECOMMEND,
}

@Serializable
enum class AudioRoute {
    @SerialName("headset") HEADSET,
    @SerialName("speakerphone") SPEAKERPHONE,
}

/** Events the LLM may propose as CandidateEvent.type. Not the committed catalog. */
fun CandidateType.mayBeProposedByLlm(): Boolean = this != CandidateType.SAFETY

/**
 * Events the LLM must never emit as committed truth.
 * Engine / safety / voice layer produce these.
 */
fun CommittedEventType.llmMayEmitAsCommittedTruth(): Boolean = when (this) {
    CommittedEventType.SET_COMPLETED,
    CommittedEventType.SET_CORRECTED,
    CommittedEventType.SET_DELETED,
    CommittedEventType.WEIGHT_CHANGED,
    CommittedEventType.REPS_CHANGED,
    CommittedEventType.USER_NOTE_CREATED,
    CommittedEventType.USER_FEEDBACK,
    CommittedEventType.USER_QUESTION,
    CommittedEventType.SUBSTITUTION_APPLIED,
    CommittedEventType.EXERCISE_CHANGED,
    -> false // extractor proposes candidates; engine commits after validation
    else -> false
}
