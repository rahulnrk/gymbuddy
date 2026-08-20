package com.gymbuddy.domain.loop

import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.SafetySeverity
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.context.EpistemicFact
import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
import com.gymbuddy.domain.events.WorkoutEvent
import com.gymbuddy.domain.safety.SafetyResult

enum class SpeechActId {
    SESSION_GREETING,
    SESSION_RESUME,
    SESSION_COMPLETE,
    PAUSE_ACK,
    CORRECTION_ACK,
    USEFUL_DELTA,
    FIRST_SET_OF_EXERCISE,
    WEIGHT_CHANGE_ACK,
    SUBSTITUTION_PROPOSE,
    SUBSTITUTION_APPLIED,
    CLARIFICATION,
    ANSWER_QUESTION,
    SAFETY,
    OFFLINE_VOICE_UNAVAILABLE,
    SUGGEST_WEIGHT_THIS_SESSION,
    SKIP_ACK,
    EXTRA_SET_ACK,
}

enum class SilenceReason {
    SET_ACTIVE,
    NO_USEFUL_DELTA,
    ACK_ONLY,
    SYSTEM_EVENT,
    POLICY_SILENT_ACT,
}

sealed class SpeakDecision {
    open val queued: List<SpeechActId> get() = emptyList()

    data class Speak(
        val actId: SpeechActId,
        val templateId: String,
        val facts: List<EpistemicFact> = emptyList(),
    ) : SpeakDecision()

    data class Silence(
        val reason: SilenceReason,
        override val queued: List<SpeechActId> = emptyList(),
    ) : SpeakDecision()

    val speak: Boolean get() = this is Speak
}

/**
 * Deterministic speak gate. No TTS, no I/O, no workout mutation.
 * The LLM does not decide whether to speak.
 */
object ShouldSpeak {
    fun shouldSpeak(
        contexts: FourContexts,
        snapshot: WorkoutSessionSnapshot,
        committedThisTurn: List<WorkoutEvent>,
        safety: SafetyResult,
        questionThisTurn: Boolean,
        clarificationThisTurn: Boolean,
        recommendation: Recommendation?,
        userAskedAdvice: Boolean = false,
    ): SpeakDecision = decide(
        contexts, snapshot, committedThisTurn, safety, questionThisTurn, clarificationThisTurn,
        recommendation, userAskedAdvice,
    )

    fun decide(
        contexts: FourContexts,
        snapshot: WorkoutSessionSnapshot,
        committedThisTurn: List<WorkoutEvent>,
        safety: SafetyResult,
        questionThisTurn: Boolean,
        clarificationThisTurn: Boolean,
        recommendation: Recommendation?,
        userAskedAdvice: Boolean = false,
    ): SpeakDecision {
        val types = committedThisTurn.map { it.type }.toSet()
        val must = mustSpeakAct(
            snapshot, types, safety, questionThisTurn, clarificationThisTurn, recommendation, userAskedAdvice,
        )
        val high = safety.severity == SafetySeverity.HIGH || safety.severity == SafetySeverity.CRITICAL
        if (high) {
            return SpeakDecision.Speak(SpeechActId.SAFETY, "safety.critical", emptyList())
        }
        // Application policy, not the LLM. SET_ACTIVE wins over every non-safety MUST.
        if (contexts.workout.sessionState == WorkoutSessionState.SET_ACTIVE ||
            snapshot.phase == WorkoutSessionState.SET_ACTIVE
        ) {
            val queued = must?.let { listOf(it) } ?: emptyList()
            return SpeakDecision.Silence(SilenceReason.SET_ACTIVE, queued)
        }
        if (must != null) {
            return SpeakDecision.Speak(must, templateId(must), emptyList())
        }
        if (sameRepsSameWeight(snapshot, types)) {
            return SpeakDecision.Silence(SilenceReason.NO_USEFUL_DELTA)
        }
        if (types.isEmpty() && !questionThisTurn && recommendation == null) {
            return SpeakDecision.Silence(SilenceReason.ACK_ONLY)
        }
        if (recommendation != null && !userAskedAdvice && !usefulDelta(snapshot, types)) {
            return SpeakDecision.Silence(SilenceReason.POLICY_SILENT_ACT)
        }
        if (types.all { it in SYSTEM_ONLY } && types.isNotEmpty()) {
            return SpeakDecision.Silence(SilenceReason.SYSTEM_EVENT)
        }
        return SpeakDecision.Silence(SilenceReason.NO_USEFUL_DELTA)
    }

