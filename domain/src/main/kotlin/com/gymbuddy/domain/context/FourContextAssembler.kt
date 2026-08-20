package com.gymbuddy.domain.context

import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.EpistemicStance
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.engine.ProjectedSet
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
import com.gymbuddy.domain.events.WorkoutEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Read-only four-context assembly.
 *
 * Reads [WorkoutSessionSnapshot], committed session events, and stored
 * structured history/profile/conversation. Does not parse speech, call an
 * LLM, assign set numbers, append events, or write Room.
 *
 * Same inputs always produce the same [FourContexts].
 */
object FourContextAssembler {

    fun assembleFourContexts(request: AssemblyRequest): FourContexts = assemble(request)

    fun assemble(request: AssemblyRequest): FourContexts {
        val snapshot = request.snapshot
        val events = request.sessionEvents
        val workout = workoutContext(request, snapshot, events)
        val historical = historicalContext(request, snapshot, workout)
        val conversational = conversationalContext(request, events)
        val user = userContext(request, snapshot)
        val facts = factsOf(workout, historical, request)
        return FourContexts(
            workout = workout,
            historical = historical,
            conversational = conversational,
            user = user,
            facts = facts,
        )
    }

    private fun workoutContext(
        request: AssemblyRequest,
        snapshot: WorkoutSessionSnapshot,
        events: List<WorkoutEvent>,
    ): WorkoutStateContext {
        val current = snapshot.currentExercise
        val live = snapshot.liveSetsOnCurrent()
        val occurredAt = occurredAtBySetId(events)
        val startedAt = events.firstOrNull { it.type == CommittedEventType.WORKOUT_STARTED }?.occurredAt
            ?: events.firstOrNull { it.type == CommittedEventType.SESSION_INITIALIZED }?.occurredAt
            ?: ""
        val exerciseStarted = events.lastOrNull { it.type == CommittedEventType.EXERCISE_STARTED }?.occurredAt
        val restStarted = if (snapshot.rest != null) {
            events.lastOrNull { it.type == CommittedEventType.REST_STARTED }?.occurredAt
        } else null
        val substitutions = snapshot.plan?.exercises.orEmpty().mapNotNull { pe ->
            pe.substitutedFromExerciseId?.let { SubstitutionSnapshot(it, pe.exerciseId) }
        }
        return WorkoutStateContext(
            sessionId = snapshot.sessionId.orEmpty(),
            workoutId = snapshot.workoutId.orEmpty(),
            workoutName = request.workoutName,
            sessionState = snapshot.phase,
            voiceState = request.voiceState,
            currentExerciseId = current?.exerciseId,
            currentExerciseName = current?.exerciseId?.let { request.exerciseNames[it] },
            currentSetNumber = currentSetNumber(snapshot, live),
            completedSets = live.map { it.toSnapshot(occurredAt) },
            remainingSetsPlanned = snapshot.remainingPlannedOnCurrent(),
            targetReps = request.targetReps,
            currentWeightKg = snapshot.workingWeightKg,
            restStartedAt = restStarted,
            restTargetSec = snapshot.rest?.targetSec?.toDouble() ?: current?.restTargetSec?.toDouble(),
            restRemainingSec = snapshot.restRemainingSec,
            substitutions = substitutions,
            startedAt = startedAt,
            previousExerciseLastSet = previousExerciseLastSet(snapshot, occurredAt),
            currentExerciseStartedAt = exerciseStarted,
        )
    }

    private fun currentSetNumber(snapshot: WorkoutSessionSnapshot, live: List<ProjectedSet>): Int? {
        val phase = if (snapshot.phase == WorkoutSessionState.PAUSED) {
            snapshot.prePauseState ?: snapshot.phase
        } else snapshot.phase
        return when (phase) {
            WorkoutSessionState.SET_ACTIVE -> snapshot.nextOfficialSetNumber()
            WorkoutSessionState.RESTING,
            WorkoutSessionState.SET_COMPLETED,
            WorkoutSessionState.EXERCISE_COMPLETE,
            -> live.maxOfOrNull { it.setNumber }
            else -> null
        }
    }

    private fun previousExerciseLastSet(
        snapshot: WorkoutSessionSnapshot,
        occurredAt: Map<String, String>,
    ): CompletedSetSnapshot? {
        val plan = snapshot.plan ?: return null
        val idx = snapshot.currentExerciseIndex
        if (idx <= 0) return null
        val prev = plan.exercises.getOrNull(idx - 1) ?: return null
        val last = snapshot.completedSets
            .filter { it.workoutExerciseId == prev.workoutExerciseId && !it.deleted }
            .maxByOrNull { it.setNumber } ?: return null
        return last.toSnapshot(occurredAt)
    }

