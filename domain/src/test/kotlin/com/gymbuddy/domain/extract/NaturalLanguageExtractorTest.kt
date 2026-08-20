package com.gymbuddy.domain.extract

import com.gymbuddy.domain.CandidateType
import com.gymbuddy.domain.CommandKind
import com.gymbuddy.domain.FeedbackKind
import com.gymbuddy.domain.QuestionTopic
import com.gymbuddy.domain.SafetySeverity
import com.gymbuddy.domain.VoiceTurnState
import com.gymbuddy.domain.WeightUnit
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.context.ConversationalStateContext
import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.context.HistoricalStateContext
import com.gymbuddy.domain.context.UserStateContext
import com.gymbuddy.domain.context.WorkoutStateContext
import com.gymbuddy.domain.events.CommandCandidate
import com.gymbuddy.domain.events.SetCompletedCandidate
import com.gymbuddy.domain.events.SetCorrectedCandidate
import com.gymbuddy.domain.events.SetDeletedCandidate
import com.gymbuddy.domain.events.SubstitutionProposeCandidate
import com.gymbuddy.domain.events.UserFeedbackCandidate
import com.gymbuddy.domain.events.UserQuestionCandidate
import com.gymbuddy.domain.events.WeightChangedCandidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaturalLanguageExtractorTest {
    private val extractor = NaturalLanguageExtractor()

    private fun ctx(weight: Double? = 60.0) = FourContexts(
        workout = WorkoutStateContext(
            sessionId = "22222222-2222-4222-8222-222222222222",
            workoutId = "33333333-3333-4333-8333-333333333333",
            workoutName = "t",
            sessionState = WorkoutSessionState.RESTING,
            voiceState = VoiceTurnState.SILENT,
            currentExerciseId = "44444444-4444-4444-8444-444444444444",
            completedSets = emptyList(),
            remainingSetsPlanned = 3,
            currentWeightKg = weight,
            substitutions = emptyList(),
            startedAt = "2026-08-20T16:00:00Z",
        ),
        historical = HistoricalStateContext(),
        conversational = ConversationalStateContext(languageDetected = "en"),
        user = UserStateContext(
            "11111111-1111-4111-8111-111111111111",
            emptyList(), "intermediate", emptyList(), WeightUnit.KG, "en",
        ),
    )

    private fun run(text: String, weight: Double? = 60.0) =
        extractor.extract(ExtractionRequest(text, contexts = ctx(weight)))

    @Test
    fun got10ExtractsRepsNotWeight() {
        val r = run("Got 10.")
        val p = r.envelope.candidates.single().decodePayload() as SetCompletedCandidate
        assertEquals(CandidateType.SET_COMPLETED, r.envelope.candidates.single().type)
        assertEquals(10, p.reps)
        assertNull(p.weight)
        assertFalse(r.envelope.candidates.single().payload.containsKey("setNumber"))
        assertTrue(r.classifiedBeforeExtract)
    }

    @Test
    fun fortyForTen() {
        val p = run("40 for 10.").envelope.candidates.single().decodePayload() as SetCompletedCandidate
        assertEquals(10, p.reps)
        assertEquals(40.0, p.weight)
    }

    @Test
    fun dropWeightNotASet() {
        val r = run("Let's drop the weight.")
        assertEquals(CandidateType.WEIGHT_CHANGED, r.envelope.candidates.single().type)
        val p = r.envelope.candidates.single().decodePayload() as WeightChangedCandidate
        assertEquals(55.0, p.weight)
        assertTrue(r.envelope.candidates.none { it.type == CandidateType.SET_COMPLETED })
    }

    @Test
    fun historicalGuessIsNotASet() {
        val r = run("I think I used 55 last week.")
        assertTrue(r.envelope.candidates.none { it.type == CandidateType.SET_COMPLETED })
        val q = r.envelope.candidates.single().decodePayload() as UserQuestionCandidate
        assertEquals(QuestionTopic.HISTORY, q.topic)
        assertEquals("I think I used 55 last week.", q.questionOriginal)
    }

    @Test
    fun lastWeekQuestion() {
        val r = run("What did I do last week?")
        assertEquals(CandidateType.USER_QUESTION, r.envelope.candidates.single().type)
        assertEquals(QuestionTopic.HISTORY, (r.envelope.candidates.single().decodePayload() as UserQuestionCandidate).topic)
    }

    @Test
    fun proteinQuestion() {
        val r = run("How much protein should I have?")
        val q = r.envelope.candidates.single().decodePayload() as UserQuestionCandidate
        assertEquals(QuestionTopic.NUTRITION, q.topic)
        assertTrue(r.envelope.candidates.none { it.type == CandidateType.SET_COMPLETED })
    }

    @Test
    fun whyShoulderIsQuestion() {
        val r = run("Why am I feeling this more in my shoulder?")
        assertEquals(CandidateType.USER_QUESTION, r.envelope.candidates.single().type)
        assertTrue(r.envelope.candidates.none { it.type == CandidateType.SET_COMPLETED })
    }

    @Test
    fun correctionNo8() {
        val p = run("No, 8.").envelope.candidates.single().decodePayload() as SetCorrectedCandidate
        assertEquals(8, p.reps)
        assertEquals("last", p.target)
    }

    @Test
    fun actuallyMakeThat9() {
        val p = run("Actually make that 9.").envelope.candidates.single().decodePayload() as SetCorrectedCandidate
        assertEquals(9, p.reps)
    }

    @Test
    fun scratchThatDeletes() {
        assertTrue(run("Scratch that.").envelope.candidates.single().decodePayload() is SetDeletedCandidate)
    }

    @Test
    fun skipIsCommandNotExerciseSkipped() {
        val r = run("Let's skip this.")
        val p = r.envelope.candidates.single().decodePayload() as CommandCandidate
        assertEquals(CommandKind.SKIP, p.kind)
        assertTrue(r.envelope.candidates.none { it.type.name.contains("SKIPPED") })
    }

    @Test
    fun oneMoreIsExtraSetCommand() {
        val r = run("Can we do one more?")
        assertTrue(r.envelope.candidates.any { it.type == CandidateType.USER_QUESTION })
        assertTrue(
            r.envelope.candidates.any {
                it.type == CandidateType.COMMAND && (it.decodePayload() as CommandCandidate).kind == CommandKind.EXTRA_SET
            },
        )
    }

    @Test
    fun occupiedProposesSubstitution() {
        val r = run("That machine is occupied.")
        assertTrue(r.envelope.candidates.single().decodePayload() is SubstitutionProposeCandidate)
    }

    @Test
    fun dumbbellsInstead() {
        val p = run("Let's use dumbbells instead.").envelope.candidates.single().decodePayload() as SubstitutionProposeCandidate
        assertEquals("dumbbells", p.toExerciseNameRaw)
    }

    @Test
    fun casualHeavyIsFeedbackNotSet() {
        val r = run("Bro this is heavy.")
        assertTrue(r.envelope.candidates.none { it.type == CandidateType.SET_COMPLETED })
        val f = r.envelope.candidates.single().decodePayload() as UserFeedbackCandidate
        assertEquals(FeedbackKind.HARD, f.kind)
        assertFalse(r.hasMutationCandidate)
    }

    @Test
    fun purelyCasualHasNoCandidates() {
        listOf("Nice.", "Let's go.", "Give me a second.", "Man, that was rough.").forEach { u ->
            val r = run(u)
            assertTrue(r.envelope.candidates.isEmpty(), u)
            assertFalse(r.hasMutationCandidate, u)
        }
    }

    @Test
    fun restRequestIsQuestionNotSet() {
        val r = run("Can we take another 30 seconds?")
        assertTrue(r.envelope.candidates.any { it.type == CandidateType.USER_QUESTION })
        assertTrue(r.envelope.candidates.none { it.type == CandidateType.SET_COMPLETED })
    }

    @Test
    fun teluguSetAndQuestion() {
        val set = run("Nenu 60 kilos tho eight reps chesa.")
        val p = set.envelope.candidates.single().decodePayload() as SetCompletedCandidate
        assertEquals(8, p.reps)
        assertEquals(60.0, p.weight)
        val q = run("Last time entha weight vesanu?")
        assertEquals(CandidateType.USER_QUESTION, q.envelope.candidates.single().type)
        assertEquals("Last time entha weight vesanu?", (q.envelope.candidates.single().decodePayload() as UserQuestionCandidate).questionOriginal)
        val plan = run("Aaj kya karna hai?")
        assertEquals(CandidateType.USER_QUESTION, plan.envelope.candidates.single().type)
    }

    @Test
    fun multiSetCatchUpViaFake() {
        val r = run("First set was 12 at 40, second was 10, and I got 8 on this one.")
        assertEquals(3, r.envelope.candidates.size)
        assertTrue(r.llmRequired)
        assertEquals(listOf(12, 10, 8), r.envelope.candidates.map { (it.decodePayload() as SetCompletedCandidate).reps })
        assertEquals(40.0, (r.envelope.candidates.first().decodePayload() as SetCompletedCandidate).weight)
        assertNull((r.envelope.candidates[1].decodePayload() as SetCompletedCandidate).weight)
    }

    @Test
    fun multiLabelSetAndShoulder() {
        val r = run("I got 10 but my shoulder feels weird.")
        assertTrue(IntentLabel.LOGGING in r.intents || r.envelope.candidates.any { it.type == CandidateType.SET_COMPLETED })
        assertTrue(r.envelope.candidates.any { it.type == CandidateType.SET_COMPLETED })
        assertTrue(r.envelope.candidates.any { it.type == CandidateType.USER_FEEDBACK } || r.safety.fired)
    }

    @Test
    fun chestPainSetStillExtractsNumbersAfterSafety() {
        val r = run("I got 10 but my chest hurts.")
        assertEquals(SafetySeverity.CRITICAL, r.safety.severity)
        assertTrue(r.classifiedBeforeExtract)
        assertTrue(r.envelope.candidates.any { it.type == CandidateType.SET_COMPLETED })
        assertTrue(r.speech.suppressNormalCoaching)
    }

    @Test
    fun safetyAlwaysFirst() {
        val order = mutableListOf<String>()
        val clf = object : com.gymbuddy.domain.safety.SafetyClassifier {
            override fun classify(rawTranscript: String) = com.gymbuddy.domain.safety.DefaultSafetyClassifier().classify(rawTranscript).also { order += "safety" }
        }
        val ex = NaturalLanguageExtractor(com.gymbuddy.domain.safety.SafetyFirstTurn(clf))
        order += "before"
        ex.extract(ExtractionRequest("Got 10.", contexts = ctx()))
        assertTrue(order.indexOf("safety") >= 0)
        assertEquals("before", order.first())
    }

    @Test
    fun schemaRejectsStringRepsAndUnknownType() {
        val ten = com.gymbuddy.domain.events.CandidateEventEnvelope.parse(
            """{"schemaVersion":1,"candidates":[{"candidateId":"x","type":"SET_COMPLETED","overallConfidence":0.9,"payload":{"reps":"ten"}}]}""",
        )
        assertTrue(kotlin.runCatching { ten.candidates.single().decodePayload() }.isFailure)
        assertTrue(
            kotlin.runCatching {
                com.gymbuddy.domain.events.CandidateEventEnvelope.parse(
                    """{"schemaVersion":1,"candidates":[{"candidateId":"x","type":"HALLUCINATED_PR","overallConfidence":0.9,"payload":{}}]}""",
                )
            }.isFailure,
        )
        val fake = SchemaConstrainedFakeExtractor()
        assertTrue(fake.envelopeFor("garbage that is not a fixture").candidates.isEmpty())
        assertTrue(
            kotlin.runCatching { SchemaConstrainedFakeExtractor(temperature = 0.9) }.isFailure,
        )
    }

    @Test
    fun kaaduIsCorrection() {
        val p = run("Kaadu, 8.").envelope.candidates.single().decodePayload() as SetCorrectedCandidate
        assertEquals(8, p.reps)
    }

    @Test
    fun did10DoesNotInventWeight() {
        val p = run("Did 10.", weight = null).envelope.candidates.single().decodePayload() as SetCompletedCandidate
        assertEquals(10, p.reps)
        assertNull(p.weight)
    }
}
