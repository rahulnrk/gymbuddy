package com.gymbuddy.domain.loop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoopArchitectureTest {
    private fun loopDir(): File {
        val cwd = File("").absoluteFile
        return listOf(
            File(cwd, "src/main/kotlin/com/gymbuddy/domain/loop"),
            File(cwd, "domain/src/main/kotlin/com/gymbuddy/domain/loop"),
        ).first { it.isDirectory }
    }

    @Test
    fun loopCoordinatesDoesNotReimplementKernel() {
        val files = loopDir().walkTopDown().filter { it.extension == "kt" }.toList()
        val text = files.joinToString("\n") { it.readText() }
        assertTrue("class CompanionLoop" in text)
        assertTrue("NaturalLanguageExtractor" in text)
        assertTrue("CandidateValidator" in text)
        assertTrue("WorkoutEngine" in text)
        assertTrue("ShouldSpeak" in text)
        assertTrue("SafetyFirstTurn" in text || "extractor.extract" in text)
        assertFalse("androidx.compose" in text)
        assertFalse("androidx.room" in text)
        assertFalse("openai" in text)
        assertFalse("AudioManager" in text)
        files.forEach { f ->
            f.readLines().filter { it.trim().startsWith("import ") }.forEach { line ->
                assertFalse(Regex("""import android\.""").containsMatchIn(line), line)
            }
        }
        val speak = File(loopDir(), "SpeakPolicy.kt").readText()
        assertFalse("fun speak(" in speak && "TtsProvider" in speak)
        assertTrue("No TTS" in speak || "no TTS" in speak.lowercase() || "No TTS" in speak)
        assertFalse("WorkoutTransitionContract" in text)
        assertFalse("openai" in text.lowercase())
        assertFalse("elevenlabs" in text.lowercase())
        assertFalse("androidx.compose" in text)
    }

    @Test
    fun shouldSpeakIsPure() {
        val src = File(loopDir(), "SpeakPolicy.kt").readText()
        assertFalse("EventLog" in src)
        assertFalse("FakeTts" in src)
        assertFalse("append(" in src)
    }
}
