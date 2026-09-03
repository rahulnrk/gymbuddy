package com.gymbuddy.domain.voice

import com.gymbuddy.domain.VoiceTurnState
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.catalog.CatalogTest
import com.gymbuddy.domain.catalog.Provenance
import com.gymbuddy.domain.picker.Slot
import com.gymbuddy.domain.picker.SlotRole
import com.gymbuddy.domain.plan.PlannedEntry
import com.gymbuddy.domain.plan.PlannedSession
import com.gymbuddy.domain.programming.Effort
import com.gymbuddy.domain.programming.IntRange2
import com.gymbuddy.domain.programming.SetPerformance
import com.gymbuddy.domain.voice.VoiceInteractionConstants.BARGE_IN_CANCEL_P95_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.DUCK_HOLD_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.CONFIDENCE_HORIZON_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.REST_DECAY_EXTENSION_SEC
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_CLOSED_ANSWER_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_HARD_CAP_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_OPEN_QUESTION_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_SHORT_QUESTION_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_STATEMENT_MS
import com.gymbuddy.domain.voice.VoiceInteractionConstants.WINDOW_USER_INITIATED_MS
import com.gymbuddy.domain.voice.fake.FakeAudioFocus
import com.gymbuddy.domain.voice.fake.FakeClock
import com.gymbuddy.domain.voice.fake.FakeLid
import com.gymbuddy.domain.voice.fake.FakeStt
import com.gymbuddy.domain.voice.fake.FakeTts
import com.gymbuddy.domain.voice.fake.FakeVad
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceInteractionTest {
    private val catalog = CatalogTest.loadCatalog()
    private val provenance = Provenance(
        confidence = "low",
        basis = "judgement",
        source = "test fixture",
        reviewed = VoiceInteractionConstants.REVIEWED,
    )

    @Test
    fun statementOpensTheShortestWindow() {
        val window = windowFor(UtteranceKind.STATEMENT)
        val stmt = assertIs<Window.CoachInitiated>(window)
        assertEquals(WINDOW_STATEMENT_MS, stmt.maxMs)
        assertTrue(shouldDuck(stmt))
        assertTrue(stmt.maxMs < WINDOW_CLOSED_ANSWER_MS)
        assertNull(stmt.coachFinishedAtElapsedMs)
    }

    @Test
    fun closedAnswerIsShortOpenQuestionIsLong() {
        val closed = windowFor(UtteranceKind.CLOSED_ANSWER)
        val shortQ = windowFor(UtteranceKind.SHORT_QUESTION)
        val open = windowFor(UtteranceKind.OPEN_QUESTION)
        assertIs<Window.CoachInitiated>(closed)
        assertEquals(WINDOW_CLOSED_ANSWER_MS, closed.maxMs)
        assertEquals(WINDOW_SHORT_QUESTION_MS, shortQ.maxMs)
        assertEquals(WINDOW_OPEN_QUESTION_MS, open.maxMs)
        assertTrue(closed.maxMs < shortQ.maxMs)
        assertTrue(shortQ.maxMs < open.maxMs)
        assertTrue(shouldDuck(closed))
        assertTrue(shouldDuck(open))
    }

    @Test
    fun replyBudgetStartsWhenCoachStopsNotWhenWindowOpens() {
        val prompt = windowFor(UtteranceKind.CLOSED_ANSWER)
        assertIs<Window.CoachInitiated>(prompt)
        val shortLine = onCoachFinished(prompt, elapsedMs = 1_000L)
        val longLine = onCoachFinished(prompt, elapsedMs = 4_000L)
        val shortDone = assertIs<Window.CoachInitiated>(shortLine)
        val longDone = assertIs<Window.CoachInitiated>(longLine)
        assertEquals(shortDone.maxMs, longDone.maxMs)
        assertEquals(WINDOW_CLOSED_ANSWER_MS, shortDone.maxMs)

        val shortBudget = shortDone.coachFinishedAtElapsedMs!! + shortDone.maxMs
        val longBudget = longDone.coachFinishedAtElapsedMs!! + longDone.maxMs
        assertEquals(WINDOW_CLOSED_ANSWER_MS, shortBudget - 1_000L)
        assertEquals(WINDOW_CLOSED_ANSWER_MS, longBudget - 4_000L)

        assertFalse(
            onTimeout(shortDone, elapsedMs = shortBudget - 1) is Window.None,
        )
        assertIs<Window.None>(onTimeout(shortDone, elapsedMs = shortBudget))
        assertFalse(
            onTimeout(longDone, elapsedMs = longBudget - 1) is Window.None,
        )
        assertIs<Window.None>(onTimeout(longDone, elapsedMs = longBudget))

        assertFalse(
            onTimeout(prompt, elapsedMs = WINDOW_CLOSED_ANSWER_MS + 2_000L) is Window.None,
            "budget must not run while the coach is still speaking",
        )
        val cutIn = onSpeechDetected(prompt, elapsedMs = 500L)
        assertTrue(shouldDuck(cutIn))
        assertFalse(cutIn is Window.None)
        assertNull(
            assertIs<Window.CoachInitiated>(cutIn).coachFinishedAtElapsedMs,
            "barge-in must not start the reply budget; the coach has not finished",
        )
        assertEquals(WINDOW_CLOSED_ANSWER_MS, cutIn.maxMs)

        val shortLate = onSpeechDetected(shortDone, elapsedMs = 1_000L + 7_000L)
        val longLate = onSpeechDetected(longDone, elapsedMs = 4_000L + 7_000L)
        val shortLateDone = assertIs<Window.CoachInitiated>(shortLate)
        val longLateDone = assertIs<Window.CoachInitiated>(longLate)
        val shortRemain =
            shortLateDone.coachFinishedAtElapsedMs!! + shortLateDone.maxMs - (1_000L + 7_000L)
        val longRemain =
            longLateDone.coachFinishedAtElapsedMs!! + longLateDone.maxMs - (4_000L + 7_000L)
        assertEquals(shortRemain, longRemain)
        assertEquals(DUCK_HOLD_MS, shortRemain)
    }

    @Test
    fun speechBeforeCloseExtendsPastSixtySecondsWhenStillTalking() {
        val closed = onCoachFinished(windowFor(UtteranceKind.CLOSED_ANSWER), elapsedMs = 0L)
        val extended = onSpeechDetected(closed, elapsedMs = 7_000L)
        assertTrue(extended.maxMs > closed.maxMs)

        val pastCap = onSpeechDetected(
            Window.UserInitiated(EntryPoint.EMERGENCY, WINDOW_HARD_CAP_MS - 1_000L),
            elapsedMs = WINDOW_HARD_CAP_MS + 5_000L,
        )
        assertTrue(pastCap.maxMs > WINDOW_HARD_CAP_MS, "live conversation must not be capped at 60 s")
        assertTrue(shouldDuck(pastCap))
        val stillOpen = onTimeout(pastCap, elapsedMs = WINDOW_HARD_CAP_MS + 5_000L, userSpeaking = true)
        assertFalse(stillOpen is Window.None)
        val abandoned = onTimeout(pastCap, elapsedMs = WINDOW_HARD_CAP_MS + 5_000L, userSpeaking = false)
        assertIs<Window.None>(abandoned)

        assertIs<Window.None>(onSpeechDetected(Window.None, elapsedMs = 1_000L))
    }

    @Test
    fun duckExactlyWhileWindowOpen() {
        val open = windowFor(UtteranceKind.CLOSED_ANSWER)
        val ready = onCoachFinished(open, elapsedMs = 0L)
        assertTrue(shouldDuck(ready))
        assertFalse(shouldDuck(onTimeout(ready, elapsedMs = open.maxMs)))
        assertFalse(shouldDuck(close(open)))
        assertFalse(shouldDuck(onCancel(open)))
        assertTrue(shouldDuck(onTimeout(ready, elapsedMs = open.maxMs - 1)))
    }

    @Test
    fun maySpeakProactivelyFalseDuringSetActiveBelowSafetyHigh() {
        val clock = CoachClock(
            workoutElapsedMs = 10_000L,
            lastAnchorMs = 0L,
            anchorKind = AnchorKind.LOGGED_EVENT,
        )
        assertFalse(maySpeakProactively(clock, WorkoutSessionState.SET_ACTIVE, safetyHigh = false))
        assertTrue(maySpeakProactively(clock, WorkoutSessionState.SET_ACTIVE, safetyHigh = true))
        assertTrue(maySpeakProactively(clock, WorkoutSessionState.RESTING, safetyHigh = false))
    }

    @Test
    fun pastHorizonResyncOnceThenNull() {
        val clock = CoachClock(
            workoutElapsedMs = CONFIDENCE_HORIZON_MS,
            lastAnchorMs = 0L,
            anchorKind = AnchorKind.USER_UTTERANCE,
        )
        val plan = sessionWithRest(90)
        val first = proactiveUtterance(clock, WorkoutSessionState.RESTING, plan, restElapsedSec = 90)
        val resync = assertIs<CoachAct.ReSync>(first)
        assertEquals("Ready when you are?", resync.text)
        assertPlainReason(resync.reason)

        val second = proactiveUtterance(
            clock.markResyncOffered(),
            WorkoutSessionState.RESTING,
            plan,
            restElapsedSec = 90,
        )
        assertNull(second)

        val atFiveTimes = proactiveUtterance(
            clock.at(CONFIDENCE_HORIZON_MS * 5).markResyncOffered(),
            WorkoutSessionState.RESTING,
            plan,
            restElapsedSec = 400,
        )
        assertNull(atFiveTimes)
    }

    @Test
    fun userUtteranceReanchorsAndRestoresProactive() {
        val silent = CoachClock(
            workoutElapsedMs = CONFIDENCE_HORIZON_MS * 2,
            lastAnchorMs = 0L,
            anchorKind = AnchorKind.LOGGED_EVENT,
            resyncOffered = true,
        )
        assertFalse(maySpeakProactively(silent, WorkoutSessionState.RESTING))
        assertNull(
            proactiveUtterance(silent, WorkoutSessionState.RESTING, sessionWithRest(90), restElapsedSec = 200),
        )

        val live = silent.anchored(kind = AnchorKind.USER_UTTERANCE)
        assertTrue(maySpeakProactively(live, WorkoutSessionState.RESTING))
        assertFalse(live.resyncOffered)
        val restPrompt = proactiveUtterance(
            live,
            WorkoutSessionState.RESTING,
            sessionWithRest(90),
            restElapsedSec = 90,
        )
        assertIs<CoachAct.RestComplete>(restPrompt)
        assertPlainReason(restPrompt.reason)
        assertPlainReason(restPrompt.text)
        assertNull(
            proactiveUtterance(
                live.markRestCompleteOffered(),
                WorkoutSessionState.RESTING,
                sessionWithRest(90),
                restElapsedSec = 200,
            ),
        )
    }

    @Test
    fun userInitiatedWindowsShareLengthAndEmergencyIsLive() {
        assertIs<Window.None>(windowFor(EntryPoint.WAKE_WORD))
        assertIs<Window.None>(windowFor(EntryPoint.EARBUD))
        assertIs<Window.None>(windowFor(EntryPoint.ON_SCREEN))
        val emergency = assertIs<Window.UserInitiated>(windowFor(EntryPoint.EMERGENCY))
        assertEquals(WINDOW_USER_INITIATED_MS, emergency.maxMs)
        assertTrue(shouldDuck(emergency))
        assertFalse(isDormantEntry(EntryPoint.EMERGENCY))
        assertTrue(isDormantEntry(EntryPoint.WAKE_WORD))
        assertTrue(isDormantEntry(EntryPoint.EARBUD))
        assertTrue(isDormantEntry(EntryPoint.ON_SCREEN))
    }

    @Test
    fun bargeInStillUnderExistingBudget() {
        val clock = FakeClock()
        val machine = VoiceTurnMachine(
            clock = clock,
            vad = FakeVad(),
            stt = FakeStt(clock),
            tts = FakeTts(clock, cancelDelayMs = 40),
            lid = FakeLid(),
            focus = FakeAudioFocus(),
        )
        machine.start()
        machine.speak("That's your rest. Ready for the next set?")
        clock.advance(80)
        machine.onSpeechStart()
        val latency = machine.lastBargeInLatencyMs
        assertNotNull(latency)
        assertTrue(latency < BARGE_IN_CANCEL_P95_MS, "latency=$latency")

        val before = windowFor(UtteranceKind.OPEN_QUESTION)
        val after = onSpeechDetected(before, elapsedMs = 1_000L)
        assertTrue(after.maxMs >= before.maxMs)
        assertEquals(VoiceTurnState.USER_SPEAKING, machine.state)
    }

    @Test
    fun predictiveRestExtendsOnDeclineNeverBelowFloor() {
        val target = IntRange2(8, 12, provenance)
        val declined = restAdjustment(
            setsThisExercise = listOf(
                SetPerformance(reps = 8, loadKg = 80.0),
                SetPerformance(reps = 8, loadKg = 80.0),
                SetPerformance(reps = 6, loadKg = 80.0),
            ),
            target = target,
            currentRestSec = 90,
            restFloorSec = 90,
        )
        assertEquals(REST_DECAY_EXTENSION_SEC, declined.extraSec)
        assertEquals(120, declined.restSec)
        assertNotNull(declined.reason)
        assertPlainReason(declined.reason!!)
        assertTrue("6 reps" in declined.reason!!)
        assertTrue("30 seconds" in declined.reason!!)

        val alreadyShort = restAdjustment(
            setsThisExercise = listOf(
                SetPerformance(reps = 8),
                SetPerformance(reps = 6),
            ),
            target = target,
            currentRestSec = 60,
            restFloorSec = 90,
        )
        assertTrue(alreadyShort.restSec >= 90)

        val steady = restAdjustment(
            setsThisExercise = listOf(
                SetPerformance(reps = 8),
                SetPerformance(reps = 8),
            ),
            target = target,
            currentRestSec = 120,
            restFloorSec = 90,
        )
        assertEquals(0, steady.extraSec)
        assertEquals(120, steady.restSec)
        assertNull(steady.reason)
        assertTrue(steady.restSec >= 90)
    }

    @Test
    fun proactiveAndRestReasonsArePlainLanguage() {
        val plan = sessionWithRest(90)
        val rest = proactiveUtterance(
            CoachClock(30_000L, 0L, AnchorKind.LOGGED_EVENT),
            WorkoutSessionState.RESTING,
            plan,
            restElapsedSec = 90,
        )
        assertIs<CoachAct.RestComplete>(rest)
        assertPlainReason(rest.text)
        assertPlainReason(rest.reason)

        val resync = proactiveUtterance(
            CoachClock(CONFIDENCE_HORIZON_MS, 0L, AnchorKind.USER_UTTERANCE),
            WorkoutSessionState.EXERCISE_ACTIVE,
            plan,
        )
        assertIs<CoachAct.ReSync>(resync)
        assertPlainReason(resync.text)
        assertPlainReason(resync.reason)
    }

    @Test
    fun voiceTurnStateIndependentOfWorkoutSessionState() {
        val voice = VoiceTurnState.entries.map { it.name }.toSet()
        val workout = WorkoutSessionState.entries.map { it.name }.toSet()
        assertTrue(voice.intersect(workout).isEmpty(), "shared names: ${voice.intersect(workout)}")

        val voiceDir = voiceDir()
        val windowSrc = File(voiceDir, "Window.kt").readText()
        val clockSrc = File(voiceDir, "CoachClock.kt").readText()
        val machineSrc = File(voiceDir, "VoiceTurnMachine.kt").readText()
        assertFalse("VoiceTurnState" in windowSrc)
        assertFalse("VoiceTurnState" in clockSrc)
        assertFalse("CoachClock" in machineSrc)
        assertFalse("UtteranceKind" in machineSrc)
        assertFalse("fun windowFor" in machineSrc)
    }

    @Test
    fun everyVoiceConstantHasADecisionsRow() {
        val decisions = decisionsFile().readText()
        val src = File(voiceDir(), "VoiceInteractionConstants.kt").readText()
        Regex("""const val ([A-Z][A-Z0-9_]*)\s*=""").findAll(src).forEach { m ->
            val name = m.groupValues[1]
            assertTrue(name in decisions, "$name missing from DECISIONS_AND_THRESHOLDS.md")
        }
        assertEquals(77, catalog.exercises.size)
    }

    @Test
    fun restCompleteDoesNotSpeakDuringSetActive() {
        val act = proactiveUtterance(
            CoachClock(20_000L, 0L, AnchorKind.LOGGED_EVENT),
            WorkoutSessionState.SET_ACTIVE,
            sessionWithRest(90),
            restElapsedSec = 90,
            safetyHigh = true,
        )
        assertNull(act)
    }

    @Test
    fun fourMinutesUnderTheBarIsSilence() {
        val plan = sessionWithRest(90)
        val start = CoachClock(0L, 0L, AnchorKind.LOGGED_EVENT)
        val fourMinutes = 240_000L
        val ticks = listOf(0L, 30_000L, 90_000L, 180_000L, fourMinutes)
        ticks.forEach { t ->
            val clock = start.at(t)
            assertFalse(maySpeakProactively(clock, WorkoutSessionState.SET_ACTIVE, safetyHigh = false))
            assertNull(
                proactiveUtterance(
                    clock,
                    WorkoutSessionState.SET_ACTIVE,
                    plan,
                    restElapsedSec = (t / 1000).toInt(),
                    safetyHigh = false,
                ),
                "spoke at ${t}ms under a loaded bar",
            )
        }
    }

    @Test
    fun unansweredRestCompleteStaysSilentUntilHorizon() {
        val plan = sessionWithRest(90)
        val afterRest = CoachClock(
            workoutElapsedMs = 90_000L,
            lastAnchorMs = 0L,
            anchorKind = AnchorKind.LOGGED_EVENT,
        )
        val first = proactiveUtterance(afterRest, WorkoutSessionState.RESTING, plan, restElapsedSec = 90)
        assertIs<CoachAct.RestComplete>(first)
        val waiting = afterRest.markRestCompleteOffered()
        listOf(120_000L, 180_000L, 240_000L, CONFIDENCE_HORIZON_MS - 1).forEach { t ->
            assertNull(
                proactiveUtterance(waiting.at(t), WorkoutSessionState.RESTING, plan, restElapsedSec = (t / 1000).toInt()),
                "asked again at ${t}ms after rest-complete",
            )
        }
    }

    private fun sessionWithRest(restMin: Int): PlannedSession {
        val ex = catalog.bySlug("bench-press")!!
        val range = IntRange2(restMin, restMin + 30, provenance)
        val reps = IntRange2(8, 12, provenance)
        val effort = Effort(2, 4, provenance)
        return PlannedSession(
            day = "Test",
            entries = listOf(
                PlannedEntry(
                    exercise = ex,
                    slot = Slot(ex.pattern, SlotRole.PRIMARY),
                    sets = 3,
                    repRange = reps,
                    restSec = range,
                    effort = effort,
                    why = emptyList(),
                    priority = 1,
                ),
            ),
        )
    }

    private fun assertPlainReason(text: String) {
        val bannedFields = listOf(
            "restSec", "extraSec", "maxMs", "IntRange2", "SetPerformance",
            "CONFIDENCE_HORIZON", "WorkoutSessionState", "VoiceTurnState",
            "SET_ACTIVE", "lastAnchorMs",
        )
        bannedFields.forEach { field ->
            assertFalse(field in text, "reason leaked $field: $text")
        }
        listOf("Command received.", "Your set has been logged.", "Proceed to the next exercise.").forEach {
            assertFalse(it in text, text)
        }
        Regex("""\b\d+\b""").findAll(text).forEach { m ->
            val after = text.substring(m.range.last + 1).trimStart()
            assertTrue(
                after.startsWith("second") || after.startsWith("minute") || after.startsWith("rep"),
                "bare number '${m.value}' without units in: $text",
            )
        }
    }

    private fun voiceDir(): File {
        val cwd = File("").absoluteFile
        return listOf(
            File(cwd, "src/main/kotlin/com/gymbuddy/domain/voice"),
            File(cwd, "domain/src/main/kotlin/com/gymbuddy/domain/voice"),
            File(cwd.parentFile, "domain/src/main/kotlin/com/gymbuddy/domain/voice"),
        ).first { it.isDirectory }
    }

    private fun decisionsFile(): File {
        val cwd = File("").absoluteFile
        return listOf(
            File(cwd, "docs/living-spec/DECISIONS_AND_THRESHOLDS.md"),
            File(cwd, "../docs/living-spec/DECISIONS_AND_THRESHOLDS.md"),
        ).first { it.isFile }
    }
}
