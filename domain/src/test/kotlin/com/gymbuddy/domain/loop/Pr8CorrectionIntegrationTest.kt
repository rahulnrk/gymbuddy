package com.gymbuddy.domain.loop

import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.engine.InMemoryEventLog
import com.gymbuddy.domain.engine.PlannedExercise
import com.gymbuddy.domain.engine.WorkoutEngine
import com.gymbuddy.domain.engine.WorkoutPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Thin PR 8: extractor → validator → engine. No second correction implementation. */
class Pr8CorrectionIntegrationTest {
    @Test
    fun no8SamePhysicalSetId() {
        val log = InMemoryEventLog()
        val loop = CompanionLoop(
            WorkoutEngine(log), log,
            WorkoutPlan(
                "33333333-3333-4333-8333-333333333333",
                listOf(
                    PlannedExercise("55555555-5555-4555-8555-555555555555", "44444444-4444-4444-8444-444444444444", 4),
                ),
            ),
            "11111111-1111-4111-8111-111111111111",
            "22222222-2222-4222-8222-222222222222",
        )
        loop.start()
        loop.seedWorkingWeight(60.0)
        val logged = loop.handleTurn("Got 10.")
        val id = logged.snapshot.liveSetsOnCurrent().single().setId
        val corrected = loop.handleTurn("No, 8.")
        assertTrue(corrected.extraction.classifiedBeforeExtract)
        assertTrue(corrected.committed.any { it.type == CommittedEventType.SET_CORRECTED })
        assertEquals(id, corrected.snapshot.liveSetsOnCurrent().single().setId)
        assertEquals(8, corrected.snapshot.liveSetsOnCurrent().single().reps)
        assertEquals(1, corrected.snapshot.liveSetsOnCurrent().size)
    }
}
