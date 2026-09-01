package com.gymbuddy.domain.plan

import com.gymbuddy.domain.catalog.Catalog
import com.gymbuddy.domain.catalog.CatalogTest
import com.gymbuddy.domain.picker.PickContext
import com.gymbuddy.domain.picker.SlotRole
import com.gymbuddy.domain.programming.Goal
import com.gymbuddy.domain.programming.TrainingAge
import com.gymbuddy.domain.programming.UserContext
import com.gymbuddy.domain.warmup.MobilityHistory
import com.gymbuddy.domain.warmup.MobilityJson
import com.gymbuddy.domain.warmup.planWarmup
import com.gymbuddy.domain.warmup.speakSession
import com.gymbuddy.domain.warmup.suggestStretches
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanTest {
    private val catalog = CatalogTest.loadCatalog()
    private val templates = loadTemplates()
    private val warmups = MobilityJson.loadWarmups(CatalogTest.knowledge("catalog/warmups.json"))
    private val stretches = MobilityJson.loadStretches(CatalogTest.knowledge("catalog/stretches.json"))

    private fun buildWeek(templates: List<Template>, ctx: PickContext, catalog: Catalog) =
        com.gymbuddy.domain.plan.buildWeek(templates, ctx, catalog, warmups = warmups, stretches = stretches)

    private fun buildWeek(template: Template, ctx: PickContext, catalog: Catalog) =
        com.gymbuddy.domain.plan.buildWeek(template, ctx, catalog, warmups = warmups, stretches = stretches)

    @Test
    fun everyTemplateValidatesAgainstVocab() {
        assertEquals(4, templates.size)
        val roles = SlotRole.entries.map { it.name }.toSet()
        val patterns = catalog.patterns.keys
        templates.forEach { t ->
            assertEquals(t.daysPerWeek, t.days.size, t.id)
            assertTrue(t.goalEnums().isNotEmpty(), t.id)
            assertTrue(t.ageEnums().isNotEmpty(), t.id)
            assertTrue(t.provenanceDefault.source.isNotBlank(), t.id)
            t.days.forEach { day ->
                day.slots.forEach { slot ->
                    assertTrue(slot.pattern in patterns, "${t.id} unknown pattern ${slot.pattern}")
                    assertTrue(slot.role in roles, "${t.id} unknown role ${slot.role}")
                    assertTrue(slot.priority >= 1, t.id)
                }
            }
        }
        assertEquals(77, catalog.exercises.size)
        val decisions = decisionsFile().readText()
        listOf(
            "SECONDS_PER_WORKING_SET",
            "WARMUP_CAP_MIN",
            "RAMP_SET_SEC",
            "DURATION_TOLERANCE_MIN",
            "DEFAULT_DURATION_MIN",
            "MIN_EXERCISES_PER_DAY",
            "SESSION_SETS_SECONDARY_BEGINNER",
        ).forEach {
            assertTrue(it in decisions, it)
        }
    }

    private fun decisionsFile(): File {
        val cwd = File("").absoluteFile
        return listOf(
            File(cwd, "docs/living-spec/DECISIONS_AND_THRESHOLDS.md"),
            File(cwd, "../docs/living-spec/DECISIONS_AND_THRESHOLDS.md"),
        ).first { it.isFile }
    }

    @Test
    fun sameProfileSameWeekOverOneHundredRuns() {
        val ctx = fullGymBeginner()
        val first = assertIs<PlanResult.Built>(buildWeek(templates, ctx, catalog))
        val signature = weekSignature(first)
        repeat(100) {
            val next = assertIs<PlanResult.Built>(buildWeek(templates, ctx, catalog))
            assertEquals(signature, weekSignature(next))
        }
    }

    @Test
    fun missingSlotIsDroppedAndNamed() {
        val ctx = PickContext(
            user = UserContext(Goal.HYPERTROPHY, TrainingAge.BEGINNER, typicalDurationMin = 60, daysPerWeek = 3),
            equipment = setOf("none"),
        )
        val template = templates.single { it.id == "full-body-3d-beginner" }
        val result = buildWeek(template, ctx, catalog)
        when (result) {
            is PlanResult.CannotBuild -> {
                assertTrue(result.reasons.any { "squat" in it.lowercase() || "couldn't find" in it.lowercase() }, result.reasons.toString())
                result.reasons.forEach { assertSpeakableNote(it) }
            }
            is PlanResult.Built -> {
                val notes = result.week.flatMap { it.notes }
                assertTrue(notes.any { "couldn't find" in it.lowercase() }, notes.toString())
                notes.forEach { assertSpeakableNote(it) }
            }
        }
    }

    @Test
    fun prescribedWeightIsAlwaysNull() {
        val profiles = listOf(fullGymBeginner(), homeDumbbellsIntermediate(), bodyweightBeginner())
        profiles.forEach { ctx ->
            when (val result = buildWeek(templates, ctx, catalog)) {
                is PlanResult.Built -> result.week.forEach { session ->
                    session.entries.forEach { entry ->
                        assertNull(entry.prescribedWeightKg, entry.exercise.slug)
                    }
                }
                is PlanResult.CannotBuild -> { }
            }
        }
        templates.forEach { t ->
            val ctx = fullGymBeginner().let {
                it.copy(user = it.user.copy(daysPerWeek = t.daysPerWeek, trainingAge = t.ageEnums().first(), goal = t.goalEnums().first()))
            }
            val result = buildWeek(t, ctx, catalog)
            if (result is PlanResult.Built) {
                result.week.flatMap { it.entries }.forEach { assertNull(it.prescribedWeightKg) }
            }
        }
    }

    @Test
    fun timeClampNeverCutsAPrimary() {
        val ctx = fullGymBeginner().copy(user = fullGymBeginner().user.copy(typicalDurationMin = 30))
        val template = templates.single { it.id == "full-body-3d-beginner" }
        val built = assertIs<PlanResult.Built>(buildWeek(template, ctx, catalog))
        for ((day, session) in template.days.zip(built.week)) {
            val keptPrimary = session.entries.filter { it.slot.role == SlotRole.PRIMARY }.map { it.slot.pattern }.toSet()
            val droppedNotes = session.notes.filter { "couldn't find" in it.lowercase() }
            for (pattern in day.slots.filter { it.role == "PRIMARY" }.map { it.pattern }) {
                if (keptPrimary.contains(pattern)) continue
                val asked = PlanCopy.jobForPattern(pattern)
                val siblingNote = session.notes.any { "instead of $asked" in it }
                if (siblingNote) continue
                assertTrue(
                    droppedNotes.isNotEmpty(),
                    "primary $pattern missing without a drop or sibling note on ${session.day}: ${session.notes}",
                )
            }
            for (entry in session.entries.filter { it.slot.role == SlotRole.PRIMARY }) {
                assertFalse(session.notes.any { it.startsWith("I took out ${entry.exercise.name} so") })
            }
        }
    }

    @Test
    fun thirtyAndNinetyDifferOnlyByCutAccessories() {
        val base = fullGymBeginner()
        val short = assertIs<PlanResult.Built>(
            buildWeek(templates, base.copy(user = base.user.copy(typicalDurationMin = 30)), catalog),
        )
        val long = assertIs<PlanResult.Built>(
            buildWeek(templates, base.copy(user = base.user.copy(typicalDurationMin = 90)), catalog),
        )
        val firstShort = short.week.first().entries.filter { it.slot.role == SlotRole.PRIMARY }.map { it.exercise.slug }
        val firstLong = long.week.first().entries.filter { it.slot.role == SlotRole.PRIMARY }.map { it.exercise.slug }
        assertEquals(firstLong, firstShort, "Day A mains changed")
        short.week.zip(long.week).forEach { (s, l) ->
            val shortMains = s.entries.filter { it.slot.role == SlotRole.PRIMARY }.map { it.slot.pattern }
            val longMains = l.entries.filter { it.slot.role == SlotRole.PRIMARY }.map { it.slot.pattern }
            assertEquals(longMains, shortMains, "${s.day} main patterns changed")
        }
    }

    @Test
    fun beginnerVsIntermediateDifferInDoseNeverLoad() {
        val beginner = assertIs<PlanResult.Built>(buildWeek(templates, fullGymBeginner(), catalog))
        val intermediate = assertIs<PlanResult.Built>(
            buildWeek(
                templates,
                fullGymBeginner().let {
                    it.copy(user = it.user.copy(trainingAge = TrainingAge.INTERMEDIATE))
                },
                catalog,
            ),
        )
        beginner.week.flatMap { it.entries }.forEach { assertNull(it.prescribedWeightKg) }
        intermediate.week.flatMap { it.entries }.forEach { assertNull(it.prescribedWeightKg) }
        val bSets = beginner.week.flatMap { it.entries }.map { it.sets }
        val iSets = intermediate.week.flatMap { it.entries }.map { it.sets }
        assertTrue(bSets != iSets || beginner.week.flatMap { it.entries }.map { it.repRange } != intermediate.week.flatMap { it.entries }.map { it.repRange })
    }

    @Test
    fun contraindicationNeverAppears() {
        val ctx = fullGymBeginner().copy(
            user = fullGymBeginner().user.copy(conditions = listOf("shoulder-impingement")),
        )
        val built = assertIs<PlanResult.Built>(buildWeek(templates, ctx, catalog))
        val slugs = built.week.flatMap { it.entries }.map { it.exercise.slug }
        assertFalse("overhead-press" in slugs, slugs.toString())
        assertFalse("arnold-press" in slugs, slugs.toString())
    }

    @Test
    fun bodyweightOnlyBuildsAFullBodyWeek() {
        val result = buildWeek(templates, bodyweightBeginner(), catalog)
        val built = assertIs<PlanResult.Built>(result, result.toString())
        assertEquals(3, built.week.size)
        built.week.forEach { session ->
            assertTrue(session.entries.size >= 2, session.day)
        }
    }

    @Test
    fun buildWeekEmitsNoEventsAndMutatesNothing() {
        val ctx = fullGymBeginner()
        val beforeUser = ctx.user
        val beforeEquip = ctx.equipment.toSet()
        buildWeek(templates, ctx, catalog)
        assertEquals(beforeUser, ctx.user)
        assertEquals(beforeEquip, ctx.equipment)
        val src = planMainDir().listFiles { f -> f.extension == "kt" }!!.joinToString("\n") { it.readText() }
        listOf("EventLog", "WorkoutEvent", "CommittedEventType", "WorkoutEngine").forEach { bad ->
            assertFalse(bad in src, bad)
        }
        assertFalse("kotlin.random" in src.lowercase())
        assertFalse("System.currentTimeMillis" in src)
    }

    @Test
    fun validatorWarningsAreReturnedNotFixed() {
        val heavy = Template(
            id = "volume-bomb",
            name = "Volume bomb",
            goals = listOf("HYPERTROPHY"),
            trainingAges = listOf("BEGINNER"),
            daysPerWeek = 3,
            days = (1..3).map { n ->
                TemplateDay(
                    "Day $n",
                    (1..6).map { TemplateSlot("squat", "PRIMARY", 1) },
                )
            },
            provenanceDefault = templates.first().provenanceDefault,
        )
        val built = assertIs<PlanResult.Built>(buildWeek(heavy, fullGymBeginner(), catalog))
        assertTrue(
            built.warnings.any { it.rule.contains("volume") || "more than" in it.message.lowercase() },
            built.warnings.joinToString { it.message },
        )
        val squatCount = built.week.sumOf { session -> session.entries.count { it.slot.pattern == "squat" } }
        assertTrue(squatCount >= 6, "builder silently thinned the week: $squatCount")
    }

    @Test
    fun everyNoteIsPlainLanguage() {
        val results = listOf(
            buildWeek(templates, fullGymBeginner(), catalog),
            buildWeek(templates, homeDumbbellsIntermediate(), catalog),
            buildWeek(templates, bodyweightBeginner(), catalog),
            buildWeek(templates, fullGymBeginner().copy(user = fullGymBeginner().user.copy(typicalDurationMin = 30)), catalog),
        )
        results.forEach { result ->
            val notes = when (result) {
                is PlanResult.Built -> result.week.flatMap { it.notes }
                is PlanResult.CannotBuild -> result.reasons
            }
            notes.forEach { assertSpeakableNote(it) }
        }
    }

    @Test
    fun threeProfilesAndBodyweightAllBuild() {
        assertIs<PlanResult.Built>(buildWeek(templates, fullGymBeginner(), catalog))
        assertIs<PlanResult.Built>(buildWeek(templates, homeDumbbellsIntermediate(), catalog))
        assertIs<PlanResult.Built>(buildWeek(templates, bodyweightBeginner(), catalog))
    }

    @Test
    fun inRepoTemplatesHaveNoVolumeWarnings() {
        templates.forEach { template ->
            template.goalEnums().forEach { goal ->
                template.ageEnums().forEach { age ->
                    val ctx = PickContext(
                        user = UserContext(
                            goal,
                            age,
                            daysPerWeek = template.daysPerWeek,
                            typicalDurationMin = 60,
                        ),
                        equipment = catalog.equipment.keys,
                    )
                    val built = assertIs<PlanResult.Built>(buildWeek(template, ctx, catalog), template.id)
                    val volumeWarns = built.warnings.filter {
                        it.severity.name == "WARN" && it.rule.startsWith("volume")
                    }
                    assertTrue(
                        volumeWarns.isEmpty(),
                        "${template.id} $goal $age: ${volumeWarns.joinToString { it.message }}",
                    )
                }
            }
        }
    }

    @Test
    fun beginnerHingeAtMostTwoDaysAndNeverAfterSquat() {
        templates.filter { "BEGINNER" in it.trainingAges }.forEach { template ->
            val ctx = PickContext(
                user = UserContext(
                    template.goalEnums().first(),
                    TrainingAge.BEGINNER,
                    daysPerWeek = template.daysPerWeek,
                    typicalDurationMin = 60,
                ),
                equipment = catalog.equipment.keys,
            )
            val built = assertIs<PlanResult.Built>(buildWeek(template, ctx, catalog))
            val hingeDays = built.week.count { session -> session.entries.any { it.slot.pattern == "hinge" } }
            assertTrue(hingeDays <= 2, "${template.id} hinge days=$hingeDays")
            built.week.forEach { session ->
                val slugs = session.entries.map { it.exercise }
                val squatIdx = slugs.indexOfFirst { it.pattern == "squat" }
                val hingeIdx = slugs.indexOfFirst { it.pattern == "hinge" }
                if (squatIdx >= 0 && hingeIdx >= 0) {
                    val hinge = slugs[hingeIdx]
                    val heavy = hinge.skillDemand == "high" || hinge.fatigueCost == "high"
                    assertFalse(
                        heavy && hingeIdx > squatIdx,
                        "${template.id} ${session.day}: heavy ${hinge.name} after squat",
                    )
                }
            }
        }
    }

    @Test
    fun primaryNeverFewerSetsThanSecondaryInASession() {
        val built = assertIs<PlanResult.Built>(buildWeek(templates, fullGymBeginner(), catalog))
        built.week.forEach { session ->
            val primary = session.entries.filter { it.slot.role == SlotRole.PRIMARY }.minOfOrNull { it.sets }
            val secondary = session.entries.filter { it.slot.role == SlotRole.SECONDARY }.maxOfOrNull { it.sets }
            if (primary != null && secondary != null) {
                assertTrue(primary >= secondary, "${session.day}: primary $primary secondary $secondary")
            }
        }
    }

    @Test
    fun noCollapsedRepRangeUnlessCatalogIsAPoint() {
        val built = assertIs<PlanResult.Built>(buildWeek(templates, homeDumbbellsIntermediate(), catalog))
        built.week.flatMap { it.entries }.forEach { entry ->
            if (entry.repRange.min == entry.repRange.max) {
                assertEquals(
                    entry.exercise.repWindow.min,
                    entry.exercise.repWindow.max,
                    "${entry.exercise.name} collapsed to ${entry.repRange.min}",
                )
            }
        }
    }

    @Test
    fun estimatesDoNotOvershootTheHourWindow() {
        templates.forEach { template ->
            val age = template.ageEnums().first()
            val ctx = PickContext(
                user = UserContext(
                    template.goalEnums().first(),
                    age,
                    daysPerWeek = template.daysPerWeek,
                    typicalDurationMin = 60,
                ),
                equipment = catalog.equipment.keys,
            )
            val built = assertIs<PlanResult.Built>(buildWeek(template, ctx, catalog))
            val hi = 60 + PlanConstants.DURATION_TOLERANCE_MIN
            built.week.forEach { session ->
                val minutes = estimateMinutes(session)
                assertTrue(
                    minutes <= hi,
                    "${template.id} ${session.day}: $minutes min over $hi",
                )
            }
        }
    }

    @Test
    fun siblingMapIsExplicitAndNeverGuessesHingeForSquat() {
        assertEquals(listOf("horizontal pull"), PatternSiblings.of("vertical pull"))
        assertEquals(listOf("vertical pull"), PatternSiblings.of("horizontal pull"))
        assertEquals(listOf("incline push", "horizontal push"), PatternSiblings.of("vertical push"))
        assertEquals(listOf("lunge"), PatternSiblings.of("squat"))
        assertEquals(listOf("squat"), PatternSiblings.of("lunge"))
        assertTrue(PatternSiblings.of("hinge").isEmpty())
        assertTrue(PatternSiblings.MAP.values.none { "hinge" in it })
    }

    @Test
    fun droppedVerticalPullFallsBackToARowAtHome() {
        val built = assertIs<PlanResult.Built>(buildWeek(templates, homeDumbbellsIntermediate(), catalog))
        val upperA = built.week.first { it.day == "Upper A" }
        assertTrue(
            upperA.entries.any { it.exercise.pattern == "horizontal pull" },
            upperA.entries.map { it.exercise.slug }.toString(),
        )
        assertTrue(
            upperA.notes.any { "row" in it.lowercase() && "instead of" in it.lowercase() },
            upperA.notes.toString(),
        )
        assertTrue(upperA.entries.none { it.exercise.pattern == "vertical pull" })
        upperA.entries.filter { it.slot.role == SlotRole.PRIMARY }.forEach {
            assertEquals(SlotRole.PRIMARY, it.slot.role)
        }
    }

    @Test
    fun beginnerWeekHasOneLowerPatternPerDay() {
        val built = assertIs<PlanResult.Built>(buildWeek(templates, fullGymBeginner(), catalog))
        val lowers = built.week.map { session ->
            session.entries.map { it.slot.pattern }.filter { it in setOf("squat", "hinge", "lunge") }
        }
        lowers.forEach { day ->
            assertEquals(1, day.size, day.toString())
        }
        assertEquals(setOf("squat", "hinge", "lunge"), lowers.flatten().toSet())
    }

    @Test
    fun bodyweightDaysAreEvenlyFilled() {
        val built = assertIs<PlanResult.Built>(buildWeek(templates, bodyweightBeginner(), catalog))
        val sizes = built.week.map { it.day to it.entries.size }
        val counts = sizes.map { it.second }
        assertTrue(counts.min() >= 4, sizes.toString())
        assertTrue(counts.max() - counts.min() <= 1, sizes.toString())
    }

    @Test
    fun coreIsLastOnEveryTemplateDayThatHasIt() {
        templates.forEach { template ->
            template.days.forEach { day ->
                val coreIdx = day.slots.indexOfLast { it.pattern == "core" }
                if (coreIdx >= 0) {
                    assertEquals(day.slots.lastIndex, coreIdx, "${template.id} ${day.name}")
                }
            }
        }
    }

    @Test
    fun inRepoTemplatesHaveNoOrderClassWarnings() {
        templates.forEach { template ->
            template.goalEnums().forEach { goal ->
                template.ageEnums().forEach { age ->
                    val ctx = PickContext(
                        user = UserContext(
                            goal,
                            age,
                            daysPerWeek = template.daysPerWeek,
                            typicalDurationMin = 60,
                        ),
                        equipment = catalog.equipment.keys,
                    )
                    val built = assertIs<PlanResult.Built>(buildWeek(template, ctx, catalog), template.id)
                    val orderWarns = built.warnings.filter { it.rule == "order.freshness" }
                    assertTrue(
                        orderWarns.isEmpty(),
                        "${template.id} $goal $age: ${orderWarns.joinToString { it.message }}",
                    )
                }
            }
        }
    }

    @Test
    fun laterDaysSeeEarlierPicksAsRecentWhenAlternativesExist() {
        val home = assertIs<PlanResult.Built>(buildWeek(templates, homeDumbbellsIntermediate(), catalog))
        val upperA = home.week.first { it.day == "Upper A" }
        val upperB = home.week.first { it.day == "Upper B" }
        val aPush = upperA.entries.first { it.slot.pattern == "horizontal push" }.exercise.slug
        val bPush = upperB.entries.first { it.slot.pattern == "horizontal push" }.exercise.slug
        assertTrue(aPush != bPush, "Upper A $aPush Upper B $bPush")

        val gym = assertIs<PlanResult.Built>(buildWeek(templates, fullGymBeginner(), catalog))
        val dayA = gym.week.first { it.day == "Day A" }
        val dayC = gym.week.first { it.day == "Day C" }
        val aPress = dayA.entries.first { it.slot.pattern == "horizontal push" }.exercise.slug
        val cPress = dayC.entries.first { it.slot.pattern == "horizontal push" }.exercise.slug
        assertEquals("dumbbell-bench-press", aPress)
        assertTrue(aPress != cPress, "Day A $aPress Day C $cPress")
    }

    @Test
    fun printThreeProfilesForReport() {
        val a = buildWeek(templates, fullGymBeginner(), catalog)
        val b = buildWeek(templates, homeDumbbellsIntermediate(), catalog)
        val c = buildWeek(templates, bodyweightBeginner(), catalog)
        println("===== A beginner hypertrophy 3d full gym =====")
        println(formatWeek(a))
        println("===== B intermediate strength 4d home DB+bench =====")
        println(formatWeek(b))
        println("===== C beginner general 3d bodyweight =====")
        println(formatWeek(c))
        assertIs<PlanResult.Built>(a)
        assertIs<PlanResult.Built>(b)
        assertIs<PlanResult.Built>(c)
        println("===== one full session as a user would hear it =====")
        val first = a.week.first()
        println(speakSession(first))
        val slugs = listOfNotNull(first.warmup?.pulse?.drill?.slug) +
            first.warmup!!.drills.map { it.drill.slug }
        val thirdHistory = MobilityHistory().withTimes(slugs, times = 2, heardDisclaimer = true)
        val thirdWu = planWarmup(first, fullGymBeginner(), catalog, warmups, thirdHistory)
        val (thirdCd, thirdDisc) = suggestStretches(first, fullGymBeginner(), catalog, stretches, thirdHistory)
        println("===== same session, third time through =====")
        println(speakSession(first.copy(warmup = thirdWu, cooldown = thirdCd, cooldownDisclaimer = thirdDisc)))
    }

    private fun fullGymBeginner() = PickContext(
        user = UserContext(
            Goal.HYPERTROPHY,
            TrainingAge.BEGINNER,
            daysPerWeek = 3,
            typicalDurationMin = 60,
        ),
        equipment = catalog.equipment.keys,
    )

    private fun homeDumbbellsIntermediate() = PickContext(
        user = UserContext(
            Goal.STRENGTH,
            TrainingAge.INTERMEDIATE,
            daysPerWeek = 4,
            typicalDurationMin = 60,
        ),
        equipment = setOf("dumbbell", "bench-flat", "none", "mat"),
    )

    private fun bodyweightBeginner() = PickContext(
        user = UserContext(
            Goal.GENERAL_FITNESS,
            TrainingAge.BEGINNER,
            daysPerWeek = 3,
            typicalDurationMin = 60,
        ),
        equipment = setOf("none", "sturdy-table", "door-frame"),
    )

    private fun weekSignature(built: PlanResult.Built): List<List<String>> =
        built.week.map { session -> session.entries.map { "${it.exercise.slug}:${it.sets}" } }

    private fun loadTemplates(): List<Template> {
        val names = listOf(
            "full-body-3d-beginner.json",
            "full-body-3d.json",
            "upper-lower-4d.json",
            "ppl-6d.json",
        )
        return Templates.load(names.map { CatalogTest.knowledge("templates/$it") })
    }
}

