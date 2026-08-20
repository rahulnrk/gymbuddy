package com.gymbuddy.domain.extract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtractArchitectureTest {
    private fun extractDir(): File {
        val cwd = File("").absoluteFile
        return listOf(
            File(cwd, "src/main/kotlin/com/gymbuddy/domain/extract"),
            File(cwd, "domain/src/main/kotlin/com/gymbuddy/domain/extract"),
        ).first { it.isDirectory }
    }

    @Test
    fun extractHasNoRoomEnginePersistOrVendors() {
        val files = extractDir().walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(files.any { it.name == "NaturalLanguageExtractor.kt" })
        val text = files.joinToString("\n") { it.readText() }
        listOf(
            "androidx.room",
            "androidx.compose",
            "WorkoutEngine",
            "EventLog",
            "GymBuddyDatabase",
            "WorkoutEvent(",
            "openai",
            "anthropic",
            "nextOfficialSetNumber",
        ).forEach { b ->
            assertFalse(b in text, b)
        }
        files.forEach { f ->
            f.readLines().filter { it.trim().startsWith("import ") }.forEach { line ->
                assertFalse(Regex("""import android\.""").containsMatchIn(line), line)
                assertFalse("com.gymbuddy.domain.engine" in line, line)
                assertFalse("com.gymbuddy.domain.persist" in line, line)
            }
        }
        assertTrue("SafetyFirstTurn" in text)
        assertTrue("CandidateEventEnvelope" in text)
    }

    @Test
    fun temperatureIsCapped() {
        val src = File(extractDir(), "SchemaConstrainedFakeExtractor.kt").readText()
        assertTrue("0.2" in src)
        assertTrue("temperature must be <= 0.2" in src)
    }
}
