package com.gymbuddy.domain.validation

import com.gymbuddy.domain.AudioRoute
import com.gymbuddy.domain.CandidateType
import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.SetId
import com.gymbuddy.domain.WeightUnit
import com.gymbuddy.domain.context.CompletedSetSnapshot
import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.events.CandidateEvent
import com.gymbuddy.domain.events.CommandCandidate
import com.gymbuddy.domain.events.SetCompletedCandidate
import com.gymbuddy.domain.events.SetCorrectedCandidate
import com.gymbuddy.domain.events.SetDeletedCandidate
import com.gymbuddy.domain.events.SubstitutionProposeCandidate
import com.gymbuddy.domain.events.UserFeedbackCandidate
import com.gymbuddy.domain.events.UserNoteCandidate
import com.gymbuddy.domain.events.UserQuestionCandidate
import com.gymbuddy.domain.events.WeightChangedCandidate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * PR 1 gate. Pure. No EventLog, no Room, no vendors, no WorkoutSessionState writes.
 */
class DefaultCandidateValidator : CandidateValidator {

    override fun validate(
        candidate: CandidateEvent,
        snapshot: FourContexts,
        route: VoiceCommitRoute,
    ): ValidationDecision = validateOne(candidate, snapshot, route, turnWorkingKg = snapshot.workout.currentWeightKg)

    override fun validateTurn(
        candidates: List<CandidateEvent>,
        snapshot: FourContexts,
        route: VoiceCommitRoute,
    ): ValidationDecision {
        if (candidates.isEmpty()) return ValidationDecision.Drop("empty_turn")
        if (candidates.size == 1) return validate(candidates.single(), snapshot, route)
        var working = snapshot.workout.currentWeightKg
        val facts = mutableListOf<ValidatedFact>()
        val batchId = UUID.randomUUID().toString()
        val catchUp = candidates.size >= 2 ||
            candidates.any { it.loggingMode == LoggingMode.CATCH_UP }
        for (c in candidates) {
            when (val d = validateOne(c, snapshot, route, turnWorkingKg = working)) {
                is ValidationDecision.Accept -> {
                    val mapped = d.facts.map { fact ->
                        if (fact is ValidatedFact.SetCompleted && catchUp) {
                            fact.copy(
                                loggingMode = LoggingMode.CATCH_UP,
                                batchId = fact.batchId ?: batchId,
                            )
                        } else fact
                    }
                    facts += mapped
                    mapped.filterIsInstance<ValidatedFact.SetCompleted>()
                        .lastOrNull()?.weightKg?.let { working = it }
                }
                else -> return d
            }
        }
        return ValidationDecision.Accept(facts, EventSource.VOICE)
    }

    private fun validateOne(
        candidate: CandidateEvent,
        snapshot: FourContexts,
        route: VoiceCommitRoute,
        turnWorkingKg: Double?,
    ): ValidationDecision {
        val payload = try {
            candidate.decodePayload()
        } catch (_: Exception) {
            return ValidationDecision.Drop("payload_decode_failed")
        }
        if (isDuplicate(route, snapshot)) {
            return ValidationDecision.Drop("duplicate_stt_final")
        }
        return when (candidate.type) {
            CandidateType.SET_COMPLETED ->
                validateSetCompleted(candidate, payload as SetCompletedCandidate, snapshot, route, turnWorkingKg)
            CandidateType.SET_CORRECTED ->
                validateSetCorrected(candidate, payload as SetCorrectedCandidate, snapshot)
            CandidateType.SET_DELETED ->
                validateSetDeleted(payload as SetDeletedCandidate, snapshot)
            CandidateType.WEIGHT_CHANGED ->
                validateWeightChanged(payload as WeightChangedCandidate, snapshot)
            CandidateType.USER_QUESTION ->
                accept(ValidatedFact.UserQuestion(
                    (payload as UserQuestionCandidate).questionOriginal,
                    payload.topic,
                ))
            CandidateType.USER_FEEDBACK -> {
                val p = payload as UserFeedbackCandidate
                accept(ValidatedFact.UserFeedback(p.kind, p.intensity))
            }
            CandidateType.USER_NOTE -> {
                val p = payload as UserNoteCandidate
                accept(ValidatedFact.UserNote(p.textOriginal, p.scope))
            }
            CandidateType.COMMAND ->
                accept(ValidatedFact.Command((payload as CommandCandidate).kind))
            CandidateType.SUBSTITUTION_PROPOSE -> {
                val p = payload as SubstitutionProposeCandidate
                accept(ValidatedFact.SubstitutionPropose(p.toExerciseNameRaw, p.toExerciseId, p.fromExerciseId))
            }
            CandidateType.SAFETY ->
                ValidationDecision.Drop("safety_owned_by_safety_layer")
            CandidateType.EXERCISE_CHANGED ->
                ValidationDecision.Reject("exercise_changed_is_planning_mode")
            CandidateType.REPS_CHANGED ->
                ValidationDecision.Reject("reps_changed_not_a_completed_set")
        }
    }

