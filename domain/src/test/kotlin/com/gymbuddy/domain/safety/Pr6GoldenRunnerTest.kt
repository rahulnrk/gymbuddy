package com.gymbuddy.domain.safety

import com.gymbuddy.domain.SafetyAction
import com.gymbuddy.domain.SafetySeverity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Pr6GoldenRunnerTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val classifier = DefaultSafetyClassifier()
    private val gate = SafetyFirstTurn(classifier)

    @Test
    fun safetyGoldens() {
        val dir = File("").absoluteFile.let { cwd ->
            listOf(File(cwd, "tests/golden"), File(cwd, "../tests/golden"), File(cwd, "../../tests/golden"))
                .first { it.isDirectory }
        }
        val files = dir.listFiles { f -> f.name.startsWith("11-safety.") && f.extension == "json" }!!.sortedBy { it.name }
        assertTrue(files.size >= 2)
        files.forEach { file ->
            val data = json.parseToJsonElement(file.readText()).jsonObject
            val utterance = data["utterance"]?.jsonPrimitive?.contentOrNull
                ?: data["input"]?.jsonObject?.get("transcript")?.jsonPrimitive?.contentOrNull
                ?: error("${file.name} missing utterance")
            val expect = data["expect"]!!.jsonObject
            val turn = gate.process(
                utterance,
                extract = { "extracted" },
                llmCoachingSuggestion = if (expect["llmCannotOverride"]?.jsonPrimitive?.booleanOrNull == true) {
                    "Let's continue the workout."
                } else null,
            )
            val r = turn.decision
            expect["severity"]?.jsonPrimitive?.contentOrNull?.let { s ->
                assertEquals(s, r.severity?.name?.lowercase(), file.name)
            }
            expect["category"]?.jsonPrimitive?.contentOrNull?.let { cat ->
                assertEquals(cat, r.category, file.name)
            }
            expect["action"]?.jsonPrimitive?.contentOrNull?.let { act ->
                val want = when (act) {
                    "stop_recommend" -> SafetyAction.STOP_RECOMMEND
                    "pause" -> SafetyAction.PAUSE
                    "advise" -> SafetyAction.ADVISE
                    else -> error(act)
                }
                assertEquals(want, r.action, file.name)
            }
            expect["pause"]?.jsonPrimitive?.booleanOrNull?.let { assertEquals(it, r.pause, file.name) }
            expect["safetyConcern"]?.jsonPrimitive?.booleanOrNull?.let { want ->
                assertEquals(want, r.fired, file.name)
            }
            expect["allowSetCompleted"]?.jsonPrimitive?.booleanOrNull?.let {
                assertEquals(it, r.allowSetCompleted, file.name)
            }
            if (expect["llmCannotOverride"]?.jsonPrimitive?.booleanOrNull == true) {
                assertTrue(turn.speech.suppressNormalCoaching, file.name)
                assertFalse("continue" in turn.speech.text.orEmpty().lowercase(), file.name)
            }
            if (expect["route"]?.jsonPrimitive?.contentOrNull == "substitution") {
                assertFalse(r.fired, file.name)
            }
            if (expect["runsOn"]?.jsonPrimitive?.contentOrNull == "raw_transcript_before_extract") {
                assertTrue(turn.classifiedBeforeExtract, file.name)
            }
            if (file.name.contains("no-diagnosis")) {
                val t = turn.speech.text.orEmpty().lowercase()
                assertFalse("rotator" in t)
                assertFalse("heart attack" in t)
                assertFalse("you are having" in t)
            }
        }
    }
}
