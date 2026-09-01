package com.gymbuddy.domain.programming

import com.gymbuddy.domain.catalog.CatalogTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProgrammingRulesTest {
    private val catalog = CatalogTest.loadCatalog()

    @Test
    fun publicRuleFunctionsCarryCompleteProvenance() {
        Goal.entries.forEach { goal ->
            TrainingAge.entries.forEach { age ->
                val volume = weeklyHardSets(goal, age)
                assertCompleteProvenance(volume.basis)
                val e = effort(goal, age)
                assertCompleteProvenance(e.provenance)
                val freq = weeklyFrequency(goal, age)
                assertCompleteProvenance(freq.provenance)
            }
            val reps = repRange(goal)
            assertCompleteProvenance(reps.provenance)
            ExerciseKind.entries.forEach { kind ->
                TrainingAge.entries.forEach { age ->
                    assertCompleteProvenance(restSeconds(goal, kind, age).provenance)
                }
            }
        }
        assertCompleteProvenance(deloadCutPercent().provenance)
        assertCompleteProvenance(reportedEffortConfidence(2, 8).provenance)
        val bench = catalog.bySlug("bench-press")!!
        assertCompleteProvenance(effectiveRestSeconds(bench, Goal.HYPERTROPHY).provenance)
        assertCompleteProvenance(effectiveRepRange(bench, Goal.HYPERTROPHY).provenance)
        val advice = cardioPlacement(SessionSummary(listOf("squat")), Goal.STRENGTH)
        assertCompleteProvenance(advice.provenance)
    }

    @Test
    fun beginnerHypertrophyVolumeIsStrictlyBelowIntermediate() {
        val beginner = weeklyHardSets(Goal.HYPERTROPHY, TrainingAge.BEGINNER)
        val intermediate = weeklyHardSets(Goal.HYPERTROPHY, TrainingAge.INTERMEDIATE)
        assertTrue(beginner.min < intermediate.min, "${beginner.min} vs ${intermediate.min}")
        assertTrue(beginner.max < intermediate.max, "${beginner.max} vs ${intermediate.max}")
        assertEquals(ProgrammingConstants.HYPERTROPHY_INTERMEDIATE_HARD_SETS_MIN, intermediate.min)
        assertEquals(ProgrammingConstants.HYPERTROPHY_INTERMEDIATE_HARD_SETS_MAX, intermediate.max)
    }

    @Test
    fun catalogRestAndRepsWinOverGoalDefaults() {
        val bench = catalog.bySlug("bench-press")!!
        val fly = catalog.bySlug("cable-fly")!!
        val goalRest = restSeconds(Goal.HYPERTROPHY, ExerciseKind.COMPOUND)
        val effectiveBenchRest = effectiveRestSeconds(bench, Goal.HYPERTROPHY)
        assertEquals(bench.defaultRestSec, effectiveBenchRest.min)
        assertEquals(bench.defaultRestSec, effectiveBenchRest.max)
        assertNotEquals(goalRest, effectiveBenchRest)

        val strengthRest = restSeconds(Goal.STRENGTH, ExerciseKind.ISOLATION, TrainingAge.INTERMEDIATE)
        val effectiveFlyRest = effectiveRestSeconds(fly, Goal.STRENGTH)
        assertEquals(fly.defaultRestSec, effectiveFlyRest.min)
        assertTrue(effectiveFlyRest.min !in strengthRest.min..strengthRest.max || effectiveFlyRest.min == effectiveFlyRest.max)
        assertEquals(fly.defaultRestSec, effectiveFlyRest.max)

        val goalReps = repRange(Goal.STRENGTH)
        val spotted = effectiveRepRange(bench, Goal.STRENGTH, listOf("squat-rack"))
        assertEquals(bench.repWindow.min, spotted.min)
        assertEquals(bench.repWindow.max, spotted.max)
        assertNotEquals(goalReps.max, spotted.max)
        val unspotted = effectiveRepRange(bench, Goal.STRENGTH)
        assertEquals(ProgrammingConstants.STRENGTH_UNSPOTTED_REP_FLOOR, unspotted.min)
    }

    @Test
    fun doubleProgressionFillAddLoadShortAddRepsTechniqueHold() {
        val target = Prescription(
            reps = range(6, 12, ProgrammingProvenance.NASM_OPT_42),
            sets = 3,
            effort = Effort(2, 4, ProgrammingProvenance.GUIDE_43),
            loadStepKg = 5.0,
        )
        val filled = listOf(
            SetPerformance(reps = 12, rir = 3),
            SetPerformance(reps = 12, rir = 2),
            SetPerformance(reps = 12, rir = 2),
        )
        val addLoad = nextSession(filled, target)
        assertIs<ProgressionDecision.AddLoadKg>(addLoad)
        assertEquals(5.0, addLoad.delta)

        val short = listOf(
            SetPerformance(reps = 8, rir = 3),
            SetPerformance(reps = 8, rir = 3),
            SetPerformance(reps = 7, rir = 3),
        )
        val addReps = nextSession(short, target)
        assertIs<ProgressionDecision.AddReps>(addReps)
        assertEquals(8, addReps.to)

        val degradedLast = listOf(
            SetPerformance(reps = 12, rir = 2),
            SetPerformance(reps = 12, rir = 2),
            SetPerformance(reps = 12, rir = 2, techniqueBroken = true),
        )
        val hold = nextSession(degradedLast, target)
        assertTrue(hold is ProgressionDecision.Hold || hold is ProgressionDecision.RegressExercise)
        assertFalse(hold is ProgressionDecision.AddLoadKg)

        val mostlyBroken = listOf(
            SetPerformance(reps = 12, techniqueBroken = true),
            SetPerformance(reps = 10, techniqueBroken = true),
            SetPerformance(reps = 8, techniqueBroken = false),
        )
        val regress = nextSession(mostlyBroken, target)
        assertTrue(regress is ProgressionDecision.Hold || regress is ProgressionDecision.RegressExercise)
        assertFalse(regress is ProgressionDecision.AddLoadKg)
    }

    @Test
    fun progressionChangesAtMostOneVariable() {
        val target = Prescription(
            reps = range(6, 12, ProgrammingProvenance.NASM_OPT_42),
            sets = 3,
            effort = Effort(2, 4, ProgrammingProvenance.GUIDE_43),
            loadStepKg = 2.5,
        )
        val decisions = listOf(
            nextSession(listOf(SetPerformance(12), SetPerformance(12), SetPerformance(12)), target),
            nextSession(listOf(SetPerformance(8), SetPerformance(8)), target),
            nextSession(listOf(SetPerformance(10, techniqueBroken = true)), target),
            nextSession(emptyList(), target),
        )
        decisions.forEach { d ->
            val variables = listOf(
                d is ProgressionDecision.AddReps,
                d is ProgressionDecision.AddLoadKg,
                d is ProgressionDecision.AddSet,
            ).count { it }
            assertTrue(variables <= 1, "changed $variables variables: $d")
        }
    }

    @Test
    fun deloadDueByWeekEightAndEarlierWithPoorSignals() {
        val quiet = RecoverySignals()
        assertFalse(deloadDue(0, quiet))
        assertFalse(deloadDue(3, quiet))
        assertFalse(deloadDue(7, quiet))
        assertTrue(deloadDue(8, quiet))
        assertTrue(deloadDue(9, quiet))

        val poor = RecoverySignals(sleepPoor = true)
        assertFalse(deloadDue(3, poor))
        assertTrue(deloadDue(4, poor))
        assertTrue(deloadDue(6, RecoverySignals(painReported = true)))
        assertTrue(deloadDue(4, RecoverySignals(motivationLow = true)))
        assertFalse(deloadDue(4, RecoverySignals(sleepPoor = false, painReported = false, motivationLow = false)))
    }

    @Test
    fun cardioPlacementAvoidsHardIntervalsOnHeavyLower() {
        val squatDay = cardioPlacement(SessionSummary(listOf("squat", "hinge")), Goal.STRENGTH)
        assertTrue(squatDay.avoidHardIntervals)
        assertTrue("interval" in squatDay.message.lowercase() || "sprint" in squatDay.message.lowercase())

        val pushDay = cardioPlacement(SessionSummary(listOf("horizontal push")), Goal.HYPERTROPHY)
        assertFalse(pushDay.avoidHardIntervals)
    }

    @Test
    fun orderScorePutsCompoundBeforeIsolationAndCoreLast() {
        val bench = catalog.bySlug("bench-press")!!
        val fly = catalog.bySlug("cable-fly")!!
        val plank = catalog.bySlug("plank")!!
        assertTrue(orderScore(bench) > orderScore(fly), "bench ${orderScore(bench)} fly ${orderScore(fly)}")
        assertTrue(orderScore(fly) > orderScore(plank), "fly ${orderScore(fly)} plank ${orderScore(plank)}")

        val bad = validateOrder(listOf(fly, bench))
        assertTrue(bad.any { it.rule == "order.freshness" })
        val good = validateOrder(listOf(bench, fly, plank))
        assertTrue(good.none { it.rule == "order.freshness" })

        val gm = catalog.bySlug("dumbbell-good-morning")!!
        val chin = catalog.bySlug("chin-up")!!
        assertTrue(orderClass(chin) == orderClass(gm))
        assertTrue(validateOrder(listOf(gm, chin)).none { it.rule == "order.freshness" })

        val calf = catalog.bySlug("calf-raise")!!
        assertTrue(orderClass(calf) > orderClass(plank))
        assertTrue(validateOrder(listOf(plank, calf)).any { it.rule == "order.freshness" })
    }

    @Test
    fun beginnerEffortMatchesTrainedAndDoesNotUseASeparateRirBand() {
        val beginner = effort(Goal.STRENGTH, TrainingAge.BEGINNER)
        val trained = effort(Goal.STRENGTH, TrainingAge.INTERMEDIATE)
        assertEquals(ProgrammingConstants.MOST_WORK_RIR_MIN, beginner.rirMin)
        assertEquals(ProgrammingConstants.MOST_WORK_RIR_MAX, beginner.rirMax)
        assertEquals(beginner.rirMin, trained.rirMin)
        assertEquals(beginner.rirMax, trained.rirMax)
    }

    @Test
    fun reportedEffortConfidenceTrustsCloseRirOnShortSetsOnly() {
        assertEquals(EffortConfidence.TRUST, reportedEffortConfidence(2, 8).confidence)
        assertEquals(EffortConfidence.TRUST, reportedEffortConfidence(3, 12).confidence)
        assertEquals(EffortConfidence.LOW, reportedEffortConfidence(4, 8).confidence)
        assertEquals(EffortConfidence.LOW, reportedEffortConfidence(2, 13).confidence)
        assertEquals(EffortConfidence.LOW, reportedEffortConfidence(5, 20).confidence)
    }

    @Test
    fun lowConfidenceRirDoesNotDriveLoadChangeOnItsOwn() {
        val longTarget = Prescription(
            reps = range(6, 20, ProgrammingProvenance.NASM_OPT_42),
            sets = 2,
            effort = Effort(2, 4, ProgrammingProvenance.ACSM_2026),
            loadStepKg = 5.0,
        )
        val longClaimedFailure = listOf(
            SetPerformance(reps = 15, rir = 0),
            SetPerformance(reps = 14, rir = 0),
        )
        val fromLongSet = nextSession(longClaimedFailure, longTarget)
        assertIs<ProgressionDecision.AddReps>(fromLongSet)

        val shortTarget = Prescription(
            reps = range(6, 12, ProgrammingProvenance.NASM_OPT_42),
            sets = 2,
            effort = Effort(2, 4, ProgrammingProvenance.ACSM_2026),
            loadStepKg = 5.0,
        )
        val easyRirNotFilled = listOf(
            SetPerformance(reps = 8, rir = 5),
            SetPerformance(reps = 8, rir = 4),
        )
        val fromEasyRir = nextSession(easyRirNotFilled, shortTarget)
        assertIs<ProgressionDecision.AddReps>(fromEasyRir)
        assertFalse(fromEasyRir is ProgressionDecision.AddLoadKg)
    }

    @Test
    fun fatLossInheritsHypertrophyRepsAndRest() {
        val hypertReps = repRange(Goal.HYPERTROPHY)
        val fatReps = repRange(Goal.FAT_LOSS)
        assertEquals(hypertReps.min, fatReps.min)
        assertEquals(hypertReps.max, fatReps.max)
        val hypertRest = restSeconds(Goal.HYPERTROPHY, ExerciseKind.COMPOUND, TrainingAge.INTERMEDIATE)
        val fatRest = restSeconds(Goal.FAT_LOSS, ExerciseKind.COMPOUND, TrainingAge.INTERMEDIATE)
        assertEquals(hypertRest.min, fatRest.min)
        assertEquals(hypertRest.max, fatRest.max)
        assertEquals(ProgrammingConstants.HYPERTROPHY_REST_SEC_MIN, fatRest.min)
        assertEquals(ProgrammingConstants.HYPERTROPHY_REST_SEC_MAX, fatRest.max)
    }

    @Test
    fun strengthRestDependsOnTrainingAgeAndKind() {
        val beginner = restSeconds(Goal.STRENGTH, ExerciseKind.COMPOUND, TrainingAge.BEGINNER)
        assertEquals(ProgrammingConstants.STRENGTH_REST_SEC_BEGINNER_MIN, beginner.min)
        assertEquals(ProgrammingConstants.STRENGTH_REST_SEC_BEGINNER_MAX, beginner.max)
        val isolation = restSeconds(Goal.STRENGTH, ExerciseKind.ISOLATION, TrainingAge.INTERMEDIATE)
        assertEquals(ProgrammingConstants.STRENGTH_ISOLATION_REST_SEC_MIN, isolation.min)
        assertEquals(ProgrammingConstants.STRENGTH_ISOLATION_REST_SEC_MAX, isolation.max)
        val compound = restSeconds(Goal.STRENGTH, ExerciseKind.COMPOUND, TrainingAge.INTERMEDIATE)
        assertEquals(ProgrammingConstants.STRENGTH_REST_SEC_MIN, compound.min)
        assertEquals(ProgrammingConstants.STRENGTH_REST_SEC_MAX, compound.max)
        val beginnerIsolation = restSeconds(Goal.STRENGTH, ExerciseKind.ISOLATION, TrainingAge.BEGINNER)
        assertEquals(beginner.min, beginnerIsolation.min)
        assertEquals(beginner.max, beginnerIsolation.max)
    }

    @Test
    fun unspottedHighConsequenceFloorsAtSixUnlessRackPresent() {
        val bench = catalog.bySlug("bench-press")!!
        val deadlift = catalog.bySlug("deadlift")!!
        assertEquals(6, unspottedRepFloor(bench, emptyList()))
        assertEquals(null, unspottedRepFloor(bench, listOf("squat-rack")))
        assertEquals(null, unspottedRepFloor(bench, listOf("power-rack")))
        assertEquals(null, unspottedRepFloor(deadlift, emptyList()))
        assertEquals(5, repRange(Goal.STRENGTH).min)
        assertEquals(8, repRange(Goal.STRENGTH).max)
        assertEquals(6, effectiveRepRange(bench, Goal.STRENGTH, emptyList()).min)
        assertEquals(5, effectiveRepRange(bench, Goal.STRENGTH, listOf("smith-machine")).min)
    }

    @Test
    fun nextSessionDoesNotHardcodeLoadStep() {
        val target = Prescription(
            reps = range(5, 5, ProgrammingProvenance.STRENGTH_REPS),
            sets = 3,
            effort = Effort(2, 4, ProgrammingProvenance.GUIDE_43),
            loadStepKg = 5.0,
        )
        val filled = listOf(SetPerformance(5), SetPerformance(5), SetPerformance(5))
        val decision = nextSession(filled, target)
        assertEquals(ProgressionDecision.AddLoadKg(5.0), decision)
    }
}
