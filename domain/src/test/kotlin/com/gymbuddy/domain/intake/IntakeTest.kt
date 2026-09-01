package com.gymbuddy.domain.intake

import com.gymbuddy.domain.catalog.CatalogTest
import com.gymbuddy.domain.programming.Goal
import com.gymbuddy.domain.programming.PlannedExercise
import com.gymbuddy.domain.programming.PlannedSession
import com.gymbuddy.domain.programming.TrainingAge
import com.gymbuddy.domain.programming.validateSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IntakeTest {
    private val catalog = CatalogTest.loadCatalog()
    private val vocab = EquipmentVocab(catalog.equipment)
    private val conditionNames = catalog.conditions.mapValues { it.value.name }

    @Test
    fun redFlagYesIsReferAndNotReady() {
        val intake = readyBase().copy(
            healthScreen = clearScreen().copy(
                flags = clearFlags() + (RedFlag.CHEST_PAIN to Answer.YES),
            ),
        )
        val result = evaluate(intake, vocab)
        val refer = assertIs<IntakeResult.Refer>(result)
        assertTrue(RedFlag.CHEST_PAIN in refer.outcome.flags)
        assertFalse(result is IntakeResult.Ready)
        assertFalse(screen(intake.healthScreen) is ScreenOutcome.Clear)
    }

    @Test
    fun redFlagUnknownIsIncompleteNeverClear() {
        val health = clearScreen().copy(
            flags = clearFlags() + (RedFlag.DIZZINESS_OR_FAINTING to Answer.UNKNOWN),
        )
        val outcome = screen(health)
        assertIs<ScreenOutcome.Incomplete>(outcome)
        assertFalse(outcome is ScreenOutcome.Clear)
        val result = evaluate(readyBase().copy(healthScreen = health), vocab)
        assertIs<IntakeResult.NeedMore>(result)
        assertFalse(result is IntakeResult.Ready)
    }

    @Test
    fun skippingEveryScreeningQuestionIsNotReady() {
        val skipped = HealthScreen()
        assertIs<ScreenOutcome.Incomplete>(screen(skipped))
        val result = evaluate(readyBase().copy(healthScreen = skipped), vocab)
        assertFalse(result is IntakeResult.Ready)
        assertIs<IntakeResult.NeedMore>(result)
        val blank = evaluate(Intake(), vocab)
        assertFalse(blank is IntakeResult.Ready)
    }

    @Test
    fun readyUnreachableWhileRequiredMissing() {
        val withScreen = Intake(healthScreen = clearScreen())
        val result = evaluate(withScreen, vocab)
        val need = assertIs<IntakeResult.NeedMore>(result)
        assertTrue(need.missing.any { it.tier == Tier.REQUIRED_TO_SUGGEST })
        assertEquals(QuestionId.GOAL, need.nextQuestion)
    }

    @Test
    fun missingShapingNeverBlocksReady() {
        val intake = readyBase().copy(
            recovery = null,
            dislikes = emptySet(),
            trainsWithPartner = null,
            priorInjuriesResolved = emptyList(),
        )
        assertIs<IntakeResult.Ready>(evaluate(intake, vocab))
    }

    @Test
    fun bodyweightOnlyMatchesBodyweightSubset() {
        val tokens = EquipmentAvailability(EquipmentPreset.BODYWEIGHT_ONLY).resolve(vocab)
        val fromPreset = catalog.trainableWith(tokens).map { it.slug }.toSet()
        val fromNone = catalog.trainableWith(listOf("none")).map { it.slug }.toSet()
        assertEquals(fromNone, fromPreset)
        assertEquals(3, fromPreset.size)
        assertEquals(61, catalog.exercises.size)
    }

    @Test
    fun alsoHaveAddsDoNotHaveRemovesAndWins() {
        val home = EquipmentAvailability(EquipmentPreset.HOME_LIMITED)
        val withBar = EquipmentAvailability(
            EquipmentPreset.BODYWEIGHT_ONLY,
            alsoHave = setOf("pull-up-bar"),
        )
        val withoutBar = EquipmentAvailability(
            EquipmentPreset.HOME_LIMITED,
            doNotHave = setOf("pull-up-bar"),
        )
        val conflict = EquipmentAvailability(
            EquipmentPreset.BODYWEIGHT_ONLY,
            alsoHave = setOf("dumbbell"),
            doNotHave = setOf("dumbbell"),
        )
        assertTrue("pull-up-bar" in withBar.resolve(vocab))
        assertFalse("pull-up-bar" in EquipmentAvailability(EquipmentPreset.BODYWEIGHT_ONLY).resolve(vocab))
        assertTrue("pull-up-bar" in home.resolve(vocab))
        assertFalse("pull-up-bar" in withoutBar.resolve(vocab))
        assertFalse("dumbbell" in conflict.resolve(vocab))
        assertEquals(9, catalog.trainableWith(EquipmentAvailability(EquipmentPreset.BODYWEIGHT_ONLY, alsoHave = setOf("kettlebell")).resolve(vocab)).size)
        assertEquals(15, catalog.trainableWith(EquipmentAvailability(EquipmentPreset.BODYWEIGHT_ONLY, alsoHave = setOf("dumbbell")).resolve(vocab)).size)
    }

    @Test
    fun declaredConditionReachesValidatorBlock() {
        val declared = declareCondition("shoulder-impingement", catalog.conditions.keys, conditionNames)
        assertIs<DeclaredCondition.Known>(declared)
        val screen = clearScreen().copy(
            hasListedCondition = Answer.YES,
            conditions = listOf(declared),
        )
        val ready = assertIs<IntakeResult.Ready>(evaluate(readyBase().copy(healthScreen = screen), vocab))
        assertTrue("shoulder-impingement" in ready.context.conditions)
        val press = catalog.bySlug("overhead-press")!!
        val hits = validateSession(
            PlannedSession(listOf(PlannedExercise(press, 3))),
            ready.context,
            catalog,
        )
        assertTrue(hits.any { it.rule == "safety.contraindication" && it.severity == com.gymbuddy.domain.programming.Severity.BLOCK }, hits.toString())
    }

    @Test
    fun sharpPainOnMovementIsSafetyBlock() {
        val screen = clearScreen().copy(
            hasPain = Answer.YES,
            painReports = listOf(
                PainReport(
                    where = "front of the shoulder",
                    whenItHappens = "on the press",
                    movements = setOf("overhead-press"),
                    character = PainCharacter.SHARP,
                ),
            ),
        )
        val ready = assertIs<IntakeResult.Ready>(evaluate(readyBase().copy(healthScreen = screen), vocab))
        val press = catalog.bySlug("overhead-press")!!
        val hits = validateSession(
            PlannedSession(listOf(PlannedExercise(press, 3))),
            ready.context,
            catalog,
        )
        assertTrue(hits.any { it.rule == "safety.pain-movement" && it.severity == com.gymbuddy.domain.programming.Severity.BLOCK }, hits.toString())
        val squat = catalog.bySlug("back-squat")!!
        val other = validateSession(
            PlannedSession(listOf(PlannedExercise(squat, 3))),
            ready.context,
            catalog,
        )
        assertTrue(other.none { it.rule == "safety.pain-movement" })
    }

    @Test
    fun freeTextConditionIsUnknownNeverGuessed() {
        val ids = catalog.conditions.keys
        val names = conditionNames
        val unknown = declareCondition("rotator cuff", ids, names)
        assertIs<DeclaredCondition.Unknown>(unknown)
        val near = declareCondition("shoulder", ids, names)
        assertIs<DeclaredCondition.Unknown>(near)
        assertFalse(catalog.conditions.keys.any { it.equals("shoulder", ignoreCase = true) })
        val known = declareCondition("Shoulder impingement", ids, names)
        assertEquals("shoulder-impingement", (known as DeclaredCondition.Known).id)
    }

    @Test
    fun needMoreReturnsExactlyOneNextQuestion() {
        val result = evaluate(Intake(), vocab)
        val need = assertIs<IntakeResult.NeedMore>(result)
        assertEquals(1, listOf(need.nextQuestion).size)
        assertEquals(QuestionId.GOAL, need.nextQuestion)
        assertTrue(need.missing.size > 1)
        val afterGoal = evaluate(Intake(goal = Goal.STRENGTH), vocab)
        assertEquals(QuestionId.TRAINING_AGE, (afterGoal as IntakeResult.NeedMore).nextQuestion)
    }

    @Test
    fun clearanceRequiresExplicitDateAndIsNeverInferred() {
        val flagged = clearScreen().copy(
            flags = clearFlags() + (RedFlag.CHEST_PAIN to Answer.YES),
        )
        assertIs<ScreenOutcome.ReferBeforeTraining>(screen(flagged))
        val still = evaluate(readyBase().copy(healthScreen = flagged), vocab)
        assertIs<IntakeResult.Refer>(still)
        val cleared = recordClearance(flagged, "2026-09-01", setOf(RedFlag.CHEST_PAIN))
        assertEquals("2026-09-01", cleared.clearance!!.userReportedDate)
        val after = screen(cleared)
        assertFalse(after is ScreenOutcome.ReferBeforeTraining)
        assertTrue(after is ScreenOutcome.Clear || after is ScreenOutcome.ProceedWithCaution)
        val inferred = flagged.copy(clearance = null)
        assertIs<ScreenOutcome.ReferBeforeTraining>(screen(inferred))
    }

    @Test
    fun referralMessagesContainNoConditionNames() {
        val names = catalog.conditions.values.map { it.name }
        RedFlag.entries.forEach { flag ->
            val text = ReferralCopy.forFlag(flag)
            names.forEach { name ->
                assertFalse(
                    text.contains(name, ignoreCase = true),
                    "referral for $flag contains condition name '$name': $text",
                )
            }
        }
        val combined = ReferralCopy.forFlags(RedFlag.entries)
        names.forEach { name ->
            assertFalse(combined.contains(name, ignoreCase = true), "combined referral contains '$name'")
        }
    }

    @Test
    fun yesOutranksUnknownOnTheSameScreen() {
        val mixed = clearScreen().copy(
            flags = clearFlags() +
                (RedFlag.CHEST_PAIN to Answer.YES) +
                (RedFlag.DIZZINESS_OR_FAINTING to Answer.UNKNOWN),
        )
        assertIs<ScreenOutcome.ReferBeforeTraining>(screen(mixed))
    }

    @Test
    fun partnerIsNotAssumedFromNull() {
        val bench = catalog.bySlug("bench-press")!!
        assertEquals(6, com.gymbuddy.domain.programming.unspottedRepFloor(bench, emptyList(), null))
        assertEquals(null, com.gymbuddy.domain.programming.unspottedRepFloor(bench, emptyList(), true))
        assertEquals(6, com.gymbuddy.domain.programming.unspottedRepFloor(bench, emptyList(), false))
    }

    @Test
    fun intakeDoesNotGenerateWorkouts() {
        val dir = java.io.File("").absoluteFile
        val src = listOf(
            java.io.File(dir, "src/main/kotlin/com/gymbuddy/domain/intake"),
            java.io.File(dir, "domain/src/main/kotlin/com/gymbuddy/domain/intake"),
            java.io.File(dir, "../src/main/kotlin/com/gymbuddy/domain/intake"),
        ).first { it.isDirectory }
        val body = src.listFiles { f -> f.extension == "kt" }!!.joinToString("\n") { it.readText() }
        listOf("fun generatePlan", "fun generateWorkout", "fun buildWorkout").forEach { bad ->
            assertFalse(bad in body, bad)
        }
        assertEquals(61, catalog.exercises.size)
        assertEquals(3, equipmentCoverage(catalog, EquipmentAvailability(EquipmentPreset.BODYWEIGHT_ONLY).resolve(vocab)).exerciseCount)
        assertEquals(41, equipmentCoverage(catalog, EquipmentAvailability(EquipmentPreset.HOME_LIMITED).resolve(vocab)).exerciseCount)
        assertEquals(61, equipmentCoverage(catalog, EquipmentAvailability(EquipmentPreset.FULL_GYM).resolve(vocab)).exerciseCount)
    }

    private fun clearFlags(): Map<RedFlag, Answer> = RedFlag.entries.associateWith { Answer.NO }

    private fun clearScreen(): HealthScreen = HealthScreen(
        flags = clearFlags(),
        hasListedCondition = Answer.NO,
        hasPain = Answer.NO,
    )

    private fun readyBase(): Intake = Intake(
        goal = Goal.HYPERTROPHY,
        trainingAge = TrainingAge.INTERMEDIATE,
        daysPerWeek = 3,
        typicalSessionMinutes = 60,
        equipment = EquipmentAvailability(EquipmentPreset.FULL_GYM),
        healthScreen = clearScreen(),
    )
}
