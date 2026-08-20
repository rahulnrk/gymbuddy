package com.gymbuddy.domain.extract

import com.gymbuddy.domain.CandidateType
import com.gymbuddy.domain.VoiceTurnState
import com.gymbuddy.domain.WeightUnit
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.context.ConversationalStateContext
import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.context.HistoricalStateContext
import com.gymbuddy.domain.context.UserStateContext
import com.gymbuddy.domain.context.WorkoutStateContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Pr7GoldenRunnerTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val extractor = NaturalLanguageExtractor()

    @Test
    fun extract04And05Utterances() {
        val dir = File("").absoluteFile.let { cwd ->
            listOf(File(cwd, "tests/golden"), File(cwd, "../tests/golden"), File(cwd, "../../tests/golden"))
                .first { it.isDirectory }
        }
        val files = dir.listFiles { f ->
            f.extension == "json" && (f.name.startsWith("04-nl-logging.") || f.name.startsWith("05-extract."))
        }!!.sortedBy { it.name }
        assertTrue(files.isNotEmpty())
        files.forEach { file ->
            val data = json.parseToJsonElement(file.readText()).jsonObject
            val utterance = data["utterance"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val given = data["given"]?.jsonObject
            val expect = data["expect"]?.jsonObject ?: return@forEach
            if (expect["schemaValid"]?.jsonPrimitive?.booleanOrNull == false) return@forEach
            val weight = given?.get("currentWeightKg")?.jsonPrimitive?.doubleOrNull
            val result = extractor.extract(
                ExtractionRequest(
                    transcript = utterance,
                    contexts = contexts(weight),
                ),
            )
            val types = result.envelope.candidates.map { it.type.name }
            when {
                expect["factType"]?.jsonPrimitive?.contentOrNull == "SetCompleted" ->
                    assertTrue(CandidateType.SET_COMPLETED.name in types, file.name)
                expect["factType"]?.jsonPrimitive?.contentOrNull == "WeightChanged" ->
                    assertTrue(CandidateType.WEIGHT_CHANGED.name in types, file.name)
                expect["factType"]?.jsonPrimitive?.contentOrNull == "UserQuestion" ->
                    assertTrue(CandidateType.USER_QUESTION.name in types, file.name)
                expect["notFactType"]?.jsonPrimitive?.contentOrNull == "SetCompleted" ->
                    assertTrue(CandidateType.SET_COMPLETED.name !in types, file.name)
            }
            if (expect["notInventHistory"]?.jsonPrimitive?.contentOrNull == "true") {
                assertTrue(CandidateType.SET_COMPLETED.name !in types, file.name)
            }
            assertTrue(result.classifiedBeforeExtract, file.name)
            result.envelope.candidates.forEach { c ->
                assertTrue("setNumber" !in c.payload.keys, file.name)
            }
        }
    }

    private fun contexts(weight: Double?) = FourContexts(
        workout = WorkoutStateContext(
            sessionId = "22222222-2222-4222-8222-222222222222",
            workoutId = "33333333-3333-4333-8333-333333333333",
            workoutName = "t",
            sessionState = WorkoutSessionState.RESTING,
            voiceState = VoiceTurnState.SILENT,
            currentExerciseId = "44444444-4444-4444-8444-444444444444",
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
}
