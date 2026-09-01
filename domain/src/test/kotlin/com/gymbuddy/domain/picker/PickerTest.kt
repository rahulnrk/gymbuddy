package com.gymbuddy.domain.picker

import com.gymbuddy.domain.catalog.CatalogTest
import com.gymbuddy.domain.intake.PainCharacter
import com.gymbuddy.domain.intake.PainReport
import com.gymbuddy.domain.programming.Goal
import com.gymbuddy.domain.programming.TrainingAge
import com.gymbuddy.domain.programming.UserContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PickerTest {
    private val catalog = CatalogTest.loadCatalog()
    private val squat = Slot("squat", SlotRole.PRIMARY, setOf("quad"))
    private val beginnerHome = PickContext(
        user = UserContext(Goal.HYPERTROPHY, TrainingAge.BEGINNER),
        equipment = setOf("none", "kettlebell", "dumbbell", "mat"),
    )

    @Test
    fun contraindicatedExerciseNeverRanks() {
        val ctx = fullGym().copy(
            user = UserContext(
                Goal.STRENGTH,
                TrainingAge.INTERMEDIATE,
                conditions = listOf("shoulder-impingement"),
            ),
        )
        val slot = Slot("vertical push", SlotRole.PRIMARY)
        val ranked = rank(slot, catalog, ctx)
        assertTrue(ranked.none { it.exercise.slug == "overhead-press" }, ranked.map { it.exercise.slug }.toString())
        val set = candidates(slot, catalog, ctx)
        assertTrue(set.exclusions.any { it.exercise.slug == "overhead-press" && it.exclusion is Exclusion.Contraindicated })
    }

    @Test
    fun sharpPainNeverRanks() {
        val ctx = fullGym().copy(
            user = UserContext(
                Goal.HYPERTROPHY,
                TrainingAge.INTERMEDIATE,
                sharpPainSlugs = setOf("overhead-press"),
            ),
            painReports = listOf(
                PainReport("shoulder", "on the press", setOf("overhead-press"), PainCharacter.SHARP),
            ),
        )
        val ranked = rank(Slot("vertical push", SlotRole.PRIMARY), catalog, ctx)
        assertTrue(ranked.none { it.exercise.slug == "overhead-press" })
    }

    @Test
    fun preferenceCannotPromoteAnExcludedExercise() {
        val ctx = fullGym().copy(
            user = UserContext(
                Goal.STRENGTH,
                TrainingAge.ADVANCED,
                conditions = listOf("shoulder-impingement"),
            ),
            acceptedSubstitutions = setOf("overhead-press", "arnold-press"),
        )
        val ranked = rank(Slot("vertical push", SlotRole.PRIMARY), catalog, ctx)
        assertTrue(ranked.none { it.exercise.slug == "overhead-press" })
        assertTrue(ranked.none { it.exercise.slug == "arnold-press" })
    }

    @Test
    fun kettlebellSquatSlotIsGobletNotBackSquat() {
        val ctx = PickContext(
            user = UserContext(Goal.HYPERTROPHY, TrainingAge.INTERMEDIATE),
            equipment = setOf("kettlebell", "none"),
        )
        val ranked = rank(squat, catalog, ctx)
        val slugs = ranked.map { it.exercise.slug }
        assertTrue("goblet-squat" in slugs, slugs.toString())
        assertFalse("back-squat" in slugs, slugs.toString())
        assertFalse("front-squat" in slugs, slugs.toString())
    }

    @Test
    fun rankingIsDeterministicAcrossOneHundredRuns() {
        val first = rank(squat, catalog, beginnerHome).map { it.exercise.slug }
        repeat(100) {
            assertEquals(first, rank(squat, catalog, beginnerHome).map { it.exercise.slug })
        }
    }

    @Test
    fun tiesBreakAlphabeticallyBySlug() {
        val ranked = rank(squat, catalog, fullGym())
        ranked.zipWithNext { a, b ->
            assertTrue(
                a.score > b.score || (a.score == b.score && a.exercise.slug < b.exercise.slug),
                "${a.exercise.slug} ${a.score} then ${b.exercise.slug} ${b.score}",
            )
        }
    }

    @Test
    fun everyScoreHasASpeakableReason() {
        rank(squat, catalog, beginnerHome).forEach { scored ->
            assertTrue(scored.reasons.isNotEmpty(), scored.exercise.slug)
            scored.reasons.forEach { reason ->
                assertSpeakableExplanation(reason.explanation, scored.exercise.slug)
            }
        }
    }

    @Test
    fun fatiguedPrimaryDropsBelowFreshEquivalent() {
        val fresh = rank(squat, catalog, beginnerHome).first { it.exercise.slug == "goblet-squat" }
        val tiredCtx = beginnerHome.copy(fatigue = mapOf("quad" to 1.0))
        val tired = rank(squat, catalog, tiredCtx).first { it.exercise.slug == "goblet-squat" }
        assertTrue(tired.score < fresh.score, "tired ${tired.score} fresh ${fresh.score}")
        assertTrue(tired.reasons.any { "fatig" in it.explanation.lowercase() })
    }

    @Test
    fun dislikeYieldsWhenAlternativesExistAndIsKeptWhenAlone() {
        val withAlts = fullGym().copy(dislikes = setOf("back-squat"))
        val set = candidates(squat, catalog, withAlts)
        assertTrue(set.survivors.none { it.slug == "back-squat" })
        assertTrue(set.exclusions.any { it.exclusion is Exclusion.Disliked && it.exercise.slug == "back-squat" })
        assertTrue(set.keptDespiteDislike.isEmpty())

        val onlyGoblet = beginnerHome.copy(dislikes = setOf("goblet-squat"))
        val kept = candidates(squat, catalog, onlyGoblet)
        assertTrue("goblet-squat" in kept.survivors.map { it.slug }, kept.survivors.map { it.slug }.toString())
        assertTrue("goblet-squat" in kept.keptDespiteDislike)
        assertTrue(rank(squat, catalog, onlyGoblet).any { it.exercise.slug == "goblet-squat" })
    }

    @Test
    fun emptyCandidateSetStillReturnsExclusions() {
        val ctx = PickContext(
            user = UserContext(Goal.HYPERTROPHY, TrainingAge.INTERMEDIATE),
            equipment = emptySet(),
        )
        val set = candidates(Slot("squat", SlotRole.PRIMARY), catalog, ctx)
        assertTrue(set.survivors.isEmpty())
        assertTrue(set.exclusions.isNotEmpty())
        assertTrue(set.exclusions.all { it.exclusion is Exclusion.MissingEquipment || it.exclusion is Exclusion.WrongPattern })
    }

    @Test
    fun dosePrecedenceCatalogThenUnspottedFloor() {
        val bench = catalog.bySlug("bench-press")!!
        val ctx = PickContext(
            user = UserContext(Goal.STRENGTH, TrainingAge.INTERMEDIATE),
            equipment = setOf("barbell", "plate", "bench-flat"),
        )
        val d = dose(bench, Slot("horizontal push", SlotRole.PRIMARY), ctx)
        assertTrue(d.repRange.min >= 6, d.repRange.toString())
        assertTrue(d.cappedBy.any { "catalog" in it.lowercase() && "reps" in it })
        assertTrue(d.cappedBy.any { "rack" in it.lowercase() && "6 reps" in it })
        assertTrue(d.cappedBy.any { "seconds" in it })
        val withRack = dose(
            bench,
            Slot("horizontal push", SlotRole.PRIMARY),
            ctx.copy(equipment = ctx.equipment + "squat-rack"),
        )
        assertTrue(withRack.cappedBy.none { "rack" in it.lowercase() && "6 reps" in it })
    }

    @Test
    fun rankReturnsExercisesNeverASession() {
        val ranked = rank(squat, catalog, beginnerHome)
        assertTrue(ranked.isNotEmpty())
        ranked.forEach {
            assertTrue(it.exercise.slug.isNotBlank())
            assertTrue(it.reasons.isNotEmpty())
        }
        val src = pickerMainDir().listFiles { f -> f.extension == "kt" }!!.joinToString("\n") { it.readText() }
        listOf("fun generatePlan", "fun generateWorkout", "fun buildWorkout", "PlannedSession").forEach { bad ->
            assertFalse(bad in src, bad)
        }
        assertFalse("kotlin.random" in src.lowercase())
        assertFalse("System.currentTimeMillis" in src)
        assertFalse("Clock" in src)
        assertEquals(61, catalog.exercises.size)
    }

    private fun fullGym(): PickContext = PickContext(
        user = UserContext(Goal.HYPERTROPHY, TrainingAge.INTERMEDIATE),
        equipment = catalog.equipment.keys,
    )
}

