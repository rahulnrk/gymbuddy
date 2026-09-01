package com.gymbuddy.domain.programming

import com.gymbuddy.domain.catalog.CatalogTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgrammingValidatorTest {
    private val catalog = CatalogTest.loadCatalog()

    private val intermediateHypertrophy = UserContext(
        goal = Goal.HYPERTROPHY,
        trainingAge = TrainingAge.INTERMEDIATE,
    )

    @Test
    fun validateWeekFlagsPushOnlyWeek() {
        val bench = planned("bench-press", 4)
        val press = planned("overhead-press", 4)
        val fly = planned("cable-fly", 3)
        val week = listOf(PlannedSession(listOf(bench, press, fly)))
        val hits = validateWeek(week, intermediateHypertrophy, catalog)
        assertTrue(hits.any { it.rule == "pattern.imbalance.push-only" }, hits.toString())
        assertTrue(hits.any { it.severity == Severity.WARN && "push" in it.message.lowercase() })
    }

    @Test
    fun contraindicatedExerciseIsBlock() {
        val press = planned("overhead-press", 3)
        val user = intermediateHypertrophy.copy(conditions = listOf("shoulder-impingement"))
        val hits = validateSession(PlannedSession(listOf(press)), user, catalog)
        val block = hits.filter { it.rule == "safety.contraindication" }
        assertTrue(block.isNotEmpty(), hits.toString())
        assertTrue(block.all { it.severity == Severity.BLOCK })
        assertTrue(block.all { "swap" in it.message.lowercase() || "poor match" in it.message.lowercase() })
        assertTrue(block.none { it.severity != Severity.BLOCK })
    }

    @Test
    fun unilateralSetsCountPerSideNotHalved() {
        val lunges = planned("walking-lunge", 3)
        val week = listOf(PlannedSession(listOf(lunges)))
        val tally = hardSetsByMuscle(week, catalog)
        val glute = tally["glute"]
        val quad = tally["quad"]
        assertEquals(3.0, glute, tally.toString())
        assertEquals(3.0, quad, tally.toString())
        assertTrue((glute ?: 0.0) > 1.5, "primary glute must not be halved: $tally")
    }

    @Test
    fun fractionalSetCountingCreditsIndirectAsHalf() {
        val week = listOf(PlannedSession(listOf(planned("bench-press", 4))))
        val tally = hardSetsByMuscle(week, catalog)
        assertEquals(4.0, tally["chest_mid"], tally.toString())
        assertEquals(2.0, tally["triceps"], tally.toString())
        assertEquals(2.0, tally["delt_front"], tally.toString())
        assertEquals(2.0, tally["chest_upper"], tally.toString())
        assertFalse("serratus" in tally, tally.toString())
        assertEquals(1.0, setCredit(1.0))
        assertEquals(1.0, setCredit(0.75))
        assertEquals(0.5, setCredit(0.74))
        assertEquals(0.5, setCredit(0.30))
        assertEquals(0.0, setCredit(0.29))
        assertEquals(0.0, setCredit(0.2))
    }

    @Test
    fun violationMessagesAreSpeakable() {
        val bench = planned("bench-press", 28)
        val press = planned("overhead-press", 8)
        val fly = planned("cable-fly", 6, prescribedRestSec = 10, prescribedRepMin = 25, prescribedRepMax = 30)
        val user = intermediateHypertrophy.copy(conditions = listOf("shoulder-impingement"))
        val week = listOf(PlannedSession(listOf(bench, press, fly)))
        val hits = validateWeek(week, user, catalog)
        assertTrue(hits.isNotEmpty())
        hits.forEach { v ->
            assertSpeakable(v.message)
            assertFalse(v.message.contains(v.rule), "message repeats rule id: ${v.message}")
        }
    }

    @Test
    fun validatorReturnsViolationsAndDoesNotMutatePlan() {
        val original = PlannedSession(
            listOf(
                planned("bench-press", 4),
                planned("barbell-row", 4),
            ),
        )
        val before = original.copy(exercises = original.exercises.toList())
        val sessionHits = validateSession(original, intermediateHypertrophy, catalog)
        assertEquals(before, original)
        assertEquals(before.exercises, original.exercises)
        val weekBefore = listOf(original)
        val weekHits = validateWeek(weekBefore, intermediateHypertrophy, catalog)
        assertEquals(listOf(before), weekBefore)
        // Return type is List<Violation>, never a plan. Input must be bit-identical.
        assertEquals(before.exercises.map { it.hardSets }, original.exercises.map { it.hardSets })
        assertTrue(sessionHits.none { it.message.isBlank() })
        assertTrue(weekHits.none { it.message.isBlank() })
    }

    @Test
    fun catalogJustifiedRestIsNotAViolation() {
        val bench = planned(
            "bench-press",
            3,
            prescribedRestSec = 180,
            prescribedRepMin = 5,
            prescribedRepMax = 12,
        )
        val hits = validateSession(
            PlannedSession(listOf(bench)),
            UserContext(Goal.ENDURANCE, TrainingAge.INTERMEDIATE, equipment = listOf("squat-rack")),
            catalog,
        )
        assertTrue(hits.none { it.rule == "rest.out-of-range" }, hits.toString())
        assertTrue(hits.none { it.rule == "reps.out-of-range" }, hits.toString())
        val goalRest = restSeconds(Goal.ENDURANCE, ExerciseKind.COMPOUND)
        assertFalse(goalRest.contains(180))
        assertFalse(repRange(Goal.ENDURANCE).contains(5))
    }

    @Test
    fun balancedWeekIsNotPushOnly() {
        val week = listOf(
            PlannedSession(listOf(planned("bench-press", 4), planned("barbell-row", 4))),
        )
        val hits = validateWeek(week, intermediateHypertrophy, catalog)
        assertTrue(hits.none { it.rule.startsWith("pattern.imbalance") }, hits.toString())
    }

    @Test
    fun unspottedBelowFloorIsWarnAndDoesNotMutate() {
        val bench = planned("bench-press", 3, prescribedRepMin = 5, prescribedRepMax = 5)
        val original = PlannedSession(listOf(bench))
        val before = original.copy(exercises = original.exercises.toList())
        val hits = validateSession(original, intermediateHypertrophy, catalog)
        val floorHits = hits.filter { it.rule == "reps.unspotted-floor" }
        assertTrue(floorHits.any { it.severity == Severity.WARN }, hits.toString())
        floorHits.forEach { assertSpeakable(it.message) }
        assertEquals(before, original)
        val withRack = validateSession(
            original,
            intermediateHypertrophy.copy(equipment = listOf("squat-rack")),
            catalog,
        )
        assertTrue(withRack.none { it.rule == "reps.unspotted-floor" }, withRack.toString())
        val deadlift = planned("deadlift", 3, prescribedRepMin = 3, prescribedRepMax = 5)
        val hingeHits = validateSession(
            PlannedSession(listOf(deadlift)),
            intermediateHypertrophy,
            catalog,
        )
        assertTrue(hingeHits.none { it.rule == "reps.unspotted-floor" }, hingeHits.toString())
    }

    @Test
    fun programmingPackageDoesNotGenerateWorkouts() {
        val dir = programmingMainDir()
        val body = dir.listFiles { f -> f.extension == "kt" }!!.joinToString("\n") { it.readText() }
        listOf("fun generatePlan", "fun generateWorkout", "fun buildWorkout", "fun createPlan", "fun suggestWorkout").forEach { bad ->
            assertFalse(bad in body, "programming package contains $bad")
        }
        assertEquals(61, catalog.exercises.size)
    }

    private fun planned(
        slug: String,
        hardSets: Int,
        prescribedRestSec: Int? = null,
        prescribedRepMin: Int? = null,
        prescribedRepMax: Int? = null,
    ): PlannedExercise = PlannedExercise(
        exercise = catalog.bySlug(slug)!!,
        hardSets = hardSets,
        prescribedRestSec = prescribedRestSec,
        prescribedRepMin = prescribedRepMin,
        prescribedRepMax = prescribedRepMax,
    )
}

