package com.gymbuddy.app.hud

import com.gymbuddy.domain.CommittedEventType
import com.gymbuddy.domain.EventSource
import com.gymbuddy.domain.SessionId
import com.gymbuddy.domain.engine.EngineResult
import com.gymbuddy.domain.engine.WorkoutSessionSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class Pr10GoldenRunnerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun uiGoldens() {
        val dir = goldenDir()
        val files = dir.listFiles { f ->
            f.extension == "json" && f.name.startsWith("01-lifecycle.ui.")
        }!!.sortedBy { it.name }
        assertTrue(files.isNotEmpty(), "missing 01-lifecycle.ui goldens")
        files.forEach { file ->
            val data = json.parseToJsonElement(file.readText()).jsonObject
            val expect = data["expect"]!!.jsonObject
            when {
                expect["everyControlHasLabel"]?.jsonPrimitive?.booleanOrNull == true -> {
                    val ui = startedUi()
                    assertEquals(
                        expect["focusOrder"]!!.jsonArray.map { it.jsonPrimitive.content },
                        ui.focusOrder,
                        file.name,
                    )
                    assertTrue(ui.controls.all { it.label.isNotBlank() }, file.name)
                    assertEquals(expect["minTapTargetDp"]!!.jsonPrimitive.intOrNull, ui.minTapTargetDp, file.name)
                    assertTrue(ui.highContrast, file.name)
                    assertEquals(ui.focusOrder, ui.controls.map { it.id }, file.name)
                }
                expect["workoutStartedSource"] != null -> {
                    val (log, hud) = GymHudHarness.session()
                    hud.start()
                    val events = log.sessionEvents(SessionId(GymHudHarness.SESSION))
                    val sequence = expect["sequence"]!!.jsonArray.map { CommittedEventType.valueOf(it.jsonPrimitive.content) }
                    assertEquals(sequence, events.map { it.type }, file.name)
                    assertEquals(EventSource.MANUAL, events.first { it.type == CommittedEventType.WORKOUT_STARTED }.source, file.name)
                    assertEquals(EventSource.SYSTEM, events.first { it.type == CommittedEventType.SESSION_INITIALIZED }.source, file.name)
                    val second = hud.start() as EngineResult.Committed
                    assertTrue(second.events.isEmpty(), file.name)
                }
                expect["setSource"] != null -> {
                    val (log, hud) = GymHudHarness.session()
                    hud.start()
                    hud.logManualSet(10, 60.0)
                    val set = log.sessionEvents(SessionId(GymHudHarness.SESSION)).first { it.type == CommittedEventType.SET_COMPLETED }
                    assertEquals(EventSource.MANUAL, set.source, file.name)
                    assertEquals("manual", expect["setSource"]!!.jsonPrimitive.content, "golden documents source=manual")
                }
                expect["isChatThread"] != null -> {
                    val ui = GymHudBinder.bind(
                        WorkoutSessionSnapshot(),
                        caption = CaptionStub(userLine = "Got 10.", aiLine = "Nice."),
                    )
                    assertFalse(ui.caption.isChatThread, file.name)
                    assertEquals(0, ui.caption.bubbleCount, file.name)
                    assertTrue(ui.caption.minimized, file.name)
                }
                expect["fields"] != null -> {
                    val (_, hud) = GymHudHarness.session()
                    hud.start()
                    val ui = hud.ui()
                    val fields = expect["fields"]!!.jsonArray.map { it.jsonPrimitive.content }
                    fields.forEach { field ->
                        when (field) {
                            "exerciseName" -> assertTrue(ui.exerciseName.isNotBlank(), file.name)
                            "kicker" -> assertTrue("exercise" in ui.kicker, file.name)
                            "weight" -> assertTrue(ui.weightLabel.isNotBlank(), file.name)
                            "set" -> assertTrue(ui.setNumber >= 1 && ui.setLabel.isNotBlank(), file.name)
                            "target" -> assertTrue(ui.targetSets >= 1, file.name)
                            "setGrid" -> assertTrue(ui.setPills.isNotEmpty(), file.name)
                            "previous" -> assertEquals("no previous", ui.previousPerformance, file.name)
                            "rest" -> assertFalse(ui.rest.visible, file.name)
                            "pocket" -> assertTrue(ui.pocket.label.isNotBlank(), file.name)
                            "progress" -> assertTrue("exercise" in ui.progress && "sets" in ui.progress, file.name)
                            "voice" -> assertEquals(VoiceIndicator.LISTENING, ui.voice, file.name)
                            else -> fail("${file.name} unknown field $field")
                        }
                    }
                }
                expect["noWorkoutStartedOnResume"]?.jsonPrimitive?.booleanOrNull == true -> {
                    val (log, hud) = GymHudHarness.session()
                    hud.start()
                    hud.pause()
                    hud.resume()
                    val types = log.sessionEvents(SessionId(GymHudHarness.SESSION)).map { it.type }
                    assertEquals(1, types.count { it == CommittedEventType.WORKOUT_STARTED }, file.name)
                    assertTrue(CommittedEventType.WORKOUT_RESUMED in types, file.name)
                    assertTrue(CommittedEventType.WORKOUT_PAUSED in types, file.name)
                }
                else -> fail("unmatched golden shape: ${file.name}")
            }
        }
    }

    private fun startedUi(): HudUiState {
        val (_, hud) = GymHudHarness.session()
        hud.start()
        return hud.ui()
    }

    private fun goldenDir(): File {
        val fromResources = javaClass.classLoader.getResource("01-lifecycle.ui.a11y-labels-focus-order.json")
        if (fromResources != null) return File(fromResources.toURI()).parentFile
        val roots = listOf(File("tests/golden"), File("../tests/golden"))
        return roots.first { it.isDirectory }
    }
}
