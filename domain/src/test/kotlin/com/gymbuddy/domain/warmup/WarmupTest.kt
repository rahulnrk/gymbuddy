package com.gymbuddy.domain.warmup

import com.gymbuddy.domain.catalog.CatalogIds
import com.gymbuddy.domain.catalog.CatalogTest
import com.gymbuddy.domain.picker.PickContext
import com.gymbuddy.domain.picker.SlotRole
import com.gymbuddy.domain.plan.PlanResult
import com.gymbuddy.domain.plan.Template
import com.gymbuddy.domain.plan.Templates
import com.gymbuddy.domain.plan.buildWeek
import com.gymbuddy.domain.plan.estimateMinutes
import com.gymbuddy.domain.programming.Goal
import com.gymbuddy.domain.programming.TrainingAge
import com.gymbuddy.domain.programming.UserContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WarmupTest {
    private val catalog = CatalogTest.loadCatalog()
    private val warmups = MobilityJson.loadWarmups(CatalogTest.knowledge("catalog/warmups.json"))
    private val stretches = MobilityJson.loadStretches(CatalogTest.knowledge("catalog/stretches.json"))
    private val templates = loadTemplates()

    @Test
    fun schemasDecodeAndIdsMatchSlugs() {
        assertEquals(30, warmups.drills.size)
        assertEquals(30, stretches.stretches.size)
        warmups.drills.forEach { d ->
            assertEquals(CatalogIds.fromSlug(d.slug).toString(), d.id, d.slug)
            assertTrue(d.howTo.length >= 40, d.slug)
            assertNotNull(d.provenanceDefault.source)
        }
        stretches.stretches.forEach { s ->
            assertEquals(CatalogIds.fromSlug(s.slug).toString(), s.id, s.slug)
            assertTrue(s.howTo.length >= 40, s.slug)
            assertNotNull(s.provenanceDefault.source)
        }
        val polluted = CatalogTest.knowledge("catalog/warmups.json")
            .replaceFirst("\"slug\"", "\"load\":\"barbell\",\"slug\"")
        assertTrue(
            runCatching { MobilityJson.loadWarmups(polluted) }.isFailure,
        )
    }

    @Test
    fun everyTokenResolves() {
        warmups.drills.forEach { d ->
            d.targetPatterns.forEach { p ->
                assertNotNull(catalog.patterns[p], "${d.slug} unknown pattern $p")
            }
            d.targetMuscles.forEach { m ->
                assertNotNull(catalog.muscles[m], "${d.slug} unknown muscle $m")
            }
            d.requiredEquipment.flatten().forEach { e ->
                assertNotNull(catalog.equipment[e], "${d.slug} unknown gear $e")
            }
            d.contraindications.forEach { c ->
                assertNotNull(catalog.conditions[c], "${d.slug} unknown condition $c")
            }
        }
        stretches.stretches.forEach { s ->
            s.targetMuscles.forEach { m ->
                assertNotNull(catalog.muscles[m], "${s.slug} unknown muscle $m")
            }
            s.requiredEquipment.flatten().forEach { e ->
                assertNotNull(catalog.equipment[e], "${s.slug} unknown gear $e")
            }
            s.contraindications.forEach { c ->
                assertNotNull(catalog.conditions[c], "${s.slug} unknown condition $c")
            }
        }
    }

    @Test
    fun squatDayRehearsesSquatNotPull() {
        val built = week(fullGymBeginner())
        val squatOnly = built.week.first { it.day == "Day A" }.let { day ->
            day.copy(entries = day.entries.filter { it.slot.pattern == "squat" })
        }
        assertTrue(squatOnly.entries.isNotEmpty())
        val wu = planWarmup(squatOnly, fullGymBeginner(), catalog, warmups)
        val names = wu.drills.map { it.drill.slug }
        assertTrue("bodyweight-squat-rehearsal" in names, names.toString())
        assertTrue(names.none { it in setOf("band-pull-apart", "doorway-lat-reach") }, names.toString())
        assertTrue(
            wu.drills.none { it.drill.method == "rehearsal" && it.drill.targetPatterns.all { p -> p in pullPatterns } },
            names.toString(),
        )
    }

    @Test
    fun bodyweightPulseIsNotABike() {
        val built = week(bodyweightBeginner())
        built.week.forEach { session ->
            val pulse = session.warmup?.pulse?.drill
            assertNotNull(pulse, session.day)
            assertTrue("bike" !in pulse.slug, pulse.slug)
            assertTrue("cycle" !in pulse.slug, pulse.slug)
            assertEquals("brisk-walk", pulse.slug)
        }
    }

    @Test
    fun contraindicatedDrillNeverAppears() {
        val ctx = fullGymBeginner().copy(
            user = fullGymBeginner().user.copy(conditions = listOf("flexion-intolerant-back")),
        )
        val built = week(ctx)
        built.week.forEach { session ->
            val slugs = session.warmup?.drills.orEmpty().map { it.drill.slug }
            assertTrue("cat-camel" !in slugs, slugs.toString())
            assertTrue("hip-hinge-pattern" !in slugs, slugs.toString())
            session.warmup?.drills.orEmpty().forEach { entry ->
                assertTrue("flexion-intolerant-back" !in entry.drill.contraindications, entry.drill.slug)
            }
        }
    }

    @Test
    fun rampSetsAreRelativeAndOnTheFirstPrimary() {
        val built = week(fullGymBeginner())
        val dayA = built.week.first { it.day == "Day A" }
        val firstPrimary = dayA.entries.first { it.slot.role == SlotRole.PRIMARY }
        val ramps = assertNotNull(dayA.warmup).rampSets
        assertTrue(ramps.isNotEmpty())
        assertTrue(ramps.size in 1..3)
        ramps.forEach { ramp ->
            assertEquals(firstPrimary.exercise.name, ramp.liftName)
            assertFalse(Regex("""\d+\s*kg""").containsMatchIn(ramp.effortDescription), ramp.effortDescription)
            assertTrue("working weight" in ramp.effortDescription)
        }
        val spoken = speakSession(dayA)
        assertFalse(Regex("""\d+\s*kg""").containsMatchIn(spoken), spoken)
    }

    @Test
    fun warmupStaysAtOrUnderTheCapAndCutsDrillsNotPulse() {
        val built = week(fullGymBeginner())
        built.week.forEach { session ->
            val wu = assertNotNull(session.warmup, session.day)
            assertTrue(wu.estimatedMinutes <= WarmupConstants.WARMUP_CAP_MIN, "${session.day} ${wu.estimatedMinutes}")
            assertNotNull(wu.pulse, session.day)
        }
    }

    @Test
    fun staticNeverPreSessionDynamicNeverPost() {
        val built = week(fullGymBeginner())
        built.week.forEach { session ->
            session.warmup?.drills.orEmpty().forEach { entry ->
                assertTrue(entry.drill.method in setOf("mobility", "activation", "rehearsal", "pulse"))
            }
            session.cooldown.forEach { pick ->
                assertEquals("static", pick.stretch.type, pick.stretch.slug)
                assertEquals("post-session", pick.stretch.timing, pick.stretch.slug)
            }
        }
        assertTrue(stretches.stretches.any { it.type == "dynamic" && it.timing == "warmup" })
        assertTrue(stretches.stretches.none { it.type == "dynamic" && it.timing == "post-session" })
        assertTrue(stretches.stretches.none { it.type == "static" && it.timing == "warmup" })
    }

    @Test
    fun suggestedStretchesTargetMusclesThatWorked() {
        val built = week(fullGymBeginner())
        val dayA = built.week.first { it.day == "Day A" }
        assertTrue(dayA.cooldown.isNotEmpty())
        assertTrue(dayA.cooldown.size <= 3)
        val worked = dayA.entries.flatMap { entry ->
            catalog.musclesFor(entry.exercise).filter { it.value >= 0.3 }.keys
        }.toSet()
        dayA.cooldown.forEach { pick ->
            assertTrue(
                pick.stretch.targetMuscles.any { it in worked },
                "${pick.stretch.slug} ${pick.stretch.targetMuscles} vs $worked",
            )
            assertFalse("not a fix" in pick.why, pick.why)
            assertFalse("weak" in pick.why, pick.why)
        }
        assertNotNull(dayA.cooldownDisclaimer)
        assertTrue("not a fix" in dayA.cooldownDisclaimer!!, dayA.cooldownDisclaimer)
        val again = week(fullGymBeginner(), history = MobilityHistory(heardStretchDisclaimer = true))
        assertTrue(again.week.first { it.day == "Day A" }.cooldownDisclaimer == null)
    }

    @Test
    fun firstTimeNarratesHowToLaterTimesAreNameAndReason() {
        val built = week(fullGymBeginner())
        val dayA = built.week.first { it.day == "Day A" }
        val first = speakSession(dayA)
        dayA.warmup!!.drills.forEach { entry ->
            assertEquals(0, entry.timesSeen, entry.drill.slug)
            assertTrue(entry.drill.howTo in first, entry.drill.slug)
        }
        val slugs = listOfNotNull(dayA.warmup?.pulse?.drill?.slug) + dayA.warmup!!.drills.map { it.drill.slug }
        val thirdHistory = MobilityHistory().withTimes(slugs, times = 2, heardDisclaimer = true)
        val thirdWu = planWarmup(dayA, fullGymBeginner(), catalog, warmups, thirdHistory)
        val (thirdCd, thirdDisc) = suggestStretches(dayA, fullGymBeginner(), catalog, stretches, thirdHistory)
        val third = speakSession(dayA.copy(warmup = thirdWu, cooldown = thirdCd, cooldownDisclaimer = thirdDisc))
        thirdWu.drills.forEach { entry ->
            assertEquals(2, entry.timesSeen, entry.drill.slug)
            assertFalse(entry.drill.howTo in third, entry.drill.slug)
        }
        thirdWu.pulse?.let { assertFalse(it.drill.howTo in third) }
        assertTrue("—" in third)
        assertTrue(thirdDisc == null)
    }

    @Test
    fun squatAndPressDayDoesNotStackUpperBackActivation() {
        val built = week(fullGymBeginner())
        val dayA = built.week.first { it.day == "Day A" }
        val upperBack = setOf("prone-y-raise", "floor-w-raise", "band-external-rotation")
        val slugs = dayA.warmup!!.drills.map { it.drill.slug }
        val hits = slugs.count { it in upperBack }
        assertTrue(hits <= 1, slugs.toString())
        val activationRegions = dayA.warmup!!.drills
            .filter { it.drill.method == "activation" }
            .map { drillRegion(it.drill) }
        assertEquals(activationRegions.size, activationRegions.toSet().size, slugs.toString())
    }

    @Test
    fun printedWeeksWithRealWarmup() {
        val profiles = listOf(fullGymBeginner(), homeDumbbellsIntermediate(), bodyweightBeginner())
        profiles.forEach { ctx ->
            val built = week(ctx)
            built.week.forEach { session ->
                val minutes = estimateMinutes(session)
                val hi = 60 + 15
                println("${ctx.user.goal} ${ctx.user.trainingAge} ${session.day}: $minutes min (warmup ${session.warmup?.estimatedMinutes})")
                // Overshoot is reported, not hidden by cutting the week further.
                if (minutes > hi) {
                    println("  OVERSHOOT ${session.day} $minutes > $hi")
                }
            }
        }
    }

    @Test
    fun everyRowHasProvenance() {
        warmups.drills.forEach { d ->
            assertTrue(d.provenanceDefault.source.isNotBlank(), d.slug)
            assertTrue(d.provenanceDefault.basis.isNotBlank(), d.slug)
            assertEquals(WarmupConstants.REVIEWED, d.provenanceDefault.reviewed, d.slug)
        }
        stretches.stretches.forEach { s ->
            assertTrue(s.provenanceDefault.source.isNotBlank(), s.slug)
            assertEquals(WarmupConstants.REVIEWED, s.provenanceDefault.reviewed, s.slug)
        }
    }

    @Test
    fun noTwoWarmupsShareAHowTo() {
        val how = warmups.drills.map { it.howTo }
        assertEquals(how.size, how.toSet().size, how.groupingBy { it }.eachCount().filter { it.value > 1 }.toString())
        val stretchHow = stretches.stretches.map { it.howTo }
        assertEquals(stretchHow.size, stretchHow.toSet().size)
    }

    @Test
    fun warmupConstantsHaveDecisionRows() {
        val src = warmupMainDir().listFiles { f -> f.extension == "kt" }!!.joinToString("\n") { it.readText() }
        val names = Regex("""const val ([A-Z][A-Z0-9_]*)\s*=""").findAll(src).map { it.groupValues[1] }.toList()
        val decisions = decisionsFile().readText()
        names.forEach { assertTrue(it in decisions, it) }
    }

    private fun week(ctx: PickContext, history: MobilityHistory = MobilityHistory()): PlanResult.Built =
        assertIs(buildWeek(templates, ctx, catalog, warmups = warmups, stretches = stretches, history = history))

    private fun fullGymBeginner() = PickContext(
        user = UserContext(Goal.HYPERTROPHY, TrainingAge.BEGINNER, daysPerWeek = 3, typicalDurationMin = 60),
        equipment = catalog.equipment.keys,
    )

    private fun homeDumbbellsIntermediate() = PickContext(
        user = UserContext(Goal.STRENGTH, TrainingAge.INTERMEDIATE, daysPerWeek = 4, typicalDurationMin = 60),
        equipment = setOf("dumbbell", "bench-flat", "none", "mat"),
    )

    private fun bodyweightBeginner() = PickContext(
        user = UserContext(Goal.GENERAL_FITNESS, TrainingAge.BEGINNER, daysPerWeek = 3, typicalDurationMin = 60),
        equipment = setOf("none", "sturdy-table", "door-frame"),
    )

    private val pullPatterns = setOf("horizontal pull", "vertical pull", "lats", "rear delt", "traps")

    private fun loadTemplates(): List<Template> {
        val names = listOf(
            "full-body-3d-beginner.json",
            "full-body-3d.json",
            "upper-lower-4d.json",
            "ppl-6d.json",
        )
        return Templates.load(names.map { CatalogTest.knowledge("templates/$it") })
    }

    private fun warmupMainDir(): java.io.File {
        val cwd = java.io.File("").absoluteFile
        return listOf(
            java.io.File(cwd, "src/main/kotlin/com/gymbuddy/domain/warmup"),
            java.io.File(cwd, "domain/src/main/kotlin/com/gymbuddy/domain/warmup"),
        ).first { it.isDirectory }
    }

    private fun decisionsFile(): java.io.File {
        val cwd = java.io.File("").absoluteFile
        return listOf(
            java.io.File(cwd, "docs/living-spec/DECISIONS_AND_THRESHOLDS.md"),
            java.io.File(cwd, "../docs/living-spec/DECISIONS_AND_THRESHOLDS.md"),
        ).first { it.isFile }
    }
}
