package com.gymbuddy.domain

import com.gymbuddy.domain.context.CompletedSetSnapshot
import com.gymbuddy.domain.context.ConversationalStateContext
import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.context.HistoricalStateContext
import com.gymbuddy.domain.context.SttFinalRecord
import com.gymbuddy.domain.context.UserStateContext
import com.gymbuddy.domain.context.WorkoutStateContext
import com.gymbuddy.domain.events.CandidateEvent
import com.gymbuddy.domain.events.CandidateEventEnvelope
import com.gymbuddy.domain.validation.DefaultCandidateValidator
import com.gymbuddy.domain.validation.ValidatedFact
import com.gymbuddy.domain.validation.ValidationDecision
import com.gymbuddy.domain.validation.VoiceCommitRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CandidateValidatorRulesTest {
    private val v = DefaultCandidateValidator()

    private fun route(
        headset: Boolean = true,
        talkers: Int = 1,
        duration: Double = 400.0,
        overlap: Boolean = false,
        hash: String? = null,
        t: Long = 1_000L,
    ) = VoiceCommitRoute(
        audioRoute = if (headset) AudioRoute.HEADSET else AudioRoute.SPEAKERPHONE,
        talkerCount = talkers,
        sttDurationMs = duration,
        overlappingVad = overlap,
        sttFinalHash = hash,
        recordedAtEpochMs = t,
    )

    private fun snap(
        weight: Double? = 60.0,
        sets: List<CompletedSetSnapshot> = emptyList(),
        finals: List<SttFinalRecord> = emptyList(),
    ) = FourContexts(
        workout = WorkoutStateContext(
            sessionId = "22222222-2222-4222-8222-222222222222",
            workoutId = "33333333-3333-4333-8333-333333333333",
            workoutName = "t",
            sessionState = WorkoutSessionState.RESTING,
            voiceState = VoiceTurnState.SILENT,
            currentExerciseId = "44444444-4444-4444-8444-444444444444",
            completedSets = sets,
            remainingSetsPlanned = 3,
            currentWeightKg = weight,
            substitutions = emptyList(),
            startedAt = "2026-08-20T16:00:00Z",
        ),
        historical = HistoricalStateContext(),
        conversational = ConversationalStateContext(languageDetected = "en", recentSttFinals = finals),
        user = UserStateContext(
            userId = "11111111-1111-4111-8111-111111111111",
            goals = emptyList(),
            experience = "intermediate",
            equipment = emptyList(),
            units = WeightUnit.KG,
            primaryLanguage = "en",
        ),
    )

    private fun parse(raw: String) = CandidateEventEnvelope.parse(raw).candidates.single()

    private fun setCompletedJson(reps: Int? = 10, weight: Double? = null) = """
        {"schemaVersion":1,"candidates":[{
          "candidateId":"77777777-7777-4777-8777-777777777777",
          "type":"SET_COMPLETED","overallConfidence":0.92,
          "fieldConfidence":{"reps":0.9},
          "loggingMode":"live",
          "payload":${if (weight == null) """{"reps":$reps}""" else """{"reps":$reps,"weight":$weight,"weightUnit":"kg"}"""}
        }]}
    """.trimIndent()

    @Test
    fun explicitWeightAndReps() {
        val d = v.validate(parse(setCompletedJson(10, 40.0)), snap(weight = null), route())
        val f = (d as ValidationDecision.Accept).facts.filterIsInstance<ValidatedFact.SetCompleted>().single()
        assertEquals(10, f.reps)
        assertEquals(40.0, f.weightKg)
        assertFalse(f.inheritWeight)
        assertFalse(f::class.java.declaredFields.any { it.name == "setNumber" })
    }

    @Test
    fun inheritedWeight() {
        val d = v.validate(parse(setCompletedJson(10)), snap(60.0), route())
        val f = (d as ValidationDecision.Accept).facts.filterIsInstance<ValidatedFact.SetCompleted>().single()
        assertEquals(60.0, f.weightKg)
        assertTrue(f.inheritWeight)
    }

    @Test
    fun missingWorkingWeightClarifies() {
        val d = v.validate(parse(setCompletedJson(10)), snap(null), route())
        assertTrue(d is ValidationDecision.Clarify)
    }

    @Test
    fun invalidRepsTypeNeverCommits() {
        val raw = """
            {"schemaVersion":1,"candidates":[{
              "candidateId":"77777777-7777-4777-8777-777777777777",
              "type":"SET_COMPLETED","overallConfidence":0.9,
              "fieldConfidence":{"reps":0.9},"payload":{"reps":"ten"}
            }]}
        """.trimIndent()
        val env = CandidateEventEnvelope.parse(raw)
        val d = v.validate(env.candidates.single(), snap(), route())
        assertTrue(d is ValidationDecision.Drop)
        assertTrue((d as ValidationDecision.Drop).reason.contains("payload"))
    }

    @Test
    fun duplicateSttHashWithin10sDropped() {
        val r = route(hash = "abc", t = 5_000)
        val s = snap(finals = listOf(SttFinalRecord("abc", 0L)))
        val d = v.validate(parse(setCompletedJson(10)), s, r)
        assertTrue(d is ValidationDecision.Drop)
    }

    @Test
    fun duplicateOutsideWindowAllowed() {
        val r = route(hash = "abc", t = 11_000)
        val s = snap(finals = listOf(SttFinalRecord("abc", 0L)))
        val d = v.validate(parse(setCompletedJson(10)), s, r)
        assertTrue(d is ValidationDecision.Accept)
    }

    @Test
    fun nonHeadsetNotAutoCommit() {
        val d = v.validate(parse(setCompletedJson(10)), snap(), route(headset = false))
        assertTrue(d is ValidationDecision.Drop)
    }

    @Test
    fun multiTalkerNotAutoCommit() {
        val d = v.validate(parse(setCompletedJson(10)), snap(), route(talkers = 2))
        assertTrue(d is ValidationDecision.Drop)
    }

    @Test
    fun durationBelow250msNotAutoCommit() {
        val d = v.validate(parse(setCompletedJson(10)), snap(), route(duration = 249.0))
        assertTrue(d is ValidationDecision.Drop)
    }

    @Test
    fun overlappingVadNotAutoCommit() {
        val d = v.validate(parse(setCompletedJson(10)), snap(), route(overlap = true))
        assertTrue(d is ValidationDecision.Drop)
    }

    @Test
    fun skipIsCommandNotASet() {
        val raw = """
            {"schemaVersion":1,"candidates":[{
              "candidateId":"77777777-7777-4777-8777-777777777777",
              "type":"COMMAND","overallConfidence":0.85,
              "fieldConfidence":{"kind":0.85},"payload":{"kind":"skip"}
            }]}
        """.trimIndent()
        val accept = v.validate(parse(raw), snap(), route()) as ValidationDecision.Accept
        val f = accept.facts.single() as ValidatedFact.Command
        assertEquals(CommandKind.SKIP, f.kind)
        assertTrue(accept.facts.none { it is ValidatedFact.SetCompleted })
    }

    @Test
    fun historicalQuestionIsNotASet() {
        val raw = """
            {"schemaVersion":1,"candidates":[{
              "candidateId":"77777777-7777-4777-8777-777777777777",
              "type":"USER_QUESTION","overallConfidence":0.9,
              "fieldConfidence":{"questionOriginal":0.9},
              "payload":{"questionOriginal":"How much did I bench last week?","topic":"history"}
            }]}
        """.trimIndent()
        val d = v.validate(parse(raw), snap(), route())
        assertTrue((d as ValidationDecision.Accept).facts.single() is ValidatedFact.UserQuestion)
    }
}