internal fun assertSpeakableExplanation(explanation: String, slug: String) {
    val banned = listOf(
        "skillDemand", "defaultRestSec", "targetMuscles", "recentSlugs",
        "muscleOverrides", "keptDespiteDislike", "setCredit", "repWindow",
    )
    banned.forEach { token ->
        assertFalse(token in explanation, "$slug explanation contains $token: $explanation")
    }
    assertFalse('_' in explanation, "$slug underscore: $explanation")
    val covered = Regex(
        """\d+(?:\.\d+)?(?:\s+to\s+\d+(?:\.\d+)?)?\s+(?:hard sets|sets|seconds|reps|times|time|percent)""",
        RegexOption.IGNORE_CASE,
    )
    val spans = covered.findAll(explanation).flatMap { it.range.toList() }.toSet()
    Regex("""\d+(?:\.\d+)?""").findAll(explanation).forEach { m ->
        assertTrue(
            m.range.any { it in spans },
            "$slug number '${m.value}' has no unit in: $explanation",
        )
    }
}

internal fun pickerMainDir(): java.io.File {
    val cwd = java.io.File("").absoluteFile
    return listOf(
        java.io.File(cwd, "src/main/kotlin/com/gymbuddy/domain/picker"),
        java.io.File(cwd, "domain/src/main/kotlin/com/gymbuddy/domain/picker"),
        java.io.File(cwd, "../src/main/kotlin/com/gymbuddy/domain/picker"),
    ).first { it.isDirectory }
}
