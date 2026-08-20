package com.gymbuddy.domain.safety

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafetyArchitectureTest {
    private fun safetyDir(): File {
        val cwd = File("").absoluteFile
        return listOf(
            File(cwd, "src/main/kotlin/com/gymbuddy/domain/safety"),
            File(cwd, "domain/src/main/kotlin/com/gymbuddy/domain/safety"),
        ).first { it.isDirectory }
    }

    @Test
    fun safetyPackageHasNoVendorsEnginePersistOrUi() {
        val files = safetyDir().listFiles { f -> f.extension == "kt" }!!.toList()
        assertTrue(files.any { it.name == "DefaultSafetyClassifier.kt" })
        val text = files.joinToString("\n") { it.readText() }
        listOf(
            "androidx.room",
            "androidx.compose",
            "java.sql",
            "WorkoutEngine",
            "EventLog",
            "GymBuddyDatabase",
            "SttProvider",
            "TtsProvider",
            "LlmProvider",
            "openai",
            "setNumber",
            "applyFacts",
        ).forEach { b ->
            assertFalse(b in text, "safety must not contain $b")
        }
        files.forEach { f ->
            f.readLines().filter { it.trim().startsWith("import ") }.forEach { line ->
                assertFalse(Regex("""import android\.""").containsMatchIn(line), line)
                assertFalse("com.gymbuddy.domain.engine" in line, line)
                assertFalse("com.gymbuddy.domain.persist" in line, line)
                assertFalse("com.gymbuddy.domain.ai" in line, line)
                assertFalse("com.gymbuddy.domain.voice" in line, line)
            }
        }
        assertTrue("No LLM" in text || "no LLM" in text || "No LLM" in text.lowercase() || "no llm" in text.lowercase())
    }

    @Test
    fun classifierDoesNotCreateEvents() {
        val src = File(safetyDir(), "DefaultSafetyClassifier.kt").readText()
        assertFalse("WorkoutEvent(" in src)
        assertFalse("EventFactory" in src)
        assertTrue("No LLM" in src || "no LLM" in src)
    }
}