    private fun historicalContext(
        request: AssemblyRequest,
        snapshot: WorkoutSessionSnapshot,
        workout: WorkoutStateContext,
    ): HistoricalStateContext {
        val store = request.history
        val sessions = store.previousSessionsForExercise
        val aggregates = mutableListOf<EpistemicFact>()
        val exerciseId = snapshot.currentExercise?.exerciseId ?: "none"
        if (sessions.isEmpty()) {
            aggregates += EpistemicFact(
                id = "fact:hist:previous-weight:$exerciseId",
                kind = "aggregate",
                stance = EpistemicStance.NONE,
                value = null,
                sourceRowIds = emptyList(),
                speechAllowed = false,
            )
        } else {
            val histSets = sessions.flatMap { it.sets }
            val sourceIds = histSets.map { "set:${it.setId}" }
            if (sourceIds.isNotEmpty()) {
                aggregates += EpistemicFact(
                    id = "fact:hist:previous-weight:$exerciseId",
                    kind = "aggregate",
                    stance = EpistemicStance.KNOWN,
                    value = buildJsonObject {
                        put("occurredOn", sessions.first().occurredOn)
                        put("setCount", histSets.size)
                    },
                    sourceRowIds = sourceIds,
                    speechAllowed = true,
                )
            }
            val weighted = histSets.mapNotNull { s -> s.weightKg?.let { s.setId to it } }
            if (weighted.size >= 2) {
                aggregates += EpistemicFact(
                    id = "fact:agg:mean-weight:$exerciseId",
                    kind = "aggregate",
                    stance = EpistemicStance.ESTIMATED,
                    value = JsonPrimitive(weighted.map { it.second }.average()),
                    sourceRowIds = weighted.map { "set:${it.first}" },
                    speechAllowed = true,
                )
            }
            val currentDay = workout.startedAt.take(10)
            val prior = sessions.maxByOrNull { it.occurredOn }
            if (prior != null && currentDay.isNotEmpty() && prior.occurredOn < currentDay) {
                aggregates += EpistemicFact(
                    id = "fact:inferred:prior-session:$exerciseId",
                    kind = "session_summary",
                    stance = EpistemicStance.INFERRED,
                    value = JsonPrimitive(prior.occurredOn),
                    sourceRowIds = listOf("history:${prior.occurredOn}"),
                    speechAllowed = true,
                )
            }
        }
        store.uncertainFacts.filter { it.stance == EpistemicStance.UNCERTAIN }.forEach { claim ->
            aggregates += claim.copy(speechAllowed = false)
        }
        return HistoricalStateContext(
            previousSessionsForExercise = sessions,
            lastWorkoutSummary = store.lastWorkoutSummary,
            knownPrs = store.knownPrs,
            aggregates = aggregates,
            recentNotesForExercise = store.recentNotesForExercise,
        )
    }

    private fun conversationalContext(
        request: AssemblyRequest,
        events: List<WorkoutEvent>,
    ): ConversationalStateContext {
        val store = request.conversation
        val correctionsFromEvents = events.filter { it.type == CommittedEventType.SET_CORRECTED }.map { ev ->
            val fieldsObj = ev.payload["fields"] as? JsonObject
            CorrectionSnapshot(
                setId = ev.payload.str("setId").orEmpty(),
                fields = fieldsObj?.keys?.sorted().orEmpty(),
                at = ev.occurredAt,
            )
        }
        val corrections = if (correctionsFromEvents.isNotEmpty()) correctionsFromEvents else store.recentCorrections
        val pendingFromEvents = events.lastOrNull { it.type == CommittedEventType.CLARIFICATION_REQUESTED }?.let { ev ->
            PendingClarification(
                question = ev.payload.str("question") ?: ev.payload.str("prompt").orEmpty(),
                options = ev.payload.stringList("options"),
                relatedEventHint = ev.payload.str("relatedEventHint"),
            )
        }
        val questionEvent = events.lastOrNull { it.type == CommittedEventType.USER_QUESTION }
        val languageEvent = events.lastOrNull { it.type == CommittedEventType.LANGUAGE_DETECTED }
        val hashesFromEvents = events.mapNotNull { ev ->
            ev.sttFinalHash?.let { SttFinalRecord(it, epochMs(ev.occurredAt)) }
        }
        val hashes = (hashesFromEvents + store.recentSttFinals)
            .distinctBy { it.hash }
        val lastUser = store.lastUserUtterance ?: questionEvent?.let { ev ->
            val text = ev.payload.str("questionOriginal") ?: ev.payload.str("textOriginal") ?: return@let null
            UtteranceSnapshot(text, ev.language ?: store.languageDetected.orEmpty(), ev.occurredAt)
        }
        val topic = store.currentTopic ?: questionEvent?.payload?.str("topic")
        val language = store.languageDetected
            ?: languageEvent?.payload?.str("language")
            ?: languageEvent?.language
            ?: request.profile.primaryLanguage
        return ConversationalStateContext(
            currentTopic = topic,
            lastUserUtterance = lastUser,
            lastAiUtterance = store.lastAiUtterance,
            pendingClarification = store.pendingClarification ?: pendingFromEvents,
            pendingCommand = store.pendingCommand,
            queuedSpeechActs = store.queuedSpeechActs,
            recentCorrections = corrections,
            languageDetected = language,
            recentSttFinals = hashes,
        )
    }

