package com.gymbuddy.domain.context

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextArchitectureTest {
    private fun contextDir(): File {
        val cwd = File("").absoluteFile
        return listOf(
            File(cwd, "src/main/kotlin/com/gymbuddy/domain/context"),
            File(cwd, "domain/src/main/kotlin/com/gymbuddy/domain/context"),
            File(cwd.parentFile, "domain/src/main/kotlin/com/gymbuddy/domain/context"),
        ).first { it.isDirectory }
    }

    @Test
    fun contextPackageHasNoRoomVendorsEngineMutationOrSpeechParsing() {
        val files = contextDir().listFiles { f -> f.extension == "kt" }!!.toList()
        assertTrue(files.any { it.name == "FourContextAssembler.kt" })
        val text = files.joinToString("\n") { it.readText() }
        val banned = listOf(
            "androidx.room",
            "java.sql",
            "org.sqlite",
            "androidx.compose",
            "SttProvider",
            "TtsProvider",
            "VadProvider",
            "WakeWordProvider",
            "LlmProvider",
            "WorkoutEngine",
            "DefaultCandidateValidator",
            "validateTurn",
            "append(",
            "EventLog",
            "decodePayload",
            "CandidateEvent",
        )
        banned.forEach { b ->
            assertFalse(b in text, "context package must not contain $b")
        }
        assertFalse("fun parseSpeech" in text)
        assertTrue("does not parse speech" in text.lowercase())
        assertTrue("FourContextAssembler" in text)
    }

    @Test
    fun assemblerDoesNotWriteRoomOrCreateEvents() {
        val src = File(contextDir(), "FourContextAssembler.kt").readText()
        assertFalse("GymBuddyDatabase" in src)
        assertFalse("RoomEventLog" in src)
        assertFalse("WorkoutEvent(" in src)
        assertFalse("EventFactory" in src)
        assertTrue("Read-only" in src || "read-only" in src)
    }
}
