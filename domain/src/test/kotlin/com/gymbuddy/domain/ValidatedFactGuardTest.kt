package com.gymbuddy.domain

import com.gymbuddy.domain.validation.CorrectionFields
import com.gymbuddy.domain.validation.PreviousSetSnapshot
import com.gymbuddy.domain.validation.ValidatedFact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidatedFactGuardTest {

    @Test
    fun sealedFactsDoNotIncludeEngineLifecycle() {
        val names = ValidatedFact::class.java.declaredClasses.map { it.simpleName }.toSet()
        val banned = listOf(
            "SetStarted", "RestStarted", "RestEnded",
            "SessionInitialized", "SessionRehydrated",
            "WorkoutStarted", "WorkoutResumed", "WorkoutPaused", "WorkoutCompleted",
        )
        banned.forEach { b ->
            assertFalse(names.any { it.equals(b, ignoreCase = true) }, "illegal fact $b in $names")
        }
    }

    @Test
    fun setCompletedHasHintButNoOfficialSetNumber() {
        val props = ValidatedFact.SetCompleted::class.java.declaredFields.map { it.name }.toSet()
        assertTrue(props.any { it.contains("setNumberHint") }, props.toString())
        assertFalse("setNumber" in props, props.toString())
        assertTrue(props.any { it.contains("inheritWeight") })
        assertTrue(props.any { it.contains("loggingMode") })
        assertTrue(props.any { it.contains("batchId") })
    }

    @Test
    fun setCorrectedRequiresExistingSetIdAndMatchingPrevious() {
        val id = SetId("66666666-6666-4666-8666-666666666666")
        val ok = ValidatedFact.SetCorrected(
            setId = id,
            fields = CorrectionFields(reps = 8),
            previous = PreviousSetSnapshot(setId = id, reps = 10, weightKg = 60.0, alreadyAssignedSetNumber = 1),
        )
        assertEquals(id, ok.setId)
        assertFails {
            ValidatedFact.SetCorrected(
                setId = id,
                fields = CorrectionFields(reps = 8),
                previous = PreviousSetSnapshot(
                    setId = SetId("00000000-0000-4000-8000-000000000000"),
                    reps = 10,
                    weightKg = 60.0,
                ),
            )
        }
    }

    @Test
    fun setDeletedCannotBeAReplacementCompletedSet() {
        val id = SetId("66666666-6666-4666-8666-666666666666")
        val deleted = ValidatedFact.SetDeleted(
            setId = id,
            previous = PreviousSetSnapshot(id, 10, 60.0, 1),
            reason = "scratch that",
        )
        assertEquals("SetDeleted", deleted::class.java.simpleName)
        assertTrue(ValidatedFact.SetCompleted::class.java.simpleName != deleted::class.java.simpleName)
    }
}
