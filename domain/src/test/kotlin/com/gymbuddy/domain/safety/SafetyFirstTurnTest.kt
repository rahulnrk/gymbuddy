package com.gymbuddy.domain.safety

import com.gymbuddy.domain.SafetySeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafetyFirstTurnTest {
    private val gate = SafetyFirstTurn()

    @Test
    fun safetyRunsBeforeExtraction() {
        val order = mutableListOf<String>()
        gate.process(
            "my chest hurts",
            extract = {
                order += "extract"
                "candidates"
            },
        )
        // classify happens before extract; extract still runs so set facts can be captured
        assertEquals(listOf("extract"), order)
        val log = mutableListOf<String>()
        val clf = object : SafetyClassifier {
            override fun classify(rawTranscript: String): SafetyResult {
                log += "safety"
                return DefaultSafetyClassifier().classify(rawTranscript)
            }
        }
        SafetyFirstTurn(clf).process("got 10", extract = {
            log += "extract"
            1
        })
        assertEquals(listOf("safety", "extract"), log)
    }

    @Test
    fun llmContinueCannotOverrideCritical() {
        val turn = gate.process(
            "I have chest pain.",
            extract = { "SET_COMPLETED" },
            llmCoachingSuggestion = "Let's continue the workout.",
        )
        assertEquals(SafetySeverity.CRITICAL, turn.decision.severity)
        assertTrue(turn.speech.suppressNormalCoaching)
        assertEquals(SafetyResponsePolicy.CRITICAL_TEXT, turn.speech.text)
        assertFalse("continue" in turn.speech.text.orEmpty().lowercase())
        assertTrue(turn.classifiedBeforeExtract)
    }

    @Test
    fun highSuppressesCoaching() {
        val turn = gate.process(
            "sharp pain in my shoulder",
            extract = { emptyList<String>() },
            llmCoachingSuggestion = "Let's continue the workout.",
        )
        assertEquals(SafetySeverity.HIGH, turn.decision.severity)
        assertTrue(turn.speech.suppressNormalCoaching)
        assertEquals(SafetyResponsePolicy.HIGH_TEXT, turn.speech.text)
    }

    @Test
    fun mediumDoesNotStopWorkout() {
        val turn = gate.process(
            "I got 10 but my shoulder feels weird",
            extract = { listOf("SetCompleted") },
            llmCoachingSuggestion = "Nice work.",
        )
        assertEquals(SafetySeverity.MEDIUM, turn.decision.severity)
        assertTrue(turn.decision.allowSetCompleted)
        assertFalse(turn.decision.pause)
        assertFalse(turn.speech.suppressNormalCoaching)
        assertEquals(listOf("SetCompleted"), turn.extracted)
    }

    @Test
    fun nonSafetyAllowsLlmCoaching() {
        val turn = gate.process(
            "Bro this is heavy.",
            extract = { "feedback" },
            llmCoachingSuggestion = "Want to drop a little?",
        )
        assertFalse(turn.decision.fired)
        assertEquals("Want to drop a little?", turn.speech.text)
        assertFalse(turn.speech.suppressNormalCoaching)
    }
}