internal fun assertSpeakable(message: String) {
    assertFalse(message.contains('_'), "snake_case in message: $message")
    assertFalse(Regex("""\b[a-z]+(?:\.[a-z0-9-]+)+""").containsMatchIn(message), "rule id in message: $message")
    val covered = Regex(
        """\d+(?:\.\d+)?(?:\s+to\s+\d+(?:\.\d+)?)?\s+(?:hard sets|sets|seconds|reps|rep window|times|time|training days|training day)""",
        RegexOption.IGNORE_CASE,
    )
    val coveredSpans = covered.findAll(message).flatMap { it.range.toList() }.toSet()
    Regex("""\d+(?:\.\d+)?""").findAll(message).forEach { m ->
        assertTrue(
            m.range.any { it in coveredSpans },
            "number '${m.value}' has no unit in: $message",
        )
    }
}

internal fun programmingMainDir(): java.io.File {
    val cwd = java.io.File("").absoluteFile
    return listOf(
        java.io.File(cwd, "src/main/kotlin/com/gymbuddy/domain/programming"),
        java.io.File(cwd, "domain/src/main/kotlin/com/gymbuddy/domain/programming"),
        java.io.File(cwd, "../src/main/kotlin/com/gymbuddy/domain/programming"),
    ).first { it.isDirectory }
}
