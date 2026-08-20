package com.gymbuddy.domain

import com.gymbuddy.domain.context.CompletedSetSnapshot
import com.gymbuddy.domain.context.ConversationalStateContext
import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.context.HistoricalStateContext
import com.gymbuddy.domain.context.UserStateContext
import com.gymbuddy.domain.context.WorkoutStateContext
import com.gymbuddy.domain.events.CandidateEventEnvelope
import com.gymbuddy.domain.validation.DefaultCandidateValidator
import com.gymbuddy.domain.validation.HintOrdinal
import com.gymbuddy.domain.validation.SetNumberHint
import com.gymbuddy.domain.validation.ValidatedFact
import com.gymbuddy.domain.validation.ValidationDecision
import com.gymbuddy.domain.validation.VoiceCommitRoute
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class Pr1GoldenRunnerTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val validator = DefaultCandidateValidator()

    private fun goldenDir(): File {
        val cwd = File("").absoluteFile
        return listOf(
            File(cwd, "tests/golden"),
            File(cwd, "../tests/golden"),
            File(cwd, "../../tests/golden"),
        ).firstOrNull { it.isDirectory } ?: error("tests/golden not found from $cwd")
    }

    @Test
    fun everyPr1GoldenPasses() {
        var n = 0
        goldenDir().listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }.forEach { file ->
            val root = json.parseToJsonElement(file.readText()).jsonObject
            val pr = root["pr"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            if ("1" !in pr) return@forEach
            n++
            runGolden(file.name, root)
        }
        assertTrue(n >= 17, "ran $n PR 1 goldens")
    }

    private fun runGolden(name: String, root: JsonObject) {
        val expect = root["expect"]!!.jsonObject
        val given = root["given"]!!.jsonObject
        val inputEl = root["input"]!!.jsonObject
        val schemaValid = expect["schemaValid"]?.jsonPrimitive?.booleanOrNull ?: true
        if (!schemaValid) {
            val raw = inputEl.toString()
            assertTrue(
                runCatching { CandidateEventEnvelope.parse(raw) }.isFailure,
                "$name expected schema/parse failure",
            )
            return
        }
        val envelope = CandidateEventEnvelope.parse(inputEl.toString())
        val snapshot = snapshotFrom(given)
        val route = routeFrom(given)
        val decision = if (envelope.candidates.size > 1) {
            validator.validateTurn(envelope.candidates, snapshot, route)
        } else {
            validator.validate(envelope.candidates.single(), snapshot, route)
        }
        assertDecision(name, expect, decision)
    }

    private fun assertDecision(name: String, expect: JsonObject, decision: ValidationDecision) {
        val want = expect["decision"]?.jsonPrimitive?.content
        when (want) {
            "accept" -> assertTrue(decision is ValidationDecision.Accept, "$name want accept got $decision")
            "clarify" -> assertTrue(decision is ValidationDecision.Clarify, "$name want clarify got $decision")
            "drop", "drop_or_no_auto_commit" ->
                assertTrue(decision is ValidationDecision.Drop || decision is ValidationDecision.Reject, "$name want drop got $decision")
            "reject" -> assertTrue(decision is ValidationDecision.Reject, "$name got $decision")
        }
        if (expect["commit"]?.jsonPrimitive?.booleanOrNull == false) {
            val facts = (decision as? ValidationDecision.Accept)?.facts.orEmpty()
            assertTrue(facts.none { it is ValidatedFact.SetCompleted }, "$name must not commit a set")
        }
        expect["promptContains"]?.jsonPrimitive?.content?.let { needle ->
            val prompt = (decision as? ValidationDecision.Clarify)?.prompt ?: fail("$name not clarify")
            assertTrue(prompt.lowercase().contains(needle.lowercase()), "$name prompt=$prompt")
        }
        val facts = (decision as? ValidationDecision.Accept)?.facts.orEmpty()
        expect["acceptFactCount"]?.jsonPrimitive?.intOrNull?.let {
            assertEquals(it, facts.size, "$name fact count")
        }
        if (expect["newSetRow"]?.jsonPrimitive?.booleanOrNull == false &&
            expect["factType"]?.jsonPrimitive?.content != "SetCompleted"
        ) {
            assertTrue(facts.none { it is ValidatedFact.SetCompleted }, "$name newSetRow")
        }
        when (expect["notFactType"]?.jsonPrimitive?.content) {
            "SetCompleted" -> assertTrue(facts.none { it is ValidatedFact.SetCompleted }, name)
        }
        when (expect["factType"]?.jsonPrimitive?.content) {
            "SetCompleted" -> {
                val sets = facts.filterIsInstance<ValidatedFact.SetCompleted>()
                assertTrue(sets.isNotEmpty(), name)
                expect["reps"]?.let { repsEl ->
                    if (repsEl is kotlinx.serialization.json.JsonArray) {
                        assertEquals(repsEl.map { it.jsonPrimitive.int }, sets.map { it.reps })
                    } else {
                        assertEquals(repsEl.jsonPrimitive.int, sets.single().reps)
                    }
                }
                expect["weightKg"]?.jsonPrimitive?.doubleOrNull?.let { assertEquals(it, sets.single().weightKg) }
                expect["weightsKg"]?.jsonArray?.let { arr ->
                    assertEquals(arr.map { it.jsonPrimitive.double }, sets.map { it.weightKg })
                }
                expect["inheritWeight"]?.let { inh ->
                    if (inh is kotlinx.serialization.json.JsonArray) {
                        assertEquals(inh.map { it.jsonPrimitive.boolean }, sets.map { it.inheritWeight })
                    } else {
                        assertEquals(inh.jsonPrimitive.boolean, sets.single().inheritWeight)
                    }
                }
                expect["loggingMode"]?.jsonPrimitive?.content?.let {
                    assertTrue(sets.all { s -> s.loggingMode.name.equals(it, ignoreCase = true) }, name)
                }
                expect["setNumberHints"]?.jsonArray?.let { hints ->
                    val got = sets.map { hintLabel(it.setNumberHint) }
                    assertEquals(hints.map { it.jsonPrimitive.content }, got)
                }
                if (expect["batchIdentity"]?.jsonPrimitive?.content == "shared_or_same_turn") {
                    val ids = sets.map { it.batchId }.distinct()
                    assertEquals(1, ids.size, name)
                    assertNotNull(ids.single())
                }
                sets.forEach { s ->
                    val fields = s::class.java.declaredFields.map { it.name }
                    assertFalse("setNumber" in fields, "$name official setNumber leaked")
                }
            }
            "SetCorrected" -> {
                val c = facts.filterIsInstance<ValidatedFact.SetCorrected>().single()
                expect["setId"]?.jsonPrimitive?.content?.let { assertEquals(it, c.setId.value) }
                expect["reps"]?.jsonPrimitive?.intOrNull?.let { assertEquals(it, c.fields.reps) }
                expect["previous"]?.jsonObject?.let { prev ->
                    assertEquals(prev["setId"]!!.jsonPrimitive.content, c.previous.setId.value)
                    prev["reps"]?.jsonPrimitive?.intOrNull?.let { assertEquals(it, c.previous.reps) }
                    prev["weightKg"]?.jsonPrimitive?.doubleOrNull?.let { assertEquals(it, c.previous.weightKg) }
                }
            }
            "SetDeleted" -> {
                val d = facts.filterIsInstance<ValidatedFact.SetDeleted>().single()
                expect["setId"]?.jsonPrimitive?.content?.let { assertEquals(it, d.setId.value) }
                expect["previous"]?.jsonObject?.let { prev ->
                    assertEquals(prev["setId"]!!.jsonPrimitive.content, d.previous.setId.value)
                }
            }
            "WeightChanged" -> {
                val w = facts.filterIsInstance<ValidatedFact.WeightChanged>().single()
                expect["weightKg"]?.jsonPrimitive?.doubleOrNull?.let { assertEquals(it, w.weightKg) }
            }
            "UserQuestion" -> assertTrue(facts.any { it is ValidatedFact.UserQuestion }, name)
        }
        expect["autoCommit"]?.jsonPrimitive?.booleanOrNull?.let { auto ->
            if (!auto) assertTrue(facts.none { it is ValidatedFact.SetCompleted }, name)
        }
        expect["replacementSetCompleted"]?.jsonPrimitive?.booleanOrNull?.let { repl ->
            if (!repl) assertTrue(facts.none { it is ValidatedFact.SetCompleted }, name)
        }
        expect["commitSet"]?.jsonPrimitive?.booleanOrNull?.let { cs ->
            if (!cs) assertTrue(facts.none { it is ValidatedFact.SetCompleted }, name)
        }
    }

    private fun hintLabel(h: SetNumberHint?): String? = when (h) {
        is SetNumberHint.Ordinal -> h.value.name.lowercase()
        is SetNumberHint.Index -> h.value.toString()
        null -> null
    }

    private fun JsonObject.optionalDouble(key: String): Double? {
        val el = this[key] ?: return null
        if (el is JsonNull) return null
        return el.jsonPrimitive.doubleOrNull
    }

    private fun routeFrom(given: JsonObject): VoiceCommitRoute {
        val route = given["audioRoute"]?.jsonPrimitive?.content ?: "headset"
        return VoiceCommitRoute(
            audioRoute = if (route == "speakerphone") AudioRoute.SPEAKERPHONE else AudioRoute.HEADSET,
            talkerCount = given["talkerCount"]?.jsonPrimitive?.intOrNull ?: given["talkers"]?.jsonPrimitive?.intOrNull ?: 1,
            sttDurationMs = given["sttDurationMs"]?.jsonPrimitive?.doubleOrNull ?: 400.0,
            overlappingVad = given["overlappingVad"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }

    private fun snapshotFrom(given: JsonObject): FourContexts {
        val sets = given["completedSets"]?.jsonArray?.map { el ->
            val o = el.jsonObject
            CompletedSetSnapshot(
                setId = o["setId"]!!.jsonPrimitive.content,
                setNumber = o["setNumber"]!!.jsonPrimitive.int,
                weightKg = o["weightKg"]?.jsonPrimitive?.doubleOrNull,
                reps = o["reps"]?.jsonPrimitive?.intOrNull,
                occurredAt = o["occurredAt"]?.jsonPrimitive?.contentOrNull,
                exerciseId = o["exerciseId"]?.jsonPrimitive?.contentOrNull,
            )
        } ?: emptyList()
        val last = given["lastSet"]?.jsonObject
        val completed = if (sets.isNotEmpty()) sets else last?.let {
            listOf(
                CompletedSetSnapshot(
                    setId = it["setId"]!!.jsonPrimitive.content,
                    setNumber = it["setNumber"]?.jsonPrimitive?.intOrNull ?: 1,
                    weightKg = it["weightKg"]?.jsonPrimitive?.doubleOrNull,
                    reps = it["reps"]?.jsonPrimitive?.intOrNull,
                ),
            )
        } ?: emptyList()
        val prev = given["previousExerciseLastSet"]?.jsonObject?.let { o ->
            CompletedSetSnapshot(
                setId = o["setId"]!!.jsonPrimitive.content,
                setNumber = o["setNumber"]!!.jsonPrimitive.int,
                weightKg = o["weightKg"]?.jsonPrimitive?.doubleOrNull,
                reps = o["reps"]?.jsonPrimitive?.intOrNull,
                occurredAt = o["occurredAt"]?.jsonPrimitive?.contentOrNull,
                exerciseId = o["exerciseId"]?.jsonPrimitive?.contentOrNull,
            )
        }
        val state = given["sessionState"]?.jsonPrimitive?.content ?: "RESTING"
        val units = given["units"]?.jsonPrimitive?.content ?: "kg"
        val hist = given["historical"]?.jsonObject
        return FourContexts(
            workout = WorkoutStateContext(
                sessionId = "22222222-2222-4222-8222-222222222222",
                workoutId = "33333333-3333-4333-8333-333333333333",
                workoutName = "fixture",
                sessionState = WorkoutSessionState.valueOf(state),
                voiceState = VoiceTurnState.SILENT,
                currentExerciseId = given["currentExerciseId"]?.jsonPrimitive?.contentOrNull,
                currentExerciseName = given["currentExerciseName"]?.jsonPrimitive?.contentOrNull,
                completedSets = completed,
                remainingSetsPlanned = 3,
                currentWeightKg = given.optionalDouble("currentWeightKg"),
                substitutions = emptyList(),
                startedAt = "2026-08-20T16:00:00Z",
                previousExerciseLastSet = prev,
                currentExerciseStartedAt = given["currentExerciseStartedAt"]?.jsonPrimitive?.contentOrNull,
            ),
            historical = HistoricalStateContext(
                previousSessionsForExercise = if (hist?.get("previousSessionsForExercise")?.jsonArray?.isEmpty() == true) {
                    emptyList()
                } else emptyList(),
            ),
            conversational = ConversationalStateContext(
                currentTopic = given["conversationalTopic"]?.jsonPrimitive?.contentOrNull,
                languageDetected = given["primaryLanguage"]?.jsonPrimitive?.content ?: "en",
            ),
            user = UserStateContext(
                userId = "11111111-1111-4111-8111-111111111111",
                goals = emptyList(),
                experience = "intermediate",
                equipment = emptyList(),
                units = if (units == "lb") WeightUnit.LB else WeightUnit.KG,
                primaryLanguage = given["primaryLanguage"]?.jsonPrimitive?.content ?: "en",
            ),
        )
    }
}
