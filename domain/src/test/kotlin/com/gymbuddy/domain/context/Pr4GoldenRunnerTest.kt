package com.gymbuddy.domain.context

import com.gymbuddy.domain.EpistemicStance
import com.gymbuddy.domain.WeightUnit
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.engine.PlannedExercise
import com.gymbuddy.domain.engine.WorkoutPlan
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Pr4GoldenRunnerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun contextGoldens() {
        val dir = File("").absoluteFile.let { cwd ->
            listOf(File(cwd, "tests/golden"), File(cwd, "../tests/golden"), File(cwd, "../../tests/golden"))
                .first { it.isDirectory }
        }
        val files = dir.listFiles { f -> f.name.startsWith("08-context.") && f.extension == "json" }!!.sortedBy { it.name }
        assertTrue(files.isNotEmpty())
        files.forEach { file ->
            val data = json.parseToJsonElement(file.readText()).jsonObject
            val ctx = assembleGiven(data["given"]?.jsonObject ?: JsonObject(emptyMap()))
            assertExpect(file.name, ctx, data["expect"]?.jsonObject ?: JsonObject(emptyMap()))
        }
    }

    private fun assembleGiven(given: JsonObject): FourContexts {
        val histNode = given["historical"]?.jsonObject
        val sessionsNode = given["previousSessionsForExercise"] as? JsonArray
            ?: histNode?.get("previousSessionsForExercise") as? JsonArray
        val sessions = sessionsNode?.map { el ->
            val o = el.jsonObject
            PreviousSessionSnapshot(
                occurredOn = o["occurredOn"]?.jsonPrimitive?.content ?: "",
                sets = (o["sets"] as? JsonArray)?.map { s ->
                    val so = s.jsonObject
                    CompletedSetSnapshot(
                        setId = so["setId"]!!.jsonPrimitive.content,
                        setNumber = so["setNumber"]!!.jsonPrimitive.int,
                        weightKg = so["weightKg"]?.jsonPrimitive?.doubleOrNull,
                        reps = so["reps"]?.jsonPrimitive?.intOrNull,
                    )
                } ?: emptyList(),
                stance = EpistemicStance.KNOWN,
            )
        } ?: emptyList()
        val uncertain = (given["uncertainFacts"] as? JsonArray)?.map { el ->
            val o = el.jsonObject
            EpistemicFact(
                id = o["id"]!!.jsonPrimitive.content,
                kind = o["kind"]?.jsonPrimitive?.content ?: "aggregate",
                stance = EpistemicStance.UNCERTAIN,
                value = o["value"],
                sourceRowIds = emptyList(),
                speechAllowed = false,
            )
        } ?: emptyList()
        val user = "11111111-1111-4111-8111-111111111111"
        val session = "22222222-2222-4222-8222-222222222222"
        val workout = "33333333-3333-4333-8333-333333333333"
        val bench = "44444444-4444-4444-8444-444444444444"
        val we = "55555555-5555-4555-8555-555555555555"
        val weight = given["currentWeightKg"]?.jsonPrimitive?.doubleOrNull
        val pending = given["pendingClarification"]?.jsonObject
        return FourContextAssembler.assemble(
            AssemblyRequest(
                snapshot = WorkoutSessionSnapshot(
                    phase = WorkoutSessionState.EXERCISE_ACTIVE,
                    userId = user,
                    sessionId = session,
                    workoutId = workout,
                    plan = WorkoutPlan(workout, listOf(PlannedExercise(we, bench, 4))),
                    workingWeightKg = weight,
                ),
                history = HistoricalStore(
                    previousSessionsForExercise = sessions,
                    lastWorkoutSummary = histNode?.get("lastWorkoutSummary")?.jsonObject?.let {
                        LastWorkoutSummary(
                            date = it["date"]!!.jsonPrimitive.content,
                            name = it["name"]!!.jsonPrimitive.content,
                            volumeKg = it["volumeKg"]!!.jsonPrimitive.doubleOrNull ?: 0.0,
                        )
                    },
                    uncertainFacts = uncertain,
                ),
                profile = UserProfileStore(
                    userId = user,
                    units = WeightUnit.KG,
                    primaryLanguage = given["primaryLanguage"]?.jsonPrimitive?.contentOrNull ?: "",
                ),
                conversation = ConversationalStore(
                    pendingClarification = pending?.let {
                        PendingClarification(
                            question = it["question"]!!.jsonPrimitive.content,
                            options = (it["options"] as? JsonArray)?.map { o -> o.jsonPrimitive.content } ?: emptyList(),
                        )
                    },
                    lastUserUtterance = given["lastUserUtterance"]?.jsonObject?.let {
                        UtteranceSnapshot(
                            it["text"]!!.jsonPrimitive.content,
                            it["lang"]?.jsonPrimitive?.content ?: "en",
                            it["at"]?.jsonPrimitive?.content ?: "",
                        )
                    },
                ),
                exerciseNames = mapOf(bench to "Bench Press"),
                workoutName = "Chest / Triceps",
            ),
        )
    }

    private fun assertExpect(name: String, ctx: FourContexts, expect: JsonObject) {
        val histBlob = ctx.historical.toString().lowercase()
        expect["forbiddenSpeech"]?.jsonArray?.forEach { token ->
            val t = token.jsonPrimitive.content.lowercase()
            assertFalse(t in histBlob, "$name historical leaked $t")
        }
        expect["stance"]?.jsonPrimitive?.content?.let { stance ->
            val want = EpistemicStance.entries.first { it.name.equals(stance, ignoreCase = true) }
            if (want == EpistemicStance.NONE) {
                assertTrue(ctx.historical.previousSessionsForExercise.isEmpty(), name)
                assertTrue(ctx.historical.aggregates.any { it.stance == EpistemicStance.NONE }, name)
            }
            if (want == EpistemicStance.KNOWN) {
                assertTrue(ctx.historical.previousSessionsForExercise.isNotEmpty(), name)
                assertTrue(ctx.facts.any { it.stance == EpistemicStance.KNOWN && it.id.startsWith("fact:hist:") }, name)
            }
            if (want == EpistemicStance.ESTIMATED) {
                assertTrue(ctx.historical.aggregates.any { it.stance == EpistemicStance.ESTIMATED }, name)
            }
            if (want == EpistemicStance.UNCERTAIN) {
                assertTrue(ctx.historical.aggregates.any { it.stance == EpistemicStance.UNCERTAIN }, name)
                assertFalse(ctx.historical.aggregates.single { it.stance == EpistemicStance.UNCERTAIN }.speechAllowed, name)
            }
        }
        expect["requiredSpeechClass"]?.jsonPrimitive?.content?.let { cls ->
            if (cls == "i_dont_have_a_previous_number") {
                assertTrue(ctx.historical.aggregates.any { it.stance == EpistemicStance.NONE && !it.speechAllowed }, name)
            }
        }
        expect["historicalReps"]?.jsonArray?.let { reps ->
            val got = ctx.historical.previousSessionsForExercise.flatMap { it.sets }.map { it.reps }
            assertEquals(reps.map { it.jsonPrimitive.int }, got, name)
        }
        expect["setNumbers"]?.jsonArray?.let { nums ->
            val got = ctx.historical.previousSessionsForExercise.flatMap { it.sets }.map { it.setNumber }
            assertEquals(nums.map { it.jsonPrimitive.int }, got, name)
        }
        expect["currentWeightKg"]?.jsonPrimitive?.doubleOrNull?.let { w ->
            assertEquals(w, ctx.workout.currentWeightKg, name)
        }
        expect["citable"]?.jsonPrimitive?.booleanOrNull?.let { citable ->
            if (citable) {
                assertTrue(ctx.historical.previousSessionsForExercise.any { it.stance == EpistemicStance.KNOWN }, name)
            }
        }
        expect["pendingQuestion"]?.jsonPrimitive?.content?.let { q ->
            assertEquals(q, ctx.conversational.pendingClarification?.question, name)
        }
        expect["meanWeightKg"]?.jsonPrimitive?.doubleOrNull?.let { mean ->
            val est = ctx.historical.aggregates.single { it.stance == EpistemicStance.ESTIMATED }
            assertEquals(mean, est.value?.jsonPrimitive?.doubleOrNull, name)
            assertTrue(est.sourceRowIds.size >= 2, name)
        }
    }
}
