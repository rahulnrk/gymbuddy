package com.gymbuddy.domain.voice

import com.gymbuddy.domain.VoiceTurnState
import com.gymbuddy.domain.voice.fake.FakeAudioFocus
import com.gymbuddy.domain.voice.fake.FakeClock
import com.gymbuddy.domain.voice.fake.FakeLid
import com.gymbuddy.domain.voice.fake.FakeStt
import com.gymbuddy.domain.voice.fake.FakeTts
import com.gymbuddy.domain.voice.fake.FakeVad
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Pr5GoldenRunnerTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun goldenDir(): File = File("").absoluteFile.let { cwd ->
        listOf(File(cwd, "tests/golden"), File(cwd, "../tests/golden"), File(cwd, "../../tests/golden"))
            .first { it.isDirectory }
    }

    private fun machine(): VoiceTurnMachine {
        val clock = FakeClock()
        return VoiceTurnMachine(clock, FakeVad(), FakeStt(clock), FakeTts(clock, cancelDelayMs = 40), FakeLid(), FakeAudioFocus())
    }

    @Test
    fun bargeInLt200msGolden() {
        val file = File(goldenDir(), "03-voice.voice.barge-in-lt-200ms.json")
        val data = json.parseToJsonElement(file.readText()).jsonObject
        val expect = data["expect"]!!.jsonObject
        val cap = expect["bargeInCancelP95Ms"]!!.jsonPrimitive.intOrNull ?: 200
        val m = machine()
        m.start()
        m.speak("Your next set is")
        (m.clock as FakeClock).advance(30)
        m.onSpeechStart()
        assertTrue(m.lastBargeInLatencyMs!! < cap)
        assertTrue(expect["ttsMustCancel"]?.jsonPrimitive?.booleanOrNull != false)
        assertTrue((m.tts as FakeTts).lastHandle!!.cancelled)
        assertFalse(m.tts.speaking)
        assertEquals(VoiceTurnState.USER_SPEAKING, m.state)
    }

    @Test
    fun echoDoesNotBargeInGolden() {
        val file = File(goldenDir(), "03-voice.event.echo-does-not-log-ai-words.json")
        val expect = json.parseToJsonElement(file.readText()).jsonObject["expect"]!!.jsonObject
        val m = machine()
        m.start()
        m.speak("Nice. That’s one more than last time.")
        m.onSpeechStart(echoFromTts = true)
        if (expect["echoDoesNotBargeIn"]?.jsonPrimitive?.booleanOrNull == true) {
            assertEquals(VoiceTurnState.AI_SPEAKING, m.state)
        }
        if (expect["ttsContinues"]?.jsonPrimitive?.booleanOrNull == true) {
            assertTrue(m.tts.speaking)
        }
    }

    @Test
    fun lidTeluguEnglishGolden() {
        val file = File(goldenDir(), "03-voice.multilingual.lid-telugu-english.json")
        val data = json.parseToJsonElement(file.readText()).jsonObject
        val transcript = data["input"]!!.jsonObject["transcript"]!!.jsonPrimitive.content
        val m = machine()
        m.start()
        m.stt.startRecognition()
        m.onSpeechStart()
        val f = m.onFinal(transcript)
        val expect = data["expect"]!!.jsonObject
        assertEquals(expect["lang"]!!.jsonPrimitive.content, f.lang)
        assertEquals(expect["mixed"]!!.jsonPrimitive.booleanOrNull, f.mixed)
        if (expect["transcriptUnchanged"]?.jsonPrimitive?.booleanOrNull == true) {
            assertEquals(transcript, f.text)
        }
    }

    @Test
    fun engineHasNoOsAudioImportGolden() {
        val file = File(goldenDir(), "27-music.event.engine-has-no-os-audio-import.json")
        json.parseToJsonElement(file.readText())
        val cwd = File("").absoluteFile
        val engine = listOf(
            File(cwd, "src/main/kotlin/com/gymbuddy/domain/engine"),
            File(cwd, "domain/src/main/kotlin/com/gymbuddy/domain/engine"),
        ).first { it.isDirectory }
        val text = engine.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertFalse("AudioManager" in text)
        assertFalse("android.media" in text)
    }

    @Test
    fun musicDuckGoldens() {
        val dir = goldenDir()
        listOf(
            "27-music.event.duck-before-tts.json",
            "27-music.event.restore-after-barge-in.json",
            "27-music.event.unsupported-still-logs.json",
        ).forEach { name ->
            val data = json.parseToJsonElement(File(dir, name).readText()).jsonObject
            val expect = data["expect"]!!.jsonObject
            val unsupported = expect["unsupported"]?.jsonPrimitive?.booleanOrNull == true
            val m = VoiceTurnMachine(
                FakeClock(),
                FakeVad(),
                FakeStt(FakeClock()),
                FakeTts(FakeClock()),
                FakeLid(),
                FakeAudioFocus(duckSupported = !unsupported),
            )
            m.start()
            m.speak("Cue the set")
            val focus = m.focus as FakeAudioFocus
            if (expect["duckRequested"]?.jsonPrimitive?.booleanOrNull == true) {
                assertTrue(focus.log.any { it.startsWith("duck:") }, name)
            }
            if (expect["ttsStarted"]?.jsonPrimitive?.booleanOrNull == true) {
                assertEquals(VoiceTurnState.AI_SPEAKING, m.state, name)
            }
            if (expect["unsupported"]?.jsonPrimitive?.booleanOrNull == true) {
                assertTrue("unsupported" in focus.log, name)
                assertEquals(VoiceTurnState.AI_SPEAKING, m.state, name)
            }
            if (name.contains("restore-after-barge-in")) {
                m.onSpeechStart()
                assertTrue("restore" in focus.log, name)
                assertTrue((m.tts as FakeTts).lastHandle!!.cancelled, name)
            } else {
                m.onTtsEnded()
                if (expect["restoreRequested"]?.jsonPrimitive?.booleanOrNull == true) {
                    assertTrue("restore" in focus.log, name)
                }
            }
            m.stop()
            if (expect["focusReleased"]?.jsonPrimitive?.booleanOrNull == true) {
                assertTrue("release" in focus.log, name)
            }
        }
    }
}
