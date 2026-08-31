package com.gymbuddy.domain.loop

import com.gymbuddy.domain.AudioRoute
import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.SafetySeverity
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.engine.InMemoryEventLog
import com.gymbuddy.domain.engine.PlannedExercise
import com.gymbuddy.domain.engine.WorkoutEngine
import com.gymbuddy.domain.engine.WorkoutPlan
import com.gymbuddy.domain.voice.FakeClock
import com.gymbuddy.domain.voice.SttFinalHash
import com.gymbuddy.domain.voice.VoiceTurnEvidence
import com.gymbuddy.domain.voice.fake.FakeTts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompanionLoopTest {
    private val user = "11111111-1111-4111-8111-111111111111"
    private val session = "22222222-2222-4222-8222-222222222222"
    private val workout = "33333333-3333-4333-8333-333333333333"
    private val bench = "44444444-4444-4444-8444-444444444444"
    private val we = "55555555-5555-4555-8555-555555555555"
    private val we2 = "55555555-5555-4555-8555-555555555556"
    private val incline = "44444444-4444-4444-8444-444444444445"

    private fun loop(log: InMemoryEventLog = InMemoryEventLog(), tts: FakeTts? = FakeTts(FakeClock())): CompanionLoop {
        val engine = WorkoutEngine(log)
        return CompanionLoop(
            engine, log,
            WorkoutPlan(workout, listOf(PlannedExercise(we, bench, 4), PlannedExercise(we2, incline, 3))),
            user, session, tts = tts, exerciseNames = mapOf(bench to "Bench Press"), workoutName = "Chest",
        )
    }

    @Test
    fun startThenGot10InheritsAndFirstSetSpeaks() {
        val loop = loop()
        loop.start()
        loop.seedWorkingWeight(60.0)
        val t = loop.handleTurn("Got 10.")
        assertTrue(t.workoutPersisted)
        assertTrue(t.committed.any { it.type == CommittedEventType.SET_COMPLETED })
        val live = t.snapshot.liveSetsOnCurrent()
        assertEquals(10, live.single().reps)
        assertEquals(60.0, live.single().weightKg ?: t.snapshot.workingWeightKg)
        assertTrue(t.speak.speak)
        assertEquals(SpeechActId.FIRST_SET_OF_EXERCISE, (t.speak as SpeakDecision.Speak).actId)
        assertTrue(t.coachingPersisted)
        assertTrue(t.extraction.classifiedBeforeExtract)
    }

    @Test
    fun sameRepsSecondSetSilentButCoachingRowPersisted() {
        val loop = loop()
        loop.start()
        loop.seedWorkingWeight(60.0)
        loop.handleTurn("Got 10.")
        loop.clock.advance(11_000)
        val t = loop.handleTurn("Got 10.")
        assertEquals(2, t.snapshot.liveSetsOnCurrent().size)
        assertFalse(t.speak.speak)
        assertEquals(SilenceReason.NO_USEFUL_DELTA, (t.speak as SpeakDecision.Silence).reason)
        assertNull(t.spokenText)
        assertTrue(t.coachingPersisted)
        val coaches = loop.log.sessionEvents(SessionId(session)).filter { it.type == CommittedEventType.COACHING_RESPONSE }
        assertTrue(coaches.any { it.payload["silence"]!!.toString().contains("true") })
    }

    @Test
    fun plusOneRepSpeaks() {
        val loop = loop()
        loop.start()
        loop.seedWorkingWeight(60.0)
        loop.handleTurn("Got 9.")
        val t = loop.handleTurn("Got 10.")
        assertTrue(t.speak.speak)
        assertEquals(SpeechActId.USEFUL_DELTA, (t.speak as SpeakDecision.Speak).actId)
        assertEquals("Nice. One more rep than your last set.", t.spokenText)
        assertTrue(CoachingPhraser.banned.none { it in t.spokenText.orEmpty() })
    }

    @Test
    fun correctionKeepsSetId() {
        val loop = loop()
        loop.start()
        loop.seedWorkingWeight(60.0)
        val first = loop.handleTurn("Got 10.")
        val id = first.snapshot.liveSetsOnCurrent().single().setId
        val t = loop.handleTurn("No, 8.")
        assertTrue(t.committed.any { it.type == CommittedEventType.SET_CORRECTED })
        assertEquals(id, t.snapshot.liveSetsOnCurrent().single().setId)
        assertEquals(8, t.snapshot.liveSetsOnCurrent().single().reps)
        assertEquals(SpeechActId.CORRECTION_ACK, (t.speak as SpeakDecision.Speak).actId)
    }

    @Test
    fun questionNoMutation() {
        val loop = loop()
        loop.start()
        val t = loop.handleTurn("What did I do last week?")
        assertTrue(t.committed.none { it.type == CommittedEventType.SET_COMPLETED })
        assertTrue(t.speak.speak)
        assertEquals(SpeechActId.ANSWER_QUESTION, (t.speak as SpeakDecision.Speak).actId)
        assertTrue(t.spokenText!!.contains("don't have a previous number") || t.spokenText!!.contains("Last time"))
    }

    @Test
    fun questionDuringLiftDeferred() {
        val loop = loop()
        loop.start()
        loop.seedWorkingWeight(60.0)
        loop.beginSet()
        val t = loop.handleTurn("How much did I do last week?")
        assertFalse(t.speak.speak)
        assertEquals(SilenceReason.SET_ACTIVE, (t.speak as SpeakDecision.Silence).reason)
        assertTrue(SpeechActId.ANSWER_QUESTION in t.queuedSpeechActs)
        val after = loop.handleTurn("Got 10.")
        assertTrue(after.speak.speak)
        assertEquals(SpeechActId.ANSWER_QUESTION, (after.speak as SpeakDecision.Speak).actId)
        assertTrue(loop.queuedSpeechActs.isEmpty())
        assertTrue(after.snapshot.liveSetsOnCurrent().isNotEmpty())
        assertTrue(after.snapshot.phase != WorkoutSessionState.SET_ACTIVE)
    }

    @Test
    fun safetyCriticalCommitsSetAndSafetySpeech() {
        val loop = loop()
        loop.start()
        loop.seedWorkingWeight(60.0)
        val t = loop.handleTurn("I got 10 but my chest hurts.")
        assertEquals(SafetySeverity.CRITICAL, t.safety.severity)
        assertTrue(t.committed.any { it.type == CommittedEventType.SET_COMPLETED } || t.snapshot.liveSetsOnCurrent().isNotEmpty())
        assertTrue(t.speak.speak)
        assertEquals(SpeechActId.SAFETY, (t.speak as SpeakDecision.Speak).actId)
        assertEquals("Stop the exercise and seek appropriate medical attention.", t.spokenText)
        assertTrue(t.snapshot.phase == WorkoutSessionState.PAUSED || t.safety.pause)
    }

    @Test
    fun casualHeavyNoSet() {
        val loop = loop()
        loop.start()
        val t = loop.handleTurn("Bro this is heavy.")
        assertTrue(t.committed.none { it.type == CommittedEventType.SET_COMPLETED })
        assertTrue(t.coachingPersisted)
    }

    @Test
    fun skipAndExtraSet() {
        val loop = loop()
        loop.start()
        val skip = loop.handleTurn("Let's skip this.")
        assertTrue(skip.committed.any { it.type == CommittedEventType.EXERCISE_SKIPPED })
        val extra = loop.handleTurn("Can we do one more?")
        assertTrue(
            extra.committed.any { it.type == CommittedEventType.PLANNED_SETS_CHANGED } ||
                extra.extraction.envelope.candidates.any { it.type.name == "COMMAND" },
        )
    }

    @Test
    fun substitutionProposeDoesNotApply() {
        val loop = loop()
        loop.start()
        val t = loop.handleTurn("Let's use dumbbells instead.")
        assertTrue(t.committed.none { it.type == CommittedEventType.SUBSTITUTION_APPLIED })
        assertTrue(t.extraction.envelope.candidates.any { it.type.name == "SUBSTITUTION_PROPOSE" })
    }

    @Test
    fun duplicateHashDoesNotDoubleSet() {
        val loop = loop()
        loop.start()
        loop.seedWorkingWeight(60.0)
        val hash = SttFinalHash.of("Got 10.")
        val ev = VoiceTurnEvidence("Got 10.", true, durationMs = 400.0, sttFinalHash = hash, recordedAtMs = 1000, audioRoute = AudioRoute.HEADSET)
        loop.handleTurn("Got 10.", ev)
        val second = loop.handleTurn("Got 10.", ev.copy(recordedAtMs = 1500))
        assertTrue(second.duplicateSuppressed || second.snapshot.liveSetsOnCurrent().size == 1)
        assertEquals(1, second.snapshot.liveSetsOnCurrent().size)
    }

    @Test
    fun freeFormConversation() {
        val loop = loop()
        loop.start()
        listOf(
            "Bro this is heavy.",
            "Why am I feeling it in my shoulder?",
            "Give me a second.",
            "Can we do one more?",
            "Let's use dumbbells instead.",
            "Actually that was 9.",
            "Why do I feel this in my shoulder?",
            "What did I do last week?",
            "Got 10.",
            "That machine is occupied.",
            "Naku chest kanna shoulder lo ekkuva feel avutundi.",
        ).forEach { u ->
            val t = loop.handleTurn(u)
            assertTrue(t.extraction.classifiedBeforeExtract, u)
        }
    }

    @Test
    fun recommendIncreaseFrom12x12x11() {
        val loop = loop()
        loop.start()
        loop.seedWorkingWeight(60.0)
        loop.handleTurn("Got 12.")
        loop.clock.advance(11_000)
        loop.handleTurn("Got 12.")
        loop.clock.advance(11_000)
        val t = loop.handleTurn("Got 11.")
        assertNotNull(t.recommendation)
        assertEquals(RecDirection.INCREASE, t.recommendation!!.direction)
        assertEquals(t.snapshot.workingWeightKg!! + 2.5, t.recommendation!!.weightKg)
        assertTrue(t.recommendation!!.evidence.all { it.sourceRowIds.isNotEmpty() })
    }

    @Test
    fun speechFailureDoesNotUndoSet() {
        val clock = FakeClock()
        val tts = FakeTts(clock, unavailable = true)
        val loop = loop(tts = tts)
        loop.start()
        loop.seedWorkingWeight(60.0)
        val t = loop.handleTurn("Got 10.")
        assertEquals(1, t.snapshot.liveSetsOnCurrent().size)
        assertTrue(t.workoutPersisted)
        assertTrue(t.error is TurnError.Speech)
    }

    @Test
    fun persistenceFailureDoesNotReportSetLogged() {
        val log = FailSetCompletedLog()
        val engine = WorkoutEngine(log)
        val loop = CompanionLoop(
            engine, log,
            WorkoutPlan(workout, listOf(PlannedExercise(we, bench, 4), PlannedExercise(we2, incline, 3))),
            user, session,
        )
        loop.start()
        loop.seedWorkingWeight(60.0)
        val t = loop.handleTurn("Got 10.")
        assertFalse(t.workoutPersisted)
        assertTrue(t.error is TurnError.Persistence || t.error is TurnError.Engine)
        assertTrue(log.inner.sessionEvents(SessionId(session)).none { it.type == CommittedEventType.SET_COMPLETED })
        assertFalse(t.spokenText.orEmpty().contains("Set logged", ignoreCase = true))
    }

    @Test
    fun bargeInWhileAiSpeakingDoesNotUndoSet() {
        val clock = FakeClock()
        val tts = FakeTts(clock, cancelDelayMs = 40)
        val loop = loop(tts = tts)
        loop.start()
        loop.seedWorkingWeight(60.0)
        loop.handleTurn("Got 10.")
        assertEquals(1, loop.engine.snapshot(session, loop.plan).liveSetsOnCurrent().size)
        val machine = com.gymbuddy.domain.voice.VoiceTurnMachine(
            clock,
            com.gymbuddy.domain.voice.fake.FakeVad(),
            com.gymbuddy.domain.voice.fake.FakeStt(clock),
            tts,
            com.gymbuddy.domain.voice.fake.FakeLid(),
            com.gymbuddy.domain.voice.fake.FakeAudioFocus(),
        )
        machine.start()
        machine.speak("Nice. That's one more than last time.")
        clock.advance(30)
        machine.onSpeechStart()
        assertTrue(machine.lastBargeInLatencyMs!! < 200)
        assertTrue(tts.lastHandle!!.cancelled)
        assertEquals(1, loop.engine.snapshot(session, loop.plan).liveSetsOnCurrent().size)
    }
}

private class FailSetCompletedLog : com.gymbuddy.domain.persist.EventLog {
    val inner = InMemoryEventLog()
    override fun append(event: com.gymbuddy.domain.events.WorkoutEvent): com.gymbuddy.domain.persist.AppendAck {
        if (event.type == CommittedEventType.SET_COMPLETED) error("disk full")
        return inner.append(event)
    }
    override fun find(userId: com.gymbuddy.domain.UserId, eventId: com.gymbuddy.domain.EventId) =
        inner.find(userId, eventId)
    override fun sessionEvents(sessionId: SessionId) = inner.sessionEvents(sessionId)
}
