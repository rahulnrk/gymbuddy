package com.gymbuddy.app.persist

import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.engine.EngineResult
import com.gymbuddy.domain.engine.PlannedExercise
import com.gymbuddy.domain.engine.WorkoutEngine
import com.gymbuddy.domain.engine.WorkoutPlan
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
import com.gymbuddy.domain.events.WorkoutEvent
import com.gymbuddy.domain.persist.EventLog
import com.gymbuddy.domain.validation.CorrectionFields
import com.gymbuddy.domain.validation.HintOrdinal
import com.gymbuddy.domain.validation.PreviousSetSnapshot
import com.gymbuddy.domain.validation.SetNumberHint
import com.gymbuddy.domain.validation.ValidatedFact
import com.gymbuddy.persist.PersistFixtures
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object EventLogHarness {
    val user = PersistFixtures.USER_ID
    val session = PersistFixtures.SESSION_ID
    val workout = PersistFixtures.WORKOUT_ID
    val exercise = PersistFixtures.EXERCISE_ID
    val we1 = PersistFixtures.WORKOUT_EXERCISE_ID
    val we2 = "55555555-5555-4555-8555-555555555556"
    val exercise2 = "44444444-4444-4444-8444-444444444445"

    fun plan(planned: Int = 4) = WorkoutPlan(
        workout,
        listOf(
            PlannedExercise(we1, exercise, planned),
            PlannedExercise(we2, exercise2, 3),
        ),
    )

    fun event(
        id: String,
        type: CommittedEventType = CommittedEventType.SESSION_INITIALIZED,
        payload: kotlinx.serialization.json.JsonObject = buildJsonObject {
            put("sessionId", session)
            put("workoutId", workout)
        },
    ) = WorkoutEvent(
        eventId = id,
        sessionId = session,
        userId = user,
        occurredAt = "2026-08-20T16:00:00Z",
        recordedAt = "2026-08-20T16:00:00Z",
        source = EventSource.SYSTEM,
        type = type,
        payload = payload,
    )

    fun start(engine: WorkoutEngine, planned: Int = 4): WorkoutPlan {
        val p = plan(planned)
        val r = engine.startSession(user, session, p)
        check(r is EngineResult.Committed) { r.toString() }
        return p
    }

    fun liveSet(reps: Int, weight: Double = 60.0) =
        ValidatedFact.SetCompleted(reps, weight, true, LoggingMode.LIVE)

    fun catchUp(reps: Int, weight: Double, hint: HintOrdinal, batch: String = "b") =
        ValidatedFact.SetCompleted(reps, weight, true, LoggingMode.CATCH_UP, SetNumberHint.Ordinal(hint), batch)

    fun snapKey(s: WorkoutSessionSnapshot) = listOf(
        s.phase,
        s.workoutId,
        s.currentExercise?.exerciseId,
        s.currentExercise?.plannedSets,
        s.liveSetsOnCurrent().map { Triple(it.setNumber, it.reps, it.weightKg) },
        s.completedSets.count { it.deleted },
        s.rest?.targetSec,
        s.prePauseState,
        s.restRemainingSec,
    )

    fun typeSeq(events: List<WorkoutEvent>) = events.map { it.type }

    fun correctionOfFirst(engine: WorkoutEngine, p: WorkoutPlan, reps: Int = 11) {
        val setId = engine.snapshot(session, p).liveSetsOnCurrent().first().setId
        val prev = engine.snapshot(session, p).liveSetsOnCurrent().first()
        engine.applyFacts(
            session, p,
            listOf(
                ValidatedFact.SetCorrected(
                    setId,
                    CorrectionFields(reps = reps),
                    PreviousSetSnapshot(setId, prev.reps, prev.weightKg, prev.setNumber),
                ),
            ),
        )
    }

    fun deleteFirst(engine: WorkoutEngine, p: WorkoutPlan) {
        val setId = engine.snapshot(session, p).liveSetsOnCurrent().first().setId
        val prev = engine.snapshot(session, p).liveSetsOnCurrent().first()
        engine.applyFacts(
            session, p,
            listOf(
                ValidatedFact.SetDeleted(
                    setId,
                    PreviousSetSnapshot(setId, prev.reps, prev.weightKg, prev.setNumber),
                    "scratch that",
                ),
            ),
        )
    }
}

internal class CountingEventLog(private val inner: EventLog) : EventLog {
    var sessionReads = 0
    var appends = 0
    var latestVersionReads = 0
    var sessionReadsOnMain = 0
    var appendsOnMain = 0

    override fun append(event: com.gymbuddy.domain.events.WorkoutEvent) = inner.append(event).also {
        appends++
        if (onMain()) appendsOnMain++
    }

    override fun find(userId: com.gymbuddy.domain.UserId, eventId: com.gymbuddy.domain.EventId) =
        inner.find(userId, eventId)

    override fun sessionEvents(sessionId: com.gymbuddy.domain.SessionId) =
        inner.sessionEvents(sessionId).also {
            sessionReads++
            if (onMain()) sessionReadsOnMain++
        }

    override fun latestVersion(sessionId: com.gymbuddy.domain.SessionId): Int {
        latestVersionReads++
        return inner.latestVersion(sessionId)
    }

    private fun onMain(): Boolean =
        android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
}