    private fun validateSetCompleted(
        candidate: CandidateEvent,
        payload: SetCompletedCandidate,
        snapshot: FourContexts,
        route: VoiceCommitRoute,
        turnWorkingKg: Double?,
    ): ValidationDecision {
        if (!voiceSetAutoCommitAllowed(route)) {
            return ValidationDecision.Drop("voice_route_no_auto_commit")
        }
        val spokenKg = payload.weight?.let { toKg(it, payload.weightUnit, snapshot) }
        val working = turnWorkingKg
            ?: snapshot.workout.currentWeightKg
            ?: snapshot.workout.completedSets.lastOrNull()?.weightKg
        val inherit = spokenKg == null && working != null
        val weightKg = spokenKg ?: working
        val reps = payload.reps
        if (reps == null && spokenKg != null) {
            return ValidationDecision.Clarify("Was that a weight change or a set? How many reps?")
        }
        if (reps == null) {
            return ValidationDecision.Clarify("How many reps?")
        }
        if (weightKg == null) {
            return ValidationDecision.Clarify("What weight was that?")
        }
        val inheritPath = inherit
        if (!inheritPath && candidate.overallConfidence < CONFIDENCE_FLOOR) {
            return ValidationDecision.Clarify("I didn't catch that. How many reps?")
        }
        if (!inherit && spokenKg != null && working != null) {
            val delta = kotlin.math.abs(spokenKg - working) / working
            if (delta > 0.20) {
                return ValidationDecision.Clarify("Was that $spokenKg or $working?")
            }
        }
        return accept(
            ValidatedFact.SetCompleted(
                reps = reps,
                weightKg = weightKg,
                inheritWeight = inherit,
                loggingMode = candidate.loggingMode ?: LoggingMode.LIVE,
                setNumberHint = parseHint(candidate.setNumberHint),
                batchId = null,
                rpe = payload.rpe,
                rir = payload.rir,
                effortNote = payload.effortNote,
                exerciseIdFromContext = snapshot.workout.currentExerciseId,
            ),
        )
    }

    private fun validateSetCorrected(
        candidate: CandidateEvent,
        payload: SetCorrectedCandidate,
        snapshot: FourContexts,
    ): ValidationDecision {
        val hint = parseHint(candidate.setNumberHint)
        val token = payload.target?.lowercase()?.trim()
        val target = when (val r = resolveCorrectionTarget(token, hint, snapshot)) {
            is TargetHit.Found -> r.set
            is TargetHit.AmbiguousExercise ->
                return ValidationDecision.Clarify("Was that on bench or incline?")
            TargetHit.None ->
                return ValidationDecision.Clarify("I don't have a set to change yet.")
        }
        val laterExists = snapshot.workout.completedSets.any { it.setNumber > target.setNumber }
        val targetingNamedSlot = token in NAMED_SLOTS || hint is SetNumberHint.Ordinal && hint.value != HintOrdinal.LAST && hint.value != HintOrdinal.THIS
            || hint is SetNumberHint.Index
        if (laterExists && targetingNamedSlot) {
            return ValidationDecision.Clarify(
                "Should I replace set ${target.setNumber} or keep both?",
                options = listOf("replace", "keep both"),
            )
        }
        val id = SetId(target.setId)
        val newKg = payload.weight?.let { toKg(it, payload.weightUnit, snapshot) }
        return accept(
            ValidatedFact.SetCorrected(
                setId = id,
                fields = CorrectionFields(reps = payload.reps, weightKg = newKg, weightUnit = payload.weightUnit),
                previous = PreviousSetSnapshot(
                    setId = id,
                    reps = target.reps,
                    weightKg = target.weightKg,
                    alreadyAssignedSetNumber = target.setNumber,
                ),
            ),
        )
    }

    private fun validateSetDeleted(
        payload: SetDeletedCandidate,
        snapshot: FourContexts,
    ): ValidationDecision {
        val target = snapshot.workout.completedSets.maxByOrNull { it.setNumber }
            ?: return ValidationDecision.Clarify("I don't have a set to change yet.")
        val id = SetId(target.setId)
        return accept(
            ValidatedFact.SetDeleted(
                setId = id,
                previous = PreviousSetSnapshot(id, target.reps, target.weightKg, target.setNumber),
                reason = payload.reason,
            ),
        )
    }

    private fun validateWeightChanged(
        payload: WeightChangedCandidate,
        snapshot: FourContexts,
    ): ValidationDecision {
        val kg = toKg(payload.weight, payload.weightUnit, snapshot)
        return accept(ValidatedFact.WeightChanged(kg, payload.weightUnit ?: snapshot.user.units))
    }

