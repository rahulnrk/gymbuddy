package com.gymbuddy.domain.loop

import com.gymbuddy.domain.AudioRoute
import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.LoggingMode
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.SetId
import com.gymbuddy.domain.WorkoutSessionState
import com.gymbuddy.domain.context.AssemblyRequest
import com.gymbuddy.domain.context.FourContextAssembler
import com.gymbuddy.domain.engine.InMemoryEventLog
import com.gymbuddy.domain.engine.PlannedExercise
import com.gymbuddy.domain.engine.ProjectedSet
import com.gymbuddy.domain.engine.WorkoutEngine
import com.gymbuddy.domain.engine.WorkoutPlan
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
import com.gymbuddy.domain.events.WorkoutEvent
import com.gymbuddy.domain.safety.SafetyResult
import com.gymbuddy.domain.voice.SttFinalHash
import com.gymbuddy.domain.voice.fake.FakeClock
import com.gymbuddy.domain.voice.VoiceTurnEvidence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Pr9GoldenRunnerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun coachingGoldens() {
        val dir = goldenDir()
        val files = dir.listFiles { f ->
            f.extension == "json" && (f.name.startsWith("09-coaching.") || f.name.startsWith("02-loop."))
        }!!.sortedBy { it.name }
        assertTrue(files.isNotEmpty())
        files.forEach { file ->
            val data = json.parseToJsonElement(file.readText()).jsonObject
            val given = data["given"]?.jsonObject
            val expect = data["expect"]!!.jsonObject
            val utterance = data["utterance"]?.jsonPrimitive?.contentOrNull
            when {
                expect["silenceFieldRequired"]?.jsonPrimitive?.booleanOrNull == true ||
                    expect["coachingResponseRequired"]?.jsonPrimitive?.booleanOrNull == true -> {
                    val loop = harness()
                    loop.start()
                    loop.seedWorkingWeight(60.0)
                    val t = loop.handleTurn("Got 10.")
                    assertTrue(t.coachingPersisted, file.name)
                    val coaches = loop.log.sessionEvents(SessionId(loop.sessionId))
                        .filter { it.type == CommittedEventType.COACHING_RESPONSE }
                    assertTrue(coaches.any { "silence" in it.payload.keys }, file.name)
                }
                expect["duplicateSuppressed"]?.jsonPrimitive?.booleanOrNull == true -> {
                    val loop = harness()
                    loop.start()
                    loop.seedWorkingWeight(60.0)
                    val hash = SttFinalHash.of("Got 10.")
                    val ev = VoiceTurnEvidence(
                        "Got 10.", true, durationMs = 400.0, sttFinalHash = hash,
                        recordedAtMs = 1000, audioRoute = AudioRoute.HEADSET,
                    )
                    loop.handleTurn("Got 10.", ev)
                    val second = loop.handleTurn("Got 10.", ev.copy(recordedAtMs = 1500))
                    assertEquals(expect["setCount"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1, second.snapshot.liveSetsOnCurrent().size, file.name)
                    assertTrue(second.duplicateSuppressed || second.snapshot.liveSetsOnCurrent().size == 1, file.name)
                }
                expect["noPrescribedWeightWrite"]?.jsonPrimitive?.booleanOrNull == true -> {
                    val loop = harness()
                    loop.start()
                    loop.seedWorkingWeight(50.0)
                    loop.handleTurn("Got 12.")
                    (loop.clock as FakeClock).advance(11_000)
                    loop.handleTurn("Got 12.")
                    (loop.clock as FakeClock).advance(11_000)
                    val t = loop.handleTurn("Got 11.")
                    assertTrue(t.recommendation != null, file.name)
                    val events = loop.log.sessionEvents(SessionId(loop.sessionId))
                    assertFalse(events.any { "prescribed_weight_kg" in it.payload.keys || "prescribedWeightKg" in it.payload.keys }, file.name)
                }
                expect["inventedHistory"]?.jsonPrimitive?.booleanOrNull == false -> {
                    val loop = harness()
                    loop.start()
                    val t = loop.handleTurn(utterance ?: "What did I do last Thursday?")
                    assertTrue(t.speak.speak, file.name)
                    assertFalse(t.spokenText.orEmpty().contains("last Thursday you used", ignoreCase = true), file.name)
                    assertFalse(t.spokenText.orEmpty().contains("I think you used"), file.name)
                    assertTrue(
                        t.spokenText.orEmpty().contains("don't have") || t.spokenText.orEmpty().contains("Last time"),
                        file.name,
                    )
                }
                expect.containsKey("forbidden") -> {
                    val loop = harness()
                    loop.start()
                    loop.seedWorkingWeight(60.0)
                    val t = loop.handleTurn("Got 10.")
                    expect["forbidden"]!!.jsonArray.forEach { el ->
                        assertFalse(el.jsonPrimitive.content in t.spokenText.orEmpty(), file.name)
                    }
                    CoachingPhraser.banned.forEach { b ->
                        assertFalse(b in t.spokenText.orEmpty(), file.name)
                    }
                }
                given != null && given["userQuestion"]?.jsonPrimitive?.booleanOrNull == true &&
                    given["sessionState"]?.jsonPrimitive?.contentOrNull == "SET_ACTIVE" -> {
                    val loop = harness()
                    loop.start()
                    loop.seedWorkingWeight(60.0)
                    loop.beginSet()
                    val t = loop.handleTurn("How much did I do last week?")
                    expect["speakNow"]?.jsonPrimitive?.booleanOrNull?.let {
                        assertEquals(it, t.speak.speak, file.name)
                    }
                    expect["reason"]?.jsonPrimitive?.contentOrNull?.let {
                        assertEquals(it, (t.speak as SpeakDecision.Silence).reason.name.lowercase(), file.name)
                    }
                    assertTrue(SpeechActId.ANSWER_QUESTION in t.queuedSpeechActs, file.name)
                }
                given != null && given["sessionState"]?.jsonPrimitive?.contentOrNull == "SET_ACTIVE" -> {
                    val loop = harness()
                    loop.start()
                    loop.beginSet()
                    val t = loop.handleTurn("Nice.")
                    expect["speakNow"]?.jsonPrimitive?.booleanOrNull?.let {
                        assertEquals(it, t.speak.speak, file.name)
                    }
                    expect["reason"]?.jsonPrimitive?.contentOrNull?.let {
                        assertEquals(it, (t.speak as SpeakDecision.Silence).reason.name.lowercase(), file.name)
                    }
                }
                expect["actId"]?.jsonPrimitive?.contentOrNull == "useful_delta" ||
                    (given?.get("previousReps") != null && given["thisReps"] != null) -> {
                    val prevR = given?.get("previousReps")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 9
                    val thisR = given?.get("thisReps")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 10
                    val phase = given?.get("sessionState")?.jsonPrimitive?.contentOrNull
                        ?.let { WorkoutSessionState.valueOf(it) } ?: WorkoutSessionState.RESTING
                    val we = "55555555-5555-4555-8555-555555555555"
                    val ex = "44444444-4444-4444-8444-444444444444"
                    val sets = listOf(
                        ProjectedSet(SetId("a"), we, ex, 1, 60.0, prevR, LoggingMode.LIVE),
                        ProjectedSet(SetId("b"), we, ex, 2, 60.0, thisR, LoggingMode.LIVE),
                    )
                    val snap = WorkoutSessionSnapshot(
                        phase = phase,
                        plan = WorkoutPlan("w", listOf(PlannedExercise(we, ex, 4))),
                        completedSets = sets,
                        sessionId = "s",
                        userId = "u",
                        workoutId = "w",
                    )
                    val d = ShouldSpeak.shouldSpeak(
                        FourContextAssembler.assemble(AssemblyRequest(snapshot = snap)), snap,
                        listOf(
                            WorkoutEvent(
                                "x", "s", "u", "t", "t",
                                com.gymbuddy.domain.EventSource.VOICE,
                                type = CommittedEventType.SET_COMPLETED,
                                payload = buildJsonObject {},
                            ),
                        ),
                        SafetyResult(false), false, false, null,
                    )
                    expect["speak"]?.jsonPrimitive?.booleanOrNull?.let { assertEquals(it, d.speak, file.name) }
                    expect["actId"]?.jsonPrimitive?.contentOrNull?.let {
                        assertEquals(it, (d as SpeakDecision.Speak).actId.name.lowercase(), file.name)
                    }
                    expect["reason"]?.jsonPrimitive?.contentOrNull?.let {
                        assertEquals(it, (d as SpeakDecision.Silence).reason.name.lowercase(), file.name)
                    }
                }
            }
        }
    }

    @Test
    fun bannedIvrNeverSpoken() {
        val loop = harness()
        loop.start()
        loop.seedWorkingWeight(60.0)
        val t = loop.handleTurn("Got 10.")
        CoachingPhraser.banned.forEach { b ->
            assertFalse(b in t.spokenText.orEmpty())
        }
    }

    private fun goldenDir(): File = File("").absoluteFile.let { cwd ->
        listOf(File(cwd, "tests/golden"), File(cwd, "../tests/golden"), File(cwd, "../../tests/golden"))
            .first { it.isDirectory }
    }

    private fun harness(): CompanionLoop {
        val log = InMemoryEventLog()
        return CompanionLoop(
            WorkoutEngine(log), log,
            WorkoutPlan(
                "33333333-3333-4333-8333-333333333333",
                listOf(PlannedExercise("55555555-5555-4555-8555-555555555555", "44444444-4444-4444-8444-444444444444", 4)),
            ),
            "11111111-1111-4111-8111-111111111111",
            "22222222-2222-4222-8222-222222222222",
            clock = FakeClock(),
        )
    }
}