internal fun assertSpeakableNote(text: String) {
    val banned = listOf(
        "daysPerWeek", "typicalDurationMin", "prescribedWeightKg", "targetMuscles",
        "PickContext", "SlotRole", "hardSets", "workoutExerciseId", "SECONDS_PER_WORKING_SET",
    )
    banned.forEach { token ->
        assertFalse(token in text, "field name $token in: $text")
    }
    assertFalse('_' in text, "underscore in: $text")
    val covered = Regex(
        """\d+(?:\.\d+)?(?:\s+to\s+\d+(?:\.\d+)?)?\s+(?:sets|set|seconds|second|minutes|minute|reps|days|day)""",
        RegexOption.IGNORE_CASE,
    )
    val spans = covered.findAll(text).flatMap { it.range.toList() }.toSet()
    Regex("""\d+(?:\.\d+)?""").findAll(text).forEach { m ->
        assertTrue(m.range.any { it in spans }, "number '${m.value}' has no unit in: $text")
    }
}

internal fun planMainDir(): File {
    val cwd = File("").absoluteFile
    return listOf(
        File(cwd, "src/main/kotlin/com/gymbuddy/domain/plan"),
        File(cwd, "domain/src/main/kotlin/com/gymbuddy/domain/plan"),
        File(cwd, "../src/main/kotlin/com/gymbuddy/domain/plan"),
    ).first { it.isDirectory }
}