    private sealed class TargetHit {
        data class Found(val set: CompletedSetSnapshot) : TargetHit()
        data object AmbiguousExercise : TargetHit()
        data object None : TargetHit()
    }

    private fun resolveCorrectionTarget(
        token: String?,
        hint: SetNumberHint?,
        snapshot: FourContexts,
    ): TargetHit {
        val current = snapshot.workout.completedSets
        val slot = slotNumber(token, hint)
        if (slot != null) {
            current.find { it.setNumber == slot }?.let { return TargetHit.Found(it) }
        }
        if (current.isNotEmpty()) {
            if (token in listOf(null, "last", "this") ||
                (hint is SetNumberHint.Ordinal && hint.value in listOf(HintOrdinal.LAST, HintOrdinal.THIS)) ||
                (token == null && hint == null)
            ) {
                return TargetHit.Found(current.maxBy { it.setNumber })
            }
            if (slot == null && token in listOf("last", "this")) {
                return TargetHit.Found(current.maxBy { it.setNumber })
            }
        }
        if (current.isEmpty()) {
            val prev = snapshot.workout.previousExerciseLastSet ?: return TargetHit.None
            val within = within120s(prev.occurredAt, snapshot.workout.currentExerciseStartedAt)
            val topic = snapshot.conversational.currentTopic?.lowercase()
            val currentName = snapshot.workout.currentExerciseName?.lowercase().orEmpty()
            val topicStillPrevious = !topic.isNullOrBlank() && topic !in currentName
            return if (within || topicStillPrevious) TargetHit.Found(prev) else TargetHit.AmbiguousExercise
        }
        return TargetHit.None
    }

    private fun within120s(occurredAt: String?, currentStartedAt: String?): Boolean {
        if (occurredAt == null || currentStartedAt == null) return false
        return try {
            val a = Instant.parse(occurredAt)
            val b = Instant.parse(currentStartedAt)
            Duration.between(a, b).abs().seconds <= 120
        } catch (_: Exception) {
            false
        }
    }

    private fun slotNumber(token: String?, hint: SetNumberHint?): Int? {
        when (token) {
            "first" -> return 1
            "second" -> return 2
            "third" -> return 3
        }
        token?.toIntOrNull()?.let { return it }
        return when (hint) {
            is SetNumberHint.Index -> hint.value
            is SetNumberHint.Ordinal -> when (hint.value) {
                HintOrdinal.FIRST -> 1
                HintOrdinal.SECOND -> 2
                HintOrdinal.THIRD -> 3
                HintOrdinal.LAST, HintOrdinal.THIS -> null
            }
            null -> null
        }
    }

    private fun parseHint(el: JsonElement?): SetNumberHint? {
        val p = el as? JsonPrimitive ?: return null
        p.intOrNull?.let { if (it >= 1) return SetNumberHint.Index(it) }
        return when (p.content.lowercase()) {
            "first" -> SetNumberHint.Ordinal(HintOrdinal.FIRST)
            "second" -> SetNumberHint.Ordinal(HintOrdinal.SECOND)
            "third" -> SetNumberHint.Ordinal(HintOrdinal.THIRD)
            "last" -> SetNumberHint.Ordinal(HintOrdinal.LAST)
            "this" -> SetNumberHint.Ordinal(HintOrdinal.THIS)
            else -> p.content.toIntOrNull()?.takeIf { it >= 1 }?.let { SetNumberHint.Index(it) }
        }
    }

    private fun voiceSetAutoCommitAllowed(route: VoiceCommitRoute): Boolean {
        return route.audioRoute == AudioRoute.HEADSET &&
            route.talkerCount == 1 &&
            !route.overlappingVad &&
            route.sttDurationMs >= 250.0
    }

    private fun isDuplicate(route: VoiceCommitRoute, snapshot: FourContexts): Boolean {
        val hash = route.sttFinalHash ?: return false
        val t = route.recordedAtEpochMs
        return snapshot.conversational.recentSttFinals.any {
            it.hash == hash && kotlin.math.abs(it.recordedAtEpochMs - t) < DUPLICATE_WINDOW_MS
        }
    }

    private fun toKg(weight: Double, unit: WeightUnit?, snapshot: FourContexts): Double {
        val u = unit ?: snapshot.user.units
        return if (u == WeightUnit.LB) weight * LB_TO_KG else weight
    }

    private fun accept(fact: ValidatedFact) =
        ValidationDecision.Accept(listOf(fact), EventSource.VOICE)

    companion object {
        const val CONFIDENCE_FLOOR = 0.55
        const val DUPLICATE_WINDOW_MS = 10_000L
        const val LB_TO_KG = 0.45359237
        val NAMED_SLOTS = setOf("first", "second", "third")
    }
}