    private fun mustSpeakAct(
        snapshot: WorkoutSessionSnapshot,
        types: Set<CommittedEventType>,
        safety: SafetyResult,
        question: Boolean,
        clarification: Boolean,
        recommendation: Recommendation?,
        userAskedAdvice: Boolean,
    ): SpeechActId? {
        if (safety.fired && (safety.severity == SafetySeverity.MEDIUM || safety.severity == SafetySeverity.LOW)) {
            return SpeechActId.SAFETY
        }
        if (question) return SpeechActId.ANSWER_QUESTION
        if (clarification) return SpeechActId.CLARIFICATION
        if (CommittedEventType.SET_CORRECTED in types || CommittedEventType.SET_DELETED in types) {
            return SpeechActId.CORRECTION_ACK
        }
        if (CommittedEventType.WORKOUT_STARTED in types) return SpeechActId.SESSION_GREETING
        if (CommittedEventType.WORKOUT_RESUMED in types) return SpeechActId.SESSION_RESUME
        if (CommittedEventType.WORKOUT_COMPLETED in types) return SpeechActId.SESSION_COMPLETE
        if (CommittedEventType.WORKOUT_PAUSED in types) return SpeechActId.PAUSE_ACK
        if (CommittedEventType.EXERCISE_SKIPPED in types) return SpeechActId.SKIP_ACK
        if (CommittedEventType.PLANNED_SETS_CHANGED in types) return SpeechActId.EXTRA_SET_ACK
        if (CommittedEventType.SUBSTITUTION_APPLIED in types) return SpeechActId.SUBSTITUTION_APPLIED
        if (CommittedEventType.WEIGHT_CHANGED in types) return SpeechActId.WEIGHT_CHANGE_ACK
        if (firstSetThisExercise(snapshot, types)) return SpeechActId.FIRST_SET_OF_EXERCISE
        if (usefulDelta(snapshot, types)) return SpeechActId.USEFUL_DELTA
        if (userAskedAdvice && recommendation != null) return SpeechActId.SUGGEST_WEIGHT_THIS_SESSION
        return null
    }

    private fun firstSetThisExercise(snapshot: WorkoutSessionSnapshot, types: Set<CommittedEventType>): Boolean =
        CommittedEventType.SET_COMPLETED in types && snapshot.liveSetsOnCurrent().size == 1

    private fun usefulDelta(snapshot: WorkoutSessionSnapshot, types: Set<CommittedEventType>): Boolean {
        if (CommittedEventType.SET_COMPLETED !in types) return false
        val live = snapshot.liveSetsOnCurrent()
        if (live.size < 2) return false
        val prev = live[live.lastIndex - 1]
        val curr = live.last()
        val dReps = kotlin.math.abs((curr.reps ?: 0) - (prev.reps ?: 0))
        val dW = kotlin.math.abs((curr.weightKg ?: 0.0) - (prev.weightKg ?: 0.0))
        return dReps >= 1 || dW >= 0.5
    }

    private fun sameRepsSameWeight(snapshot: WorkoutSessionSnapshot, types: Set<CommittedEventType>): Boolean {
        if (CommittedEventType.SET_COMPLETED !in types) return false
        val live = snapshot.liveSetsOnCurrent()
        if (live.size < 2) return false
        val prev = live[live.lastIndex - 1]
        val curr = live.last()
        return prev.reps == curr.reps && prev.weightKg == curr.weightKg
    }

    private fun templateId(act: SpeechActId): String = "speech.${act.name.lowercase()}"

    private val SYSTEM_ONLY = setOf(
        CommittedEventType.SYNC_ACKNOWLEDGED,
        CommittedEventType.AUDIO_FOCUS_CHANGED,
        CommittedEventType.LANGUAGE_DETECTED,
        CommittedEventType.TURN_ENDED,
        CommittedEventType.BARGE_IN,
    )
}
