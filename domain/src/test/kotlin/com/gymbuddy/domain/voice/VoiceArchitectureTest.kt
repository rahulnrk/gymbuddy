package com.gymbuddy.domain.voice

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceArchitectureTest {
    private fun voiceDir(): File {
        val cwd = File("").absoluteFile
        return listOf(
            File(cwd, "src/main/kotlin/com/gymbuddy/domain/voice"),
            File(cwd, "domain/src/main/kotlin/com/gymbuddy/domain/voice"),
            File(cwd.parentFile, "domain/src/main/kotlin/com/gymbuddy/domain/voice"),
        ).first { it.isDirectory }
    }

    @Test
    fun voicePackageHasNoAndroidRoomVendorsOrUi() {
        val files = voiceDir().walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(files.any { it.name == "VoiceTurnMachine.kt" })
        val text = files.joinToString("\n") { it.readText() }
        val banned = listOf(
            "androidx.room",
            "androidx.compose",
            "android.media",
            "android.bluetooth",
            "AudioTrack",
            "MediaPlayer",
            "AudioManager",
            "android.speech",
            "com.google.cloud.speech",
            "openai",
            "anthropic",
            "whisper",
            "elevenlabs",
            "WorkoutEngine",
            "DefaultCandidateValidator",
            "EventLog",
            "GymBuddyDatabase",
        )
        banned.forEach { b ->
            assertFalse(b in text, "voice package must not contain $b")
        }
        files.forEach { f ->
            f.readLines().filter { it.trim().startsWith("import ") }.forEach { line ->
                assertFalse(Regex("""import android\.""").containsMatchIn(line), line)
                assertFalse("java.sql" in line, line)
            }
        }
    }

    @Test
    fun voiceLayerDoesNotCreateEventsOrAssignSets() {
        val src = File(voiceDir(), "VoiceTurnMachine.kt").readText()
        assertFalse("WorkoutEvent(" in src)
        assertFalse("EventFactory" in src)
        assertFalse("setNumber" in src)
        assertFalse("WorkoutSessionSnapshot" in src)
        assertFalse("applyFacts" in src)
        assertTrue("Does not classify" in src || "do not classify" in src.lowercase())
    }

    @Test
    fun engineSourcesDoNotImportVoiceProviders() {
        val cwd = File("").absoluteFile
        val engine = listOf(
            File(cwd, "src/main/kotlin/com/gymbuddy/domain/engine"),
            File(cwd, "domain/src/main/kotlin/com/gymbuddy/domain/engine"),
        ).first { it.isDirectory }
        val text = engine.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertFalse("com.gymbuddy.domain.voice" in text)
        assertFalse("AudioManager" in text)
        assertFalse("android.media" in text)
    }
}
