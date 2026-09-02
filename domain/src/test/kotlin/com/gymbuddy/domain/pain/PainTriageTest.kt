package com.gymbuddy.domain.pain

import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.adapt.Adjustment
import com.gymbuddy.domain.adapt.DrainOn
import com.gymbuddy.domain.catalog.CatalogTest
import com.gymbuddy.domain.catalog.Provenance
import com.gymbuddy.domain.intake.PainCharacter
import com.gymbuddy.domain.intake.RedFlag
import com.gymbuddy.domain.intake.ReferralCopy
import com.gymbuddy.domain.picker.PickContext
import com.gymbuddy.domain.picker.Slot
import com.gymbuddy.domain.picker.SlotRole
import com.gymbuddy.domain.picker.candidates
import com.gymbuddy.domain.plan.PlannedEntry
import com.gymbuddy.domain.plan.PlannedSession
import com.gymbuddy.domain.plan.toProgramming
import com.gymbuddy.domain.programming.Effort
import com.gymbuddy.domain.programming.Goal
import com.gymbuddy.domain.programming.IntRange2
import com.gymbuddy.domain.programming.Severity
import com.gymbuddy.domain.programming.TrainingAge
import com.gymbuddy.domain.programming.UserContext
import com.gymbuddy.domain.programming.validateSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PainTriageTest {
    private val catalog = CatalogTest.loadCatalog()
    private val provenance = Provenance(
        confidence = "low",
        basis = "judgement",
        source = "pain triage test",
        reviewed = PainConstants.REVIEWED,
    )

    @Test
    fun intensityNineWithBenignProfileDoesNotRefer() {
        val nine = triage(soreness(intensity = 9))
        val one = triage(soreness(intensity = 1))
        assertIs<PainOutcome.LikelyTrainingSoreness>(nine)
        assertIs<PainOutcome.LikelyTrainingSoreness>(one)
        assertEquals(nine, one)
        val proposal = propose(soreness(intensity = 9), mixedSession(), fullGym(), catalog)
        assertEquals(9, proposal.intensityRecorded)
        assertFalse(proposal.outcome is PainOutcome.ReferBeforeContinuing)
        assertFalse(proposal.outcome is PainOutcome.UrgentReferral)
        assertFalse("9" in proposal.spoken)
    }

    @Test
    fun intensityTwoWithRadiatingNumbnessDoesRefer() {
        val pins = triage(
            PainInterview(
                intensity = 2,
                where = "left leg",
                travel = TravelAnswer.PINS,
                currentSlug = "back-squat",
                currentName = "Back Squat",
                currentPattern = "squat",
                slot = 0,
            ),
        )
        val radiating = triage(
            PainInterview(
                intensity = 2,
                where = "left leg",
                character = PainCharacter.RADIATING,
                currentSlug = "back-squat",
            ),
        )
        val refer = assertIs<PainOutcome.ReferBeforeContinuing>(pins)
        assertIs<PainOutcome.ReferBeforeContinuing>(radiating)
        assertTrue(refer.mayTrainOtherAreas)
        val proposal = propose(
            PainInterview(intensity = 2, where = "left leg", travel = TravelAnswer.PINS, currentSlug = "back-squat"),
            mixedSession(),
            fullGym(),
            catalog,
        )
        assertEquals(2, proposal.intensityRecorded)
        assertFalse("2" in proposal.spoken)
        assertTrue("Pain that travels down the leg isn't something to train through." in proposal.spoken)
        assertFalse("That's travelling" in proposal.spoken, proposal.spoken)
    }

    @Test
    fun questionFiveUnansweredNeverProducesSoreness() {
        val missingTravel = soreness().copy(travel = null)
        val outcome = triage(missingTravel)
        val need = assertIs<PainOutcome.NeedMore>(outcome)
        assertEquals(PainQuestionId.TRAVEL, need.nextQuestion)
        assertFalse(outcome is PainOutcome.LikelyTrainingSoreness)
    }

    @Test
    fun anyUnansweredQuestionNeverProducesTheBenignOutcome() {
        val gaps = listOf(
            soreness().copy(spot = null),
            soreness().copy(onset = null),
            soreness().copy(sides = null),
            soreness().copy(character = null),
            soreness().copy(travel = null),
            soreness().copy(atRest = null),
            PainInterview(where = "shoulder"),
            PainInterview(where = "quads", character = PainCharacter.DULL),
        )
        gaps.forEach { interview ->
            val outcome = triage(interview)
            assertFalse(
                outcome is PainOutcome.LikelyTrainingSoreness,
                "benign with gaps: $interview -> $outcome",
            )
        }
        val unknownPlace = soreness().copy(where = "somewhere")
        assertIs<PainOutcome.ReferBeforeContinuing>(triage(unknownPlace))
    }

    @Test
    fun chestBreathlessnessDizzinessHandOffToContractThirty() {
        val chest = triage(PainInterview(where = "chest"))
        val breath = triage(PainInterview(urgent = RedFlag.BREATHLESSNESS_AT_REST_OR_LIGHT_EXERTION))
        val dizzy = triage(PainInterview(urgent = RedFlag.DIZZINESS_OR_FAINTING))
        val chestOut = assertIs<PainOutcome.UrgentReferral>(chest)
        val breathOut = assertIs<PainOutcome.UrgentReferral>(breath)
        val dizzyOut = assertIs<PainOutcome.UrgentReferral>(dizzy)
        assertEquals(listOf(RedFlag.CHEST_PAIN), chestOut.flags)
        assertEquals(listOf(RedFlag.BREATHLESSNESS_AT_REST_OR_LIGHT_EXERTION), breathOut.flags)
        assertEquals(listOf(RedFlag.DIZZINESS_OR_FAINTING), dizzyOut.flags)
        val chestSpoken = propose(PainInterview(where = "chest"), mixedSession(), fullGym(), catalog)
        assertEquals(
            "${PainCopy.IN_SESSION_CHEST} ${ReferralCopy.RECENCY_URGENCY}",
            chestSpoken.spoken,
        )
        assertTrue(chestSpoken.spoken.startsWith("Stop. Pain in the chest isn't something we work through."))
        assertTrue(ReferralCopy.RECENCY_URGENCY in chestSpoken.spoken)
        assertFalse("You marked" in chestSpoken.spoken)
        assertTrue("You marked chest pain" in ReferralCopy.forFlag(RedFlag.CHEST_PAIN))
        assertTrue(chestSpoken.reducedSession!!.entries.isEmpty())
        assertNull(nextQuestion(PainInterview(where = "chest")))
        val pecs = triage(soreness().copy(where = "pecs"))
        assertIs<PainOutcome.LikelyTrainingSoreness>(pecs)
    }

    @Test
    fun headPainDuringExertionRefersRatherThanUrgent() {
        val outcome = triage(soreness().copy(where = "head"))
        assertIs<PainOutcome.ReferBeforeContinuing>(outcome)
        assertFalse(outcome is PainOutcome.UrgentReferral)
    }

    @Test
    fun noOutputStringContainsAConditionDisplayName() {
        val names = catalog.conditions.values.map { it.name }
        ownSpokenLines().forEach { text ->
            names.forEach { name ->
                assertFalse(
                    text.contains(name, ignoreCase = true),
                    "pain copy contains condition name '$name': $text",
                )
            }
            DIAGNOSIS.forEach { word ->
                assertFalse(
                    text.contains(word, ignoreCase = true),
                    "pain copy names a condition '$word': $text",
                )
            }
            assertSpeakablePain(text)
        }
        urgentSpokenLines().forEach { text ->
            names.forEach { name ->
                assertFalse(
                    text.contains(name, ignoreCase = true),
                    "urgent copy contains condition name '$name': $text",
                )
            }
            assertSpeakablePain(text)
        }
    }

    @Test
    fun stopThisMovementAddsAPickerExclusion() {
        val interview = stopMovement()
        val session = mixedSession()
        val proposal = propose(interview, session, fullGym(), catalog)
        assertIs<PainOutcome.StopThisMovement>(proposal.outcome)
        assertEquals(setOf("back-squat"), proposal.excludeSlugs)
        assertIs<Adjustment.DropSlot>(proposal.adjustment)
        assertNotNull(proposal.sibling)
        assertTrue(proposal.sibling!!.exercise.slug != "back-squat")
        val excluded = withExclusions(fullGym(), proposal.excludeSlugs)
        val squat = candidates(Slot("squat", SlotRole.PRIMARY), catalog, excluded)
        assertTrue(squat.survivors.none { it.slug == "back-squat" })
        assertTrue(squat.exclusions.any { it.exercise.slug == "back-squat" })
    }

    @Test
    fun referBeforeContinuingWithOtherAreasYieldsALegalReducedSession() {
        val interview = PainInterview(
            where = "shoulder",
            travel = TravelAnswer.TRAVELS,
            currentSlug = "bench-press",
            currentName = "Bench Press",
            currentPattern = "horizontal push",
            slot = 1,
        )
        val session = mixedSession()
        val before = session.entries.toList()
        val proposal = propose(interview, session, fullGym(), catalog)
        val refer = assertIs<PainOutcome.ReferBeforeContinuing>(proposal.outcome)
        assertTrue(refer.mayTrainOtherAreas)
        val reduced = assertNotNull(proposal.reducedSession)
        assertTrue(reduced.entries.isNotEmpty())
        assertTrue(reduced.entries.none { it.exercise.slug == "bench-press" })
        assertTrue(reduced.entries.none { it.slot.pattern == "horizontal push" })
        assertTrue(reduced.entries.any { it.exercise.pattern == "squat" || it.slot.pattern == "squat" })
        val user = fullGym().user.copy(sharpPainSlugs = proposal.excludeSlugs)
        val violations = validateSession(reduced.toProgramming(), user, catalog)
        assertTrue(violations.none { it.severity == Severity.BLOCK }, violations.toString())
        assertEquals(before, session.entries)
    }

    @Test
    fun nothingMutatesThePlanUntilAccepted() {
        val session = mixedSession()
        val before = session.entries.map { it.exercise.slug }
        val interviews = listOf(soreness(), stopMovement(), PainInterview(where = "chest"))
        interviews.forEach { interview ->
            propose(interview, session, fullGym(), catalog)
            assertEquals(before, session.entries.map { it.exercise.slug })
        }
    }

    @Test
    fun triageIsQueuedDuringSetActiveAndDrainedOnRest() {
        val spoken = PainCopy.question(PainQuestionId.SPOT)
        assertEquals(DrainOn.RESTING, propose(PainInterview(where = "shoulder"), mixedSession(), fullGym(), catalog).drainOn)
        assertFalse(maySpeakPainNow(WorkoutSessionState.SET_ACTIVE))
        assertTrue(maySpeakPainNow(WorkoutSessionState.RESTING))
        val queued = queuePainTalk(WorkoutSessionState.SET_ACTIVE, spoken)
        assertEquals(DrainOn.RESTING, queued.drainOn)
        assertEquals(spoken, queued.text)
    }

    @Test
    fun painRecordAppearsInTheNextSessionFollowUp() {
        val proposal = propose(stopMovement().copy(where = "shoulder"), mixedSession(), fullGym(), catalog)
        val record = assertNotNull(proposal.record)
        assertEquals("shoulder", record.where)
        assertEquals("back-squat", record.stoppedSlug)
        assertEquals("How's that shoulder today?", followUp(record))
        assertEquals("How's that left knee today?", followUp(SessionPainRecord("my left knee", 4, "back-squat", "stop")))
    }

    @Test
    fun everySpokenLineIsPlainLanguage() {
        ownSpokenLines().forEach { assertSpeakablePain(it) }
        urgentSpokenLines().forEach { assertSpeakablePain(it) }
        assertFalse('_' in PainCopy.soreness())
        assertFalse("LikelyTrainingSoreness" in PainCopy.soreness())
        assertFalse("DOMS" in PainCopy.soreness())
    }

    @Test
    fun painConstantsHaveDecisionRows() {
        val src = painMainDir().listFiles { f -> f.extension == "kt" }!!.joinToString("\n") { it.readText() }
        val names = Regex("""const val ([A-Z][A-Z0-9_]*)\s*=""").findAll(src).map { it.groupValues[1] }.toList()
        val decisions = decisionsFile().readText()
        names.forEach { assertTrue(it in decisions, it) }
        assertTrue("never read by `triage()`" in decisions.lowercase() || "never as a gate" in decisions.lowercase())
    }

    @Test
    fun outOfRangeIntensityIsDroppedFromHistoryNotUsedToRoute() {
        val ok = propose(soreness(intensity = 4), mixedSession(), fullGym(), catalog)
        val bad = propose(soreness(intensity = 11), mixedSession(), fullGym(), catalog)
        assertEquals(ok.outcome, bad.outcome)
        assertEquals(ok.spoken, bad.spoken)
        assertEquals(4, ok.intensityRecorded)
        assertNull(bad.intensityRecorded)
    }

    @Test
    fun printWhatSomeoneHearsOutOfBreath() {
        val session = mixedSession()
        val ctx = fullGym()
        val cases = listOf(
            "Q1" to propose(PainInterview(where = "shoulder"), session, ctx, catalog),
            "soreness-quad-9" to propose(soreness(intensity = 9), session, ctx, catalog),
            "sharp-knee" to propose(stopMovement(), session, ctx, catalog),
            "pins-leg-2" to propose(
                PainInterview(intensity = 2, where = "left leg", travel = TravelAnswer.PINS, currentSlug = "back-squat", currentPattern = "squat", slot = 0),
                session,
                ctx,
                catalog,
            ),
            "chest" to propose(PainInterview(where = "chest"), session, ctx, catalog),
            "night" to propose(PainInterview(where = "shoulder", nightWakes = true, currentSlug = "bench-press", currentPattern = "horizontal push"), session, ctx, catalog),
        )
        println("===== pain triage as heard at rest, still puffed =====")
        PainQuestionId.entries.forEach { q ->
            println("question ${q.name}: ${PainCopy.question(q)}")
        }
        cases.forEach { (label, p) ->
            println("$label [${p.outcome::class.simpleName}] intensityRecorded=${p.intensityRecorded}")
            println("  ${p.spoken}")
        }
        println("follow-up: ${followUp(SessionPainRecord("shoulder", 4, "bench-press", "stop"))}")
    }

    private fun soreness(intensity: Int? = null) = PainInterview(
        intensity = intensity,
        where = "quads",
        spot = SpotAnswer.SPREAD,
        onset = OnsetAnswer.BUILT_UP,
        sides = SidesAnswer.BOTH,
        character = PainCharacter.DULL,
        travel = TravelAnswer.NO,
        atRest = AtRestAnswer.WORSE_WITH_MOVE,
        currentSlug = "back-squat",
        currentName = "Back Squat",
        currentPattern = "squat",
        slot = 0,
    )

    private fun stopMovement() = PainInterview(
        where = "left knee",
        spot = SpotAnswer.ONE_SPOT,
        onset = OnsetAnswer.SUDDEN,
        sides = SidesAnswer.ONE,
        character = PainCharacter.SHARP,
        travel = TravelAnswer.NO,
        currentSlug = "back-squat",
        currentName = "Back Squat",
        currentPattern = "squat",
        slot = 0,
    )

    private fun mixedSession(): PlannedSession = PlannedSession(
        day = "Day A",
        entries = listOf(
            entry("back-squat"),
            entry("bench-press"),
            entry("lat-pulldown"),
        ),
    )

    private fun entry(slug: String): PlannedEntry {
        val ex = catalog.bySlug(slug)!!
        return PlannedEntry(
            exercise = ex,
            slot = Slot(ex.pattern, SlotRole.PRIMARY),
            sets = 3,
            repRange = IntRange2(8, 12, provenance),
            restSec = IntRange2(90, 150, provenance),
            effort = Effort(2, 4, provenance),
            why = emptyList(),
            priority = 1,
        )
    }

    private fun fullGym() = PickContext(
        user = UserContext(
            Goal.HYPERTROPHY,
            TrainingAge.BEGINNER,
            daysPerWeek = 3,
            typicalDurationMin = 60,
        ),
        equipment = catalog.equipment.keys,
    )

    private fun ownSpokenLines(): List<String> = listOf(
        PainCopy.question(PainQuestionId.SPOT),
        PainCopy.question(PainQuestionId.ONSET),
        PainCopy.question(PainQuestionId.SIDES),
        PainCopy.question(PainQuestionId.CHARACTER),
        PainCopy.question(PainQuestionId.TRAVEL),
        PainCopy.question(PainQuestionId.AT_REST),
        PainCopy.soreness(),
        PainCopy.stopMovement("Back Squat", null),
        PainCopy.stopMovement("Back Squat", "Walking Lunge"),
        PainCopy.referOtherAreas("left leg"),
        PainCopy.referOtherAreas("shoulder"),
        PainCopy.referNight(),
        PainCopy.referOnly(),
        PainCopy.IN_SESSION_CHEST,
        PainCopy.followUp("shoulder"),
        PainCopy.followUp("my left knee"),
        PainCopy.followUp("quads"),
    )

    private fun urgentSpokenLines(): List<String> = listOf(
        PainCopy.urgent(listOf(RedFlag.CHEST_PAIN)),
        PainCopy.urgent(listOf(RedFlag.BREATHLESSNESS_AT_REST_OR_LIGHT_EXERTION)),
        PainCopy.urgent(listOf(RedFlag.DIZZINESS_OR_FAINTING)),
    )

    private fun painMainDir(): java.io.File {
        val cwd = java.io.File("").absoluteFile
        return listOf(
            java.io.File(cwd, "src/main/kotlin/com/gymbuddy/domain/pain"),
            java.io.File(cwd, "domain/src/main/kotlin/com/gymbuddy/domain/pain"),
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

private val DIAGNOSIS = listOf(
    "DOMS",
    "delayed onset",
    "tendonitis",
    "tendinitis",
    "tendinopathy",
    "rotator cuff",
    "sciatica",
    "herniat",
    "arthritis",
    "strain",
    "sprain",
    "tear",
    "torn",
    "diagnosis",
    "diagnos",
)

private fun assertSpeakablePain(text: String) {
    val banned = listOf(
        "PainCharacter", "PainLocationKind", "PainOutcome", "PainInterview",
        "PainQuestionId", "intensityRecorded", "SlotIndex", "INTENSITY_MAX",
        "INTENSITY_MIN", "LikelyTrainingSoreness", "StopThisMovement",
        "ReferBeforeContinuing", "UrgentReferral", "NeedMore",
    )
    banned.forEach { token ->
        assertFalse(token in text, "field name $token in: $text")
    }
    assertFalse('_' in text, "underscore in: $text")
}
