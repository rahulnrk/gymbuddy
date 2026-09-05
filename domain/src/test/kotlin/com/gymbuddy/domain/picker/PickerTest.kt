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
    fun flexionIntolerantUserIsOfferedBenchPress() {
        val ctx = fullGym().copy(
            user = UserContext(
                Goal.STRENGTH,
                TrainingAge.INTERMEDIATE,
                conditions = listOf("flexion-intolerant-back"),
            ),
        )
        val offered = catalog.exercises.filter { "flexion-intolerant-back" !in it.contraindications }
        assertEquals(63, offered.size)
        val slot = Slot("horizontal push", SlotRole.PRIMARY)
        val set = candidates(slot, catalog, ctx)
        assertTrue(set.survivors.any { it.slug == "bench-press" })
        assertTrue(
            set.exclusions.none {
                it.exercise.slug == "bench-press" && it.exclusion is Exclusion.Contraindicated
            },
        )
        val ranked = rank(slot, catalog, ctx)
        assertTrue(ranked.any { it.exercise.slug == "bench-press" }, ranked.map { it.exercise.slug }.toString())
    }

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
    fun tiesBreakTowardLowerConsequenceThenSkillThenSlugAmongEquivalents() {
        val gm = catalog.bySlug("dumbbell-good-morning")!!
        val rdl = catalog.bySlug("romanian-deadlift")!!
        assertEquals("freeweight", implementClass(gm))
        assertEquals("freeweight", implementClass(rdl))
        assertTrue(demandRank(gm.consequenceOfFailure) < demandRank(rdl.consequenceOfFailure))
        val tied = listOf(
            ScoredExercise(rdl, 10.0, emptyList()),
            ScoredExercise(gm, 10.0, emptyList()),
        ).sortedWith(scoredComparator)
        assertEquals("dumbbell-good-morning", tied[0].exercise.slug)

        val db = catalog.bySlug("dumbbell-bench-press")!!
        val machine = catalog.bySlug("machine-chest-press")!!
        val pushUp = catalog.bySlug("push-up")!!
        assertEquals("freeweight", implementClass(db))
        assertEquals("machine", implementClass(machine))
        assertEquals("bodyweight", implementClass(pushUp))
        val presses = listOf(
            ScoredExercise(machine, 10.0, emptyList()),
            ScoredExercise(pushUp, 10.0, emptyList()),
            ScoredExercise(db, 10.0, emptyList()),
        ).sortedWith(scoredComparator)
        assertEquals("dumbbell-bench-press", presses[0].exercise.slug)

        val ranked = rank(squat, catalog, fullGym())
        ranked.zipWithNext { a, b ->
            val cmp = scoredComparator.compare(a, b)
            assertTrue(cmp <= 0, "${a.exercise.slug} ${a.score} then ${b.exercise.slug} ${b.score}")
        }
    }

    @Test
    fun beginnerHingePrefersDumbbellGoodMorningOnScoreNotSlug() {
        val ctx = PickContext(
            user = UserContext(Goal.HYPERTROPHY, TrainingAge.BEGINNER),
            equipment = catalog.equipment.keys,
        )
        val ranked = rank(Slot("hinge", SlotRole.PRIMARY), catalog, ctx)
        val gm = ranked.first { it.exercise.slug == "dumbbell-good-morning" }
        val rdl = ranked.first { it.exercise.slug == "romanian-deadlift" }
        assertTrue(
            gm.score > rdl.score,
            "RDL ${rdl.score} vs GM ${gm.score}; slug must not be why GM wins",
        )
        println("===== hinge rank beginner full gym =====")
        ranked.take(8).forEach {
            println(
                "${it.exercise.slug} score=${it.score} " +
                    "cof=${it.exercise.consequenceOfFailure} skill=${it.exercise.skillDemand}",
            )
        }
        assertEquals("dumbbell-good-morning", ranked.first().exercise.slug)
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
    fun gluteFatigueRanksGobletAboveBackSquat() {
        // close-grip bench is pattern "triceps", so it never shares a horizontal-push
        // slot with bench. Freshness still changes order when primaries differ:
        // back squat lists glute as a primary; goblet does not.
        val slot = Slot("squat", SlotRole.PRIMARY, setOf("quad"))
        val gym = fullGym()
        val fresh = rank(slot, catalog, gym).map { it.exercise.slug }
        val tired = rank(slot, catalog, gym.copy(fatigue = mapOf("glute" to 1.0))).map { it.exercise.slug }
        assertTrue("back-squat" in fresh && "goblet-squat" in fresh, fresh.toString())
        assertTrue("back-squat" in tired && "goblet-squat" in tired, tired.toString())
        assertTrue(fresh != tired, "fresh $fresh tired $tired")
        assertTrue(fresh.indexOf("back-squat") < tired.indexOf("back-squat"), "fresh $fresh tired $tired")
        assertTrue(tired.indexOf("goblet-squat") < tired.indexOf("back-squat"), tired.toString())
    }

    @Test
    fun dislikeYieldsWhenAlternativesExistAndIsKeptWhenAlone() {
        val withAlts = fullGym().copy(dislikes = setOf("back-squat"))
        val set = candidates(squat, catalog, withAlts)
        assertTrue(set.survivors.none { it.slug == "back-squat" })
        assertTrue(set.exclusions.any { it.exclusion is Exclusion.Disliked && it.exercise.slug == "back-squat" })
        assertTrue(set.keptDespiteDislike.isEmpty())

        val onlyGoblet = PickContext(
            user = UserContext(Goal.HYPERTROPHY, TrainingAge.BEGINNER),
            equipment = setOf("kettlebell"),
            dislikes = setOf("goblet-squat"),
        )
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
        assertEquals(77, catalog.exercises.size)
    }

    @Test
    fun primaryNeverFewerSetsThanSecondary() {
        TrainingAge.entries.forEach { age ->
            val primary = sessionSets(SlotRole.PRIMARY, age)
            val secondary = sessionSets(SlotRole.SECONDARY, age)
            assertTrue(primary >= secondary, "$age primary $primary secondary $secondary")
        }
    }

    @Test
    fun collapsedGoalCatalogIntersectionUsesCatalogWindow() {
        val overhead = catalog.bySlug("overhead-triceps-extension")!!
        val ctx = PickContext(
            user = UserContext(Goal.STRENGTH, TrainingAge.INTERMEDIATE),
            equipment = catalog.equipment.keys,
        )
        val d = dose(overhead, Slot("triceps", SlotRole.ISOLATION), ctx)
        assertTrue(d.repRange.min < d.repRange.max, d.repRange.toString())
        assertEquals(overhead.repWindow.min, d.repRange.min)
        assertEquals(overhead.repWindow.max, d.repRange.max)
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
