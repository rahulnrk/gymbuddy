package com.gymbuddy.domain.cardio

import com.gymbuddy.domain.catalog.CatalogIds
import com.gymbuddy.domain.catalog.CatalogTest
import com.gymbuddy.domain.picker.PickContext
import com.gymbuddy.domain.plan.PlanResult
import com.gymbuddy.domain.plan.PlannedSession
import com.gymbuddy.domain.plan.Templates
import com.gymbuddy.domain.plan.buildWeek
import com.gymbuddy.domain.programming.Goal
import com.gymbuddy.domain.programming.TrainingAge
import com.gymbuddy.domain.programming.UserContext
import com.gymbuddy.domain.programming.cardioPlacement
import com.gymbuddy.domain.warmup.MobilityJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CardioTest {
    private val catalog = CatalogTest.loadCatalog()
    private val library = CardioJson.load(
        CatalogTest.knowledge("catalog/cardio-modalities.json"),
        CatalogTest.knowledge("catalog/cardio-protocols.json"),
    )
    private val warmups = MobilityJson.loadWarmups(CatalogTest.knowledge("catalog/warmups.json"))
    private val stretches = MobilityJson.loadStretches(CatalogTest.knowledge("catalog/stretches.json"))
    private val templates = Templates.load(
        listOf(
            "full-body-3d-beginner.json",
            "full-body-3d.json",
            "upper-lower-4d.json",
            "ppl-6d.json",
        ).map { CatalogTest.knowledge("templates/$it") },
    )

    @Test
    fun schemasDecodeAndTokensResolve() {
        assertEquals(12, library.modalities.size)
        assertEquals(7, library.protocols.size)
        library.modalities.forEach { m ->
            assertEquals(CatalogIds.fromSlug(m.slug).toString(), m.id, m.slug)
            m.primaryDemand.forEach { muscle ->
                assertNotNull(catalog.muscles[muscle], "${m.slug} unknown muscle $muscle")
            }
            m.requiredEquipment.flatten().forEach { e ->
                assertNotNull(catalog.equipment[e], "${m.slug} unknown gear $e")
            }
            m.contraindications.forEach { c ->
                assertNotNull(catalog.conditions[c], "${m.slug} unknown condition $c")
            }
        }
        library.protocols.forEach { p ->
            assertEquals(CatalogIds.fromSlug(p.slug).toString(), p.id, p.slug)
            assertTrue(p.effortGuidance.length >= 20, p.slug)
            assertNoInventedIntensity(p.effortGuidance)
        }
        assertEquals(77, catalog.exercises.size)
        assertEquals(30, warmups.drills.size)
        assertEquals(30, stretches.stretches.size)
    }

    @Test
    fun compositionNotEnumeration() {
        var legal = 0
        library.protocols.forEach { p ->
            val hosts = library.modalities.filter { p.type in it.suitableProtocolTypes }
            assertTrue(hosts.isNotEmpty(), p.slug)
            legal += hosts.size
        }
        assertTrue(legal < 236, "legal=$legal should be composition, not 236 rows")
        assertTrue(legal > 12, "legal=$legal")
        val ctx = gymIntermediate()
        val kept = filterPairs(library, ctx).kept
        kept.forEach { (m, p) ->
            assertTrue(p.type in m.suitableProtocolTypes)
        }
    }

    @Test
    fun sprintIntervalsNeverForABeginner() {
        val ranked = rank(Goal.GENERAL_FITNESS, bodyweightBeginner(), 20, library)
        assertTrue(ranked.isNotEmpty())
        assertTrue(ranked.none { it.protocol.type == ProtocolType.SPRINT_INTERVALS })
        val exclusions = filterPairs(library, bodyweightBeginner()).exclusions
        assertTrue(exclusions.any { it is CardioExclusion.NeedsAerobicBase && it.protocol.type == ProtocolType.SPRINT_INTERVALS })
    }

    @Test
    fun highImpactNeverForKneePain() {
        val ctx = gymIntermediate().let {
            it.copy(user = it.user.copy(conditions = listOf("anterior-knee-pain")))
        }
        val ranked = rank(Goal.ENDURANCE, ctx, 30, library)
        assertTrue(ranked.none { it.modality.impact == CardioImpact.HIGH }, ranked.map { it.modality.slug }.toString())
        assertTrue(ranked.none { it.modality.slug in setOf("running", "skipping", "sled-push") })
        val exclusions = filterPairs(library, ctx).exclusions
        assertTrue(exclusions.any { it is CardioExclusion.ImpactBlocked && it.modality.slug == "running" })
    }

    @Test
    fun noEquipmentStillGetsWalking() {
        val ctx = PickContext(
            user = UserContext(Goal.GENERAL_FITNESS, TrainingAge.BEGINNER),
            equipment = emptySet(),
        )
        val ranked = rank(Goal.GENERAL_FITNESS, ctx, 20, library)
        assertTrue(ranked.any { it.modality.slug == "walking" }, ranked.map { it.modality.slug }.toString())
        assertTrue(ranked.none { it.modality.slug == "cycling" })
    }

    @Test
    fun hardIntervalsStayOffHeavyLowerDays() {
        val ctx = gymIntermediate()
        val built = assertIs<PlanResult.Built>(
            buildWeek(templates.single { it.id == "full-body-3d" }, ctx, catalog, warmups = warmups, stretches = stretches),
        )
        val placed = placeOnWeek(built.week, Goal.STRENGTH, ctx, library, minutesPerLiftDay = 25)
        placed.filter { it.entries.isNotEmpty() }.forEach { day ->
            val summary = sessionSummary(day)
            val advice = cardioPlacement(summary, Goal.STRENGTH)
            if (advice.avoidHardIntervals) {
                val cardio = day.cardio
                assertNotNull(cardio, day.day)
                assertEquals(ProtocolIntensity.EASY, cardio.protocol.intensity, "${day.day} ${cardio.protocol.slug}")
            }
        }
    }

    @Test
    fun weeklyTotalsLandInAcsmRange() {
        val target = weeklyCardioTarget(Goal.STRENGTH, TrainingAge.INTERMEDIATE)
        assertEquals(CardioConstants.WEEKLY_MODERATE_MIN, target.min)
        assertEquals(CardioConstants.WEEKLY_MODERATE_MAX, target.max)
        assertEquals(
            weeklyCardioTarget(Goal.ENDURANCE, TrainingAge.BEGINNER).min,
            CardioConstants.WEEKLY_MODERATE_MIN,
        )

        val easy = rank(Goal.STRENGTH, gymIntermediate(), 25, library)
            .first { it.protocol.intensity == ProtocolIntensity.EASY }
        assertEquals(0.0, acsmModerateEquivalentMinutes(easy.copy(durationMin = 75)))
        val hard = rank(Goal.ENDURANCE, gymEndurance(), 25, library).first { it.protocol.hard }
        assertEquals(50.0, acsmModerateEquivalentMinutes(hard.copy(durationMin = 25)))

        val ctx = gymIntermediate()
        val built = assertIs<PlanResult.Built>(
            buildWeek(templates.single { it.id == "full-body-3d" }, ctx, catalog, warmups = warmups, stretches = stretches),
        )
        val placed = placeOnWeek(built.week, Goal.STRENGTH, ctx, library, minutesPerLiftDay = 25)
        val liftCardio = placed.filter { it.entries.isNotEmpty() }.mapNotNull { it.cardio }
        assertEquals(0.0, liftCardio.sumOf { acsmModerateEquivalentMinutes(it) })
        val credit = weekAcsmMinutes(placed)
        assertTrue(credit >= 0.0)
        assertTrue(
            credit >= target.min || placed.count { it.cardio != null } >= CardioConstants.FREQUENCY_MAX,
            "credit=$credit placed=${placed.size}",
        )
    }

    @Test
    fun weekVariesModalityThenProtocol() {
        val ctx = gymIntermediate()
        val built = assertIs<PlanResult.Built>(
            buildWeek(templates.single { it.id == "full-body-3d" }, ctx, catalog, warmups = warmups, stretches = stretches),
        )
        val placed = placeOnWeek(built.week, Goal.STRENGTH, ctx, library, minutesPerLiftDay = 25)
        val liftMods = placed.filter { it.entries.isNotEmpty() }.mapNotNull { it.cardio?.modality?.slug }
        assertEquals(liftMods.size, liftMods.toSet().size, liftMods.toString())
        val allPairs = placed.mapNotNull { it.cardio }.map { "${it.modality.slug}/${it.protocol.slug}" }
        assertEquals(allPairs.size, allPairs.toSet().size, allPairs.toString())
    }

    @Test
    fun heavyLiftWeekNamesTheMissingIntervalDay() {
        val ctx = gymIntermediate()
        val built = assertIs<PlanResult.Built>(
            buildWeek(templates.single { it.id == "full-body-3d" }, ctx, catalog, warmups = warmups, stretches = stretches),
        )
        val placed = placeOnWeek(built.week, Goal.STRENGTH, ctx, library, minutesPerLiftDay = 25)
        val note = noRoomForIntervalsNote(3, addedSeparateDay = placed.any { it.entries.isEmpty() })
        assertTrue(
            placed.filter { it.entries.isNotEmpty() }.all { day -> day.notes.any { it == note } },
            placed.flatMap { it.notes }.toString(),
        )
        placed.filter { it.entries.isEmpty() }.forEach { extra ->
            val cardio = extra.cardio
            assertNotNull(cardio, extra.day)
            assertTrue(cardio.protocol.intensity != ProtocolIntensity.EASY, extra.day)
        }
        println(speakWeek(placed))
    }

    @Test
    fun progressionChangesOneVariable() {
        val ctx = gymIntermediate()
        val first = rank(Goal.ENDURANCE, ctx, 25, library).first { it.protocol.slug == "easy-steady" }
        val longer = nextCardioSession(first, sessionsThisWeek = 2, Goal.ENDURANCE, ctx, library)
        assertEquals(ProgressVariable.DURATION, longer.changed)
        assertEquals(first.protocol.slug, longer.prescription.protocol.slug)
        assertEquals(first.durationMin + CardioConstants.DURATION_STEP_MIN, longer.prescription.durationMin)

        val atCap = first.copy(durationMin = first.protocol.durationMax)
        val freq = nextCardioSession(atCap, sessionsThisWeek = 2, Goal.ENDURANCE, ctx, library)
        assertEquals(ProgressVariable.FREQUENCY, freq.changed)
        assertEquals(atCap.durationMin, freq.prescription.durationMin)
        assertEquals(atCap.protocol.slug, freq.prescription.protocol.slug)

        val recovery = rank(Goal.ENDURANCE, ctx, 30, library).first { it.protocol.slug == "easy-recovery" }
            .copy(durationMin = 30)
        val intensity = nextCardioSession(
            recovery,
            sessionsThisWeek = CardioConstants.FREQUENCY_MAX,
            Goal.ENDURANCE,
            ctx,
            library,
        )
        assertEquals(ProgressVariable.INTENSITY, intensity.changed)
        assertEquals(recovery.durationMin, intensity.prescription.durationMin)
        assertTrue(intensity.prescription.protocol.slug != recovery.protocol.slug)
    }

    @Test
    fun prescriptionsHaveNoPaceSpeedWattOrHeartRate() {
        val ctxs = listOf(bodyweightBeginner(), gymIntermediate(), gymEndurance())
        ctxs.forEach { ctx ->
            rank(ctx.user.goal, ctx, 25, library).forEach { p ->
                val heard = speak(p)
                assertNoInventedIntensity(heard)
                assertTrue(p.why.isNotEmpty(), p.modality.slug)
                p.why.forEach { assertNoInventedIntensity(it.explanation) }
            }
        }
    }

    @Test
    fun everyPrescriptionCarriesAReason() {
        rank(Goal.GENERAL_FITNESS, bodyweightBeginner(), 20, library).forEach { p ->
            assertTrue(p.why.isNotEmpty())
            assertTrue(p.why.any { it.explanation.isNotBlank() })
        }
    }

    @Test
    fun rankIsDeterministicOverOneHundredRuns() {
        val ctx = gymIntermediate()
        val first = rank(Goal.ENDURANCE, ctx, 30, library).map { "${it.modality.slug}/${it.protocol.slug}/${it.durationMin}" }
        repeat(100) {
            val next = rank(Goal.ENDURANCE, ctx, 30, library).map { "${it.modality.slug}/${it.protocol.slug}/${it.durationMin}" }
            assertEquals(first, next)
        }
    }

    @Test
    fun threeSpokenPrescriptions() {
        val beginner = rank(Goal.GENERAL_FITNESS, bodyweightBeginner(), 20, library).first()
        assertEquals("walking", beginner.modality.slug)
        val liftDay = run {
            val ctx = gymIntermediate()
            val built = assertIs<PlanResult.Built>(
                buildWeek(templates.single { it.id == "full-body-3d" }, ctx, catalog, warmups = warmups, stretches = stretches),
            )
            val lower = built.week.first { sessionSummary(it).patternIds.any { p -> p == "squat" || p == "hinge" } }
            val placed = placeOnSession(lower, Goal.HYPERTROPHY, ctx, library, 25)
            assertNotNull(placed)
            assertFalse(placed.protocol.hard)
            placed
        }
        val cardioDay = rank(Goal.ENDURANCE, gymEndurance(), 30, library).first { it.protocol.hard || it.protocol.type == ProtocolType.TEMPO || it.protocol.type == ProtocolType.INTERVALS }
        println("BEGINNER: ${speak(beginner)}")
        println("LIFT_DAY: ${speak(liftDay)}")
        println("CARDIO_DAY: ${speak(cardioDay)}")
        assertNoInventedIntensity(speak(beginner))
        assertNoInventedIntensity(speak(liftDay))
        assertNoInventedIntensity(speak(cardioDay))
    }

    private fun bodyweightBeginner() = PickContext(
        user = UserContext(Goal.GENERAL_FITNESS, TrainingAge.BEGINNER),
        equipment = setOf("none"),
        hasCardioHistory = false,
    )

    private fun gymIntermediate() = PickContext(
        user = UserContext(
            goal = Goal.HYPERTROPHY,
            trainingAge = TrainingAge.INTERMEDIATE,
            typicalDurationMin = 60,
            daysPerWeek = 3,
        ),
        equipment = catalog.equipment.keys,
        hasCardioHistory = true,
    )

    private fun gymEndurance() = PickContext(
        user = UserContext(
            goal = Goal.ENDURANCE,
            trainingAge = TrainingAge.INTERMEDIATE,
            typicalDurationMin = 45,
            daysPerWeek = 3,
        ),
        equipment = catalog.equipment.keys,
        hasCardioHistory = true,
    )
}
