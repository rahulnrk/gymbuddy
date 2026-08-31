package com.gymbuddy.app.hud

import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.engine.EngineResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GymHudSessionTest {
    @Test
    fun startUsesExistingLifecycleSequenceAndManualSourceOnWorkoutStarted() {
        val (log, hud) = GymHudHarness.session()
        val r = hud.start()
        assertTrue(r is EngineResult.Committed)
        val events = log.sessionEvents(SessionId(GymHudHarness.SESSION))
        assertEquals(
            listOf(
                CommittedEventType.SESSION_INITIALIZED,
                CommittedEventType.WORKOUT_STARTED,
                CommittedEventType.EXERCISE_STARTED,
            ),
            events.map { it.type },
        )
        assertEquals(EventSource.SYSTEM, events[0].source)
        assertEquals(EventSource.MANUAL, events[1].source)
        assertEquals(EventSource.SYSTEM, events[2].source)
        assertEquals("Bench Press", hud.ui().exerciseName)
        assertEquals(LifecycleAction.PAUSE, hud.ui().lifecycleAction)
    }

    @Test
    fun duplicateStartIsNoOp() {
        val (log, hud) = GymHudHarness.session()
        hud.start()
        val n = log.sessionEvents(SessionId(GymHudHarness.SESSION)).size
        val second = hud.start()
        assertTrue(second is EngineResult.Committed)
        assertEquals(0, (second as EngineResult.Committed).events.size)
        assertEquals(n, log.sessionEvents(SessionId(GymHudHarness.SESSION)).size)
    }

    @Test
    fun pauseResumeAreNotWorkoutStarted() {
        val (log, hud) = GymHudHarness.session()
        hud.start()
        hud.pause()
        assertEquals(WorkoutSessionState.PAUSED, hud.snapshot().phase)
        assertEquals(LifecycleAction.RESUME, hud.ui().lifecycleAction)
        hud.resume()
        val types = log.sessionEvents(SessionId(GymHudHarness.SESSION)).map { it.type }
        assertTrue(CommittedEventType.WORKOUT_PAUSED in types)
        assertTrue(CommittedEventType.WORKOUT_RESUMED in types)
        assertEquals(1, types.count { it == CommittedEventType.WORKOUT_STARTED })
        assertEquals(EventSource.MANUAL, log.sessionEvents(SessionId(GymHudHarness.SESSION)).first { it.type == CommittedEventType.WORKOUT_PAUSED }.source)
        assertEquals(EventSource.MANUAL, log.sessionEvents(SessionId(GymHudHarness.SESSION)).first { it.type == CommittedEventType.WORKOUT_RESUMED }.source)
    }

    @Test
    fun numberPadCommitsSetCompletedAsManual() {
        val (log, hud) = GymHudHarness.session()
        hud.start()
        val r = hud.logManualSet(reps = 10, weightKg = 60.0)
        assertTrue(r is EngineResult.Committed, r.toString())
        val sets = log.sessionEvents(SessionId(GymHudHarness.SESSION)).filter { it.type == CommittedEventType.SET_COMPLETED }
        assertEquals(1, sets.size)
        assertEquals(EventSource.MANUAL, sets[0].source)
        val ui = hud.ui()
        assertEquals("60 × 10", ui.previousPerformance)
        assertEquals("60 kg", ui.weightLabel)
        assertTrue(ui.setPills.any { !it.todo && "10" in it.text })
        assertEquals("Recovery", ui.pocket.label)
        assertTrue(ui.rest.visible)
        assertTrue(ui.numberPadEnabled)
        assertTrue(ui.undo.visible)
    }
}