    private fun userContext(request: AssemblyRequest, snapshot: WorkoutSessionSnapshot): UserStateContext {
        val p = request.profile
        return UserStateContext(
            userId = snapshot.userId?.takeIf { it.isNotBlank() } ?: p.userId,
            goals = p.goals,
            experience = p.experience,
            equipment = p.equipment,
            units = p.units,
            primaryLanguage = p.primaryLanguage,
            additionalLanguages = p.additionalLanguages,
            preferences = p.preferences,
            limitations = p.limitations,
        )
    }

    private fun factsOf(
        workout: WorkoutStateContext,
        historical: HistoricalStateContext,
        request: AssemblyRequest,
    ): List<EpistemicFact> {
        val out = mutableListOf<EpistemicFact>()
        workout.currentExerciseId?.let { id ->
            out += EpistemicFact(
                id = "fact:workout:current-exercise",
                kind = "catalog_exercise",
                stance = EpistemicStance.KNOWN,
                value = JsonPrimitive(id),
                sourceRowIds = listOf("session:${workout.sessionId}"),
                speechAllowed = true,
            )
        }
        val weight = workout.currentWeightKg
        if (weight != null) {
            val sources = workout.completedSets.map { "set:${it.setId}" }
                .ifEmpty { listOf("session:${workout.sessionId}") }
            out += EpistemicFact(
                id = "fact:workout:current-weight",
                kind = "set",
                stance = EpistemicStance.KNOWN,
                value = JsonPrimitive(weight),
                sourceRowIds = sources,
                speechAllowed = true,
            )
        }
        workout.completedSets.forEach { set ->
            out += EpistemicFact(
                id = "fact:workout:set:${set.setId}",
                kind = "set",
                stance = EpistemicStance.KNOWN,
                value = buildJsonObject {
                    put("setNumber", set.setNumber)
                    set.weightKg?.let { put("weightKg", it) }
                    set.reps?.let { put("reps", it) }
                },
                sourceRowIds = listOf("set:${set.setId}"),
                speechAllowed = true,
            )
        }
        historical.previousSessionsForExercise.forEach { session ->
            session.sets.forEach { set ->
                out += EpistemicFact(
                    id = "fact:hist:set:${set.setId}",
                    kind = "set",
                    stance = session.stance,
                    value = buildJsonObject {
                        put("occurredOn", session.occurredOn)
                        put("setNumber", set.setNumber)
                        set.weightKg?.let { put("weightKg", it) }
                        set.reps?.let { put("reps", it) }
                    },
                    sourceRowIds = listOf("set:${set.setId}", "history:${session.occurredOn}"),
                    speechAllowed = session.stance == EpistemicStance.KNOWN,
                )
            }
        }
        out += historical.aggregates
        historical.recentNotesForExercise.forEachIndexed { i, note ->
            out += EpistemicFact(
                id = "fact:note:$i:${note.createdAt}",
                kind = "note",
                stance = note.stance,
                value = JsonPrimitive(note.text),
                sourceRowIds = listOf("note:${note.createdAt}"),
                speechAllowed = note.stance == EpistemicStance.KNOWN,
            )
        }
        request.history.knownPrs.forEach { pr ->
            out += EpistemicFact(
                id = "fact:pr:${pr.exerciseId}:${pr.kind}:${pr.date}",
                kind = "pr",
                stance = pr.stance,
                value = JsonPrimitive(pr.value),
                sourceRowIds = listOf("pr:${pr.exerciseId}:${pr.date}"),
                speechAllowed = pr.stance == EpistemicStance.KNOWN,
            )
        }
        return out
    }

    private fun occurredAtBySetId(events: List<WorkoutEvent>): Map<String, String> =
        events.filter { it.type == CommittedEventType.SET_COMPLETED }.mapNotNull { ev ->
            ev.payload.str("setId")?.let { it to ev.occurredAt }
        }.toMap()

    private fun ProjectedSet.toSnapshot(occurredAt: Map<String, String>) = CompletedSetSnapshot(
        setId = setId.value,
        setNumber = setNumber,
        weightKg = weightKg,
        reps = reps,
        occurredAt = occurredAt[setId.value],
        exerciseId = exerciseId,
    )

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.stringList(key: String): List<String> {
        val el = this[key] as? JsonArray ?: return emptyList()
        return el.mapNotNull { it.jsonPrimitive.contentOrNull }
    }

    private fun epochMs(iso: String): Long =
        runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)
}
