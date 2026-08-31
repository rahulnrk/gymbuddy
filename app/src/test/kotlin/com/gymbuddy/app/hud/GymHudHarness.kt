package com.gymbuddy.app.hud

import com.gymbuddy.domain.engine.InMemoryEventLog
import com.gymbuddy.domain.engine.PlannedExercise
import com.gymbuddy.domain.engine.WorkoutEngine
import com.gymbuddy.domain.engine.WorkoutPlan

internal object GymHudHarness {
    const val USER = "11111111-1111-4111-8111-111111111111"
    const val SESSION = "22222222-2222-4222-8222-222222222222"
    const val WORKOUT = "33333333-3333-4333-8333-333333333333"
    const val BENCH = "44444444-4444-4444-8444-444444444444"
    const val INCLINE = "44444444-4444-4444-8444-444444444445"
    const val WE1 = "55555555-5555-4555-8555-555555555555"
    const val WE2 = "55555555-5555-4555-8555-555555555556"

    val names = mapOf(
        BENCH to "Bench Press",
        INCLINE to "Incline Press",
    )

    fun plan(): WorkoutPlan = WorkoutPlan(
        WORKOUT,
        listOf(
            PlannedExercise(WE1, BENCH, plannedSets = 4, restTargetSec = 90),
            PlannedExercise(WE2, INCLINE, plannedSets = 3, restTargetSec = 90),
        ),
    )

    fun session(): Pair<InMemoryEventLog, GymHudSession> {
        val log = InMemoryEventLog()
        val hud = GymHudSession(
            engine = WorkoutEngine(log),
            plan = plan(),
            userId = USER,
            sessionId = SESSION,
            exerciseNames = names,
        )
        return log to hud
    }
}
