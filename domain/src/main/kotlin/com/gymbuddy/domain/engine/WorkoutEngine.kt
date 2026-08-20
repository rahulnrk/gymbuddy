package com.gymbuddy.domain.engine

import com.gymbuddy.domain.CommandKind
import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.SetId
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.events.WorkoutEvent
import com.gymbuddy.domain.persist.AppendAck
import com.gymbuddy.domain.persist.EventLog
import com.gymbuddy.domain.state.WorkoutTransitionContract
import com.gymbuddy.domain.validation.HintOrdinal
import com.gymbuddy.domain.validation.SetNumberHint
import com.gymbuddy.domain.validation.ValidatedFact
import java.util.UUID

sealed class EngineResult {
    data class Committed(
        val events: List<WorkoutEvent>,
        val snapshot: WorkoutSessionSnapshot,
    ) : EngineResult()

    data class Rejected(val reason: String) : EngineResult()
}

/**
 * Decides legal WorkoutEvents from session commands and ValidatedFacts.
 * Does not call CandidateValidator.validateTurn. Does not persist Room.
 */
class WorkoutEngine(
    private val log: EventLog,
    private val now: () -> String = { "2026-08-20T16:00:00Z" },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun snapshot(sessionId: String, plan: WorkoutPlan? = null): WorkoutSessionSnapshot {
        val events = log.sessionEvents(com.gymbuddy.domain.SessionId(sessionId))
        return WorkoutProjector.fold(events, plan = plan)
    }

    fun startSession(
        userId: String,
        sessionId: String,
        plan: WorkoutPlan,
    ): EngineResult {
        val current = snapshot(sessionId, plan)
        if (current.phase != WorkoutSessionState.IDLE && current.phase != WorkoutSessionState.WORKOUT_COMPLETE) {
            if (current.sessionId == sessionId) return EngineResult.Committed(emptyList(), current)
            return reject("session already active")
        }
        val init = EventFactory.event(
            CommittedEventType.SESSION_INITIALIZED, userId, sessionId,
            EventFactory.payload("sessionId" to sessionId, "workoutId" to plan.workoutId),
            EventSource.SYSTEM, now(),
        )
        val started = EventFactory.event(
            CommittedEventType.WORKOUT_STARTED, userId, sessionId,
            EventFactory.payload("workoutId" to plan.workoutId, "startedAt" to now()),
            EventSource.SYSTEM, now(),
        )
        val first = plan.exercises.first()
        val ex = EventFactory.event(
            CommittedEventType.EXERCISE_STARTED, userId, sessionId,
            EventFactory.payload(
                "workoutExerciseId" to first.workoutExerciseId,
                "exerciseId" to first.exerciseId,
                "plannedSets" to first.plannedSets,
                "restTargetSec" to first.restTargetSec,
            ),
            EventSource.SYSTEM, now(),
        )
        return commit(sessionId, plan, listOf(init, started, ex))
    }

    fun failHydrate(userId: String, sessionId: String, plan: WorkoutPlan): EngineResult {
        val current = snapshot(sessionId, plan)
        if (current.phase != WorkoutSessionState.WORKOUT_STARTING) {
            val init = EventFactory.event(
                CommittedEventType.SESSION_INITIALIZED, userId, sessionId,
                EventFactory.payload("sessionId" to sessionId, "workoutId" to plan.workoutId),
                EventSource.SYSTEM, now(),
            )
            val fail = EventFactory.event(
                CommittedEventType.INTENT_REJECTED, userId, sessionId,
                EventFactory.payload("reason" to "hydrate_fail", "candidateType" to "SESSION"),
                EventSource.SYSTEM, now(),
            )
            return commit(sessionId, plan, listOf(init, fail))
        }
        val fail = EventFactory.event(
            CommittedEventType.INTENT_REJECTED, userId, sessionId,
            EventFactory.payload("reason" to "hydrate_fail", "candidateType" to "SESSION"),
            EventSource.SYSTEM, now(),
        )
        return commit(sessionId, plan, listOf(fail))
    }

    fun recover(sessionId: String, plan: WorkoutPlan): EngineResult {
        val events = log.sessionEvents(com.gymbuddy.domain.SessionId(sessionId))
        val folded = WorkoutProjector.fold(events, plan = plan)
        if (folded.phase == WorkoutSessionState.IDLE) {
            return EngineResult.Rejected("nothing to recover")
        }
        val userId = folded.userId ?: return EngineResult.Rejected("no user")
        if (events.any { it.type == CommittedEventType.SESSION_REHYDRATED }) {
            return EngineResult.Committed(emptyList(), folded)
        }
        val marker = EventFactory.event(
            CommittedEventType.SESSION_REHYDRATED, userId, sessionId,
            EventFactory.payload("sessionId" to sessionId),
            EventSource.SYSTEM, now(),
        )
        return commit(sessionId, plan, listOf(marker))
    }

    fun beginSet(sessionId: String, plan: WorkoutPlan): EngineResult {
        val s = snapshot(sessionId, plan)
        if (s.phase != WorkoutSessionState.EXERCISE_ACTIVE && s.phase != WorkoutSessionState.RESTING) {
            return reject("cannot SET_STARTED from ${s.phase}")
        }
        val we = s.currentExercise ?: return reject("no exercise")
        val ev = EventFactory.event(
            CommittedEventType.SET_STARTED, s.userId ?: return reject("no user"), s.sessionId,
            EventFactory.payload("workoutExerciseId" to we.workoutExerciseId, "setNumber" to s.nextOfficialSetNumber()),
            EventSource.SYSTEM, now(),
        )
        return commit(sessionId, plan, listOf(ev))
    }

    fun endRest(sessionId: String, plan: WorkoutPlan, reason: String = "timer"): EngineResult {
        val s = snapshot(sessionId, plan)
        val we = s.currentExercise ?: return reject("no exercise")
        val ev = EventFactory.event(
            CommittedEventType.REST_ENDED, s.userId ?: return reject("no user"), s.sessionId,
            EventFactory.payload("workoutExerciseId" to we.workoutExerciseId, "reason" to reason),
            EventSource.SYSTEM, now(),
        )
        return commit(sessionId, plan, listOf(ev))
    }

    fun pause(sessionId: String, plan: WorkoutPlan, restRemainingSec: Double? = sRest(sessionId, plan)): EngineResult {
        val s = snapshot(sessionId, plan)
        val ev = EventFactory.event(
            CommittedEventType.WORKOUT_PAUSED, s.userId ?: return reject("no user"), s.sessionId,
            EventFactory.payload("reason" to "user", "restRemainingSec" to restRemainingSec),
            EventSource.SYSTEM, now(),
        )
        return commit(sessionId, plan, listOf(ev))
    }

    fun resume(sessionId: String, plan: WorkoutPlan): EngineResult {
        val s = snapshot(sessionId, plan)
        val ev = EventFactory.event(
            CommittedEventType.WORKOUT_RESUMED, s.userId ?: return reject("no user"), s.sessionId,
            EventFactory.payload(
                "restoredState" to (s.prePauseState?.name ?: "READY_FOR_EXERCISE"),
                "restRemainingSec" to s.restRemainingSec,
            ),
            EventSource.SYSTEM, now(),
        )
        return commit(sessionId, plan, listOf(ev))
    }

    fun complete(sessionId: String, plan: WorkoutPlan): EngineResult {
        val s = snapshot(sessionId, plan)
        val ev = EventFactory.event(
            CommittedEventType.WORKOUT_COMPLETED, s.userId ?: return reject("no user"), s.sessionId,
            EventFactory.payload("completedAt" to now()),
            EventSource.SYSTEM, now(),
        )
        return commit(sessionId, plan, listOf(ev))
    }

    fun applySubstitution(sessionId: String, plan: WorkoutPlan, toExerciseId: String): EngineResult {
        val s = snapshot(sessionId, plan)
        val we = s.currentExercise ?: return reject("no exercise")
        val ev = EventFactory.event(
            CommittedEventType.SUBSTITUTION_APPLIED, s.userId ?: return reject("no user"), s.sessionId,
            EventFactory.payload(
                "workoutExerciseId" to we.workoutExerciseId,
                "fromExerciseId" to we.exerciseId,
                "toExerciseId" to toExerciseId,
                "preserveObjective" to true,
            ),
            EventSource.SYSTEM, now(),
        )
        return commit(sessionId, plan, listOf(ev))
    }

    /**
     * Commit validated facts. Never calls validateTurn. Occupancy CLARIFY
     * produces no facts; this method must not invent a correction.
     */
    fun applyFacts(sessionId: String, plan: WorkoutPlan, facts: List<ValidatedFact>): EngineResult {
        if (facts.isEmpty()) return EngineResult.Committed(emptyList(), snapshot(sessionId, plan))
        var s = snapshot(sessionId, plan)
        val userId = s.userId ?: return reject("no user")
        val out = mutableListOf<WorkoutEvent>()
        val catchUp = facts.filterIsInstance<ValidatedFact.SetCompleted>()
            .let { it.size >= 2 || it.any { f -> f.loggingMode == LoggingMode.CATCH_UP } }
        val completedFacts = facts.filterIsInstance<ValidatedFact.SetCompleted>()
        if (catchUp && completedFacts.isNotEmpty()) {
            val batch = completedFacts.first().batchId ?: newId()
            completedFacts.forEachIndexed { _, fact ->
                val evs = emitSetCompleted(s, userId, fact, catchUp = true, batchId = batch) ?: return reject("cannot complete set from ${s.phase}")
                evs.forEach { e ->
                    when (val r = WorkoutProjector.apply(s.copy(plan = plan), e)) {
                        is ApplyResult.Ok -> {
                            s = r.snapshot.copy(plan = plan)
                            out += e
                        }
                        is ApplyResult.Duplicate -> {}
                        is ApplyResult.Rejected -> return reject(r.reason)
                    }
                }
            }
            s = maybeRestAfter(s, plan, userId, out)
            if (s.phase == WorkoutSessionState.EXERCISE_COMPLETE) {
                s = maybeNextOrComplete(s, plan, userId, out)
            }
            return appendAll(sessionId, plan, out)
        }
        for (fact in facts) {
            val piece = eventsForFact(s, userId, fact) ?: return reject("cannot apply $fact from ${s.phase}")
            for (e in piece) {
                when (val r = WorkoutProjector.apply(s.copy(plan = plan), e)) {
                    is ApplyResult.Ok -> {
                        s = r.snapshot.copy(plan = plan)
                        out += e
                    }
                    is ApplyResult.Duplicate -> {}
                    is ApplyResult.Rejected -> return reject(r.reason)
                }
            }
        }
        if (facts.any { it is ValidatedFact.SetCompleted }) {
            s = maybeRestAfter(s, plan, userId, out)
            if (s.phase == WorkoutSessionState.EXERCISE_COMPLETE) {
                s = maybeNextOrComplete(s, plan, userId, out)
            }
        }
        if (out.any { it.type == CommittedEventType.EXERCISE_SKIPPED }) {
            s = maybeNextOrComplete(s, plan, userId, out)
        }
        return appendAll(sessionId, plan, out)
    }

    fun tryCommitEvent(sessionId: String, plan: WorkoutPlan, event: WorkoutEvent): EngineResult {
        val s = snapshot(sessionId, plan)
        if (!WorkoutTransitionContract.isLegal(s.phase, event.type) &&
            event.type !in setOf(
                CommittedEventType.SET_CORRECTED,
                CommittedEventType.SET_DELETED,
                CommittedEventType.SESSION_REHYDRATED,
            )
        ) {
            return reject("illegal ${s.phase} + ${event.type}")
        }
        return commit(sessionId, plan, listOf(event))
    }

    private fun eventsForFact(
        s: WorkoutSessionSnapshot,
        userId: String,
        fact: ValidatedFact,
    ): List<WorkoutEvent>? {
        val sessionId = s.sessionId ?: return null
        val we = s.currentExercise
        return when (fact) {
            is ValidatedFact.SetCompleted -> emitSetCompleted(s, userId, fact, catchUp = false, batchId = fact.batchId)
            is ValidatedFact.SetCorrected -> listOf(
                EventFactory.event(
                    CommittedEventType.SET_CORRECTED, userId, sessionId,
                    EventFactory.payload(
                        "setId" to fact.setId.value,
                        "fields" to kotlinx.serialization.json.buildJsonObject {
                            fact.fields.reps?.let { put("reps", kotlinx.serialization.json.JsonPrimitive(it)) }
                            fact.fields.weightKg?.let { put("weightKg", kotlinx.serialization.json.JsonPrimitive(it)) }
                        },
                        "previous" to kotlinx.serialization.json.buildJsonObject {
                            put("setId", kotlinx.serialization.json.JsonPrimitive(fact.previous.setId.value))
                            fact.previous.reps?.let { put("reps", kotlinx.serialization.json.JsonPrimitive(it)) }
                            fact.previous.weightKg?.let { put("weightKg", kotlinx.serialization.json.JsonPrimitive(it)) }
                        },
                    ),
                    EventSource.VOICE, now(),
                ),
            )
            is ValidatedFact.SetDeleted -> listOf(
                EventFactory.event(
                    CommittedEventType.SET_DELETED, userId, sessionId,
                    EventFactory.payload("setId" to fact.setId.value, "reason" to fact.reason),
                    EventSource.VOICE, now(),
                ),
            )
            is ValidatedFact.Command -> when (fact.kind) {
                CommandKind.SKIP -> {
                    val id = we?.workoutExerciseId ?: return null
                    listOf(
                        EventFactory.event(
                            CommittedEventType.EXERCISE_SKIPPED, userId, sessionId,
                            EventFactory.payload("workoutExerciseId" to id),
                            EventSource.SYSTEM, now(),
                        ),
                    )
                }
                CommandKind.EXTRA_SET -> {
                    val cur = we ?: return null
                    listOf(
                        EventFactory.event(
                            CommittedEventType.PLANNED_SETS_CHANGED, userId, sessionId,
                            EventFactory.payload(
                                "workoutExerciseId" to cur.workoutExerciseId,
                                "plannedSets" to (cur.plannedSets + 1),
                                "previousPlannedSets" to cur.plannedSets,
                                "reason" to "extra_set",
                            ),
                            EventSource.SYSTEM, now(),
                        ),
                    )
                }
                CommandKind.PAUSE -> listOf(
                    EventFactory.event(
                        CommittedEventType.WORKOUT_PAUSED, userId, sessionId,
                        EventFactory.payload("reason" to "user"),
                        EventSource.VOICE, now(),
                    ),
                )
                CommandKind.RESUME -> listOf(
                    EventFactory.event(
                        CommittedEventType.WORKOUT_RESUMED, userId, sessionId,
                        EventFactory.payload("restoredState" to (s.prePauseState?.name ?: "RESTING")),
                        EventSource.VOICE, now(),
                    ),
                )
                CommandKind.END_WORKOUT -> listOf(
                    EventFactory.event(
                        CommittedEventType.WORKOUT_COMPLETED, userId, sessionId,
                        EventFactory.payload("completedAt" to now()),
                        EventSource.VOICE, now(),
                    ),
                )
            }
            is ValidatedFact.WeightChanged -> listOf(
                EventFactory.event(
                    CommittedEventType.WEIGHT_CHANGED, userId, sessionId,
                    EventFactory.payload(
                        "workoutExerciseId" to (we?.workoutExerciseId ?: ""),
                        "weight" to fact.weightKg,
                        "weightKg" to fact.weightKg,
                    ),
                    EventSource.VOICE, now(),
                ),
            )
            is ValidatedFact.SubstitutionPropose -> emptyList()
            is ValidatedFact.UserQuestion,
            is ValidatedFact.UserFeedback,
            is ValidatedFact.UserNote,
            -> emptyList()
        }
    }

    private fun emitSetCompleted(
        s: WorkoutSessionSnapshot,
        userId: String,
        fact: ValidatedFact.SetCompleted,
        catchUp: Boolean,
        batchId: String?,
    ): List<WorkoutEvent>? {
        val sessionId = s.sessionId ?: return null
        val we = s.currentExercise ?: return null
        val out = mutableListOf<WorkoutEvent>()
        val live = fact.loggingMode != LoggingMode.CATCH_UP && !catchUp
        if (live && s.phase == WorkoutSessionState.EXERCISE_ACTIVE) {
            out += EventFactory.event(
                CommittedEventType.SET_STARTED, userId, sessionId,
                EventFactory.payload("workoutExerciseId" to we.workoutExerciseId, "setNumber" to s.nextOfficialSetNumber()),
                EventSource.SYSTEM, now(),
            )
        }
        if (live && s.phase == WorkoutSessionState.RESTING) {
            out += EventFactory.event(
                CommittedEventType.REST_ENDED, userId, sessionId,
                EventFactory.payload("workoutExerciseId" to we.workoutExerciseId, "reason" to "logged_through"),
                EventSource.SYSTEM, now(),
            )
        }
        val number = officialNumber(s, fact.setNumberHint, catchUp || fact.loggingMode == LoggingMode.CATCH_UP)
        val setId = newId()
        out += EventFactory.event(
            CommittedEventType.SET_COMPLETED, userId, sessionId,
            EventFactory.payload(
                "setId" to setId,
                "workoutExerciseId" to we.workoutExerciseId,
                "exerciseId" to (fact.exerciseIdFromContext ?: we.exerciseId),
                "setNumber" to number,
                "weightKg" to fact.weightKg,
                "reps" to fact.reps,
                "loggingMode" to if (catchUp) LoggingMode.CATCH_UP else fact.loggingMode,
            ),
            EventSource.VOICE, now(), batchId = batchId,
        )
        return out
    }

    private fun maybeNextOrComplete(
        s: WorkoutSessionSnapshot,
        plan: WorkoutPlan,
        userId: String,
        out: MutableList<WorkoutEvent>,
    ): WorkoutSessionSnapshot {
        if (s.phase != WorkoutSessionState.EXERCISE_COMPLETE) return s
        val nextIdx = plan.exercises.indices.firstOrNull { i ->
            i > s.currentExerciseIndex && !plan.exercises[i].skipped
        }
        val ev = if (nextIdx == null) {
            EventFactory.event(
                CommittedEventType.WORKOUT_COMPLETED, userId, s.sessionId,
                EventFactory.payload("completedAt" to now()),
                EventSource.SYSTEM, now(),
            )
        } else {
            val n = plan.exercises[nextIdx]
            EventFactory.event(
                CommittedEventType.EXERCISE_STARTED, userId, s.sessionId,
                EventFactory.payload(
                    "workoutExerciseId" to n.workoutExerciseId,
                    "exerciseId" to n.exerciseId,
                    "plannedSets" to n.plannedSets,
                    "restTargetSec" to n.restTargetSec,
                ),
                EventSource.SYSTEM, now(),
            )
        }
        return when (val r = WorkoutProjector.apply(s.copy(plan = plan), ev)) {
            is ApplyResult.Ok -> {
                out += ev
                r.snapshot.copy(plan = plan)
            }
            else -> s
        }
    }

    private fun maybeRestAfter(
        s: WorkoutSessionSnapshot,
        plan: WorkoutPlan,
        userId: String,
        out: MutableList<WorkoutEvent>,
    ): WorkoutSessionSnapshot {
        if (s.remainingPlannedOnCurrent() <= 0) return s
        val last = s.liveSetsOnCurrent().maxByOrNull { it.setNumber } ?: return s
        val we = s.currentExercise ?: return s
        val rest = EventFactory.event(
            CommittedEventType.REST_STARTED, userId, s.sessionId,
            EventFactory.payload(
                "workoutExerciseId" to we.workoutExerciseId,
                "afterSetId" to last.setId.value,
                "targetSec" to we.restTargetSec,
            ),
            EventSource.SYSTEM, now(),
        )
        return when (val r = WorkoutProjector.apply(s.copy(plan = plan), rest)) {
            is ApplyResult.Ok -> {
                out += rest
                r.snapshot.copy(plan = plan)
            }
            else -> s
        }
    }

    private fun officialNumber(
        s: WorkoutSessionSnapshot,
        hint: SetNumberHint?,
        catchUp: Boolean,
    ): Int {
        val next = s.nextOfficialSetNumber()
        if (!catchUp) return next
        val hinted = when (hint) {
            is SetNumberHint.Index -> hint.value
            is SetNumberHint.Ordinal -> when (hint.value) {
                HintOrdinal.FIRST -> 1
                HintOrdinal.SECOND -> 2
                HintOrdinal.THIRD -> 3
                HintOrdinal.LAST, HintOrdinal.THIS -> next
            }
            null -> next
        }
        val occupied = s.liveSetsOnCurrent().any { it.setNumber == hinted }
        return if (occupied) next else hinted
    }

    private fun commit(sessionId: String, plan: WorkoutPlan, events: List<WorkoutEvent>): EngineResult {
        events.forEach { e ->
            if (!WorkoutTransitionContract.isLegal(snapshot(sessionId, plan).phase, e.type) &&
                e.type !in setOf(
                    CommittedEventType.SET_CORRECTED,
                    CommittedEventType.SET_DELETED,
                    CommittedEventType.SESSION_REHYDRATED,
                    CommittedEventType.PLANNED_SETS_CHANGED,
                    CommittedEventType.SUBSTITUTION_APPLIED,
                    CommittedEventType.WEIGHT_CHANGED,
                    CommittedEventType.INTENT_REJECTED,
                    CommittedEventType.EXERCISE_SKIPPED,
                ) &&
                !(e.type == CommittedEventType.SET_COMPLETED && snapshot(sessionId, plan).phase in setOf(
                    WorkoutSessionState.RESTING,
                    WorkoutSessionState.SET_ACTIVE,
                    WorkoutSessionState.EXERCISE_ACTIVE,
                    WorkoutSessionState.SET_COMPLETED,
                ))
            ) {
                val s = snapshot(sessionId, plan)
                if (!WorkoutTransitionContract.isLegal(s.phase, e.type)) {
                    return reject("illegal ${s.phase} + ${e.type}")
                }
            }
            log.append(e)
        }
        return EngineResult.Committed(events, snapshot(sessionId, plan))
    }

    private fun appendAll(sessionId: String, plan: WorkoutPlan, events: List<WorkoutEvent>): EngineResult {
        events.forEach { log.append(it) }
        return EngineResult.Committed(events, snapshot(sessionId, plan))
    }

    private fun reject(reason: String) = EngineResult.Rejected(reason)

    private fun sRest(sessionId: String, plan: WorkoutPlan): Double? = snapshot(sessionId, plan).restRemainingSec
}

class InMemoryEventLog : EventLog {
    private val events = mutableListOf<WorkoutEvent>()

    override fun append(event: WorkoutEvent): AppendAck {
        val dup = events.any { it.userId == event.userId && it.eventId == event.eventId }
        if (dup) return AppendAck.Duplicate(com.gymbuddy.domain.EventId(event.eventId))
        events += event
        return AppendAck.Written(com.gymbuddy.domain.EventId(event.eventId))
    }

    override fun find(userId: com.gymbuddy.domain.UserId, eventId: com.gymbuddy.domain.EventId): WorkoutEvent? =
        events.find { it.userId == userId.value && it.eventId == eventId.value }

    override fun sessionEvents(sessionId: com.gymbuddy.domain.SessionId): List<WorkoutEvent> =
        events.filter { it.sessionId == sessionId.value }

    fun all(): List<WorkoutEvent> = events.toList()
}
