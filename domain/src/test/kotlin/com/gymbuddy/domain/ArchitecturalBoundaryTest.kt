package com.gymbuddy.domain

import com.gymbuddy.domain.context.FourContexts
import com.gymbuddy.domain.events.CandidateEvent
import com.gymbuddy.domain.validation.CandidateValidator
import com.gymbuddy.domain.validation.VoiceCommitRoute
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchitecturalBoundaryTest {

    private fun validationDir(): File {
        val cwd = File("").absoluteFile
        return listOf(
            File(cwd, "domain/src/main/kotlin/com/gymbuddy/domain/validation"),
            File(cwd, "src/main/kotlin/com/gymbuddy/domain/validation"),
            File(cwd, "../src/main/kotlin/com/gymbuddy/domain/validation"),
        ).firstOrNull { it.isDirectory } ?: error("validation sources not found from $cwd")
    }

    @Test
    fun validationSourcesHaveNoPersistenceVendorsOrSql() {
        val files = validationDir().listFiles { f -> f.extension == "kt" } ?: emptyArray()
        assertTrue(files.isNotEmpty())
        val imports = files.flatMap { it.readLines() }.filter { it.trim().startsWith("import ") }
        val forbidden = listOf(
            "androidx.room",
            "java.sql",
            "android.database",
            "com.gymbuddy.domain.persist",
            "com.gymbuddy.domain.ai",
            "com.gymbuddy.domain.voice",
            "androidx.compose",
            "org.sqlite",
        )
        imports.forEach { line ->
            forbidden.forEach { bad ->
                assertFalse(line.contains(bad), "validation import not allowed: $line")
            }
        }
        val body = files.joinToString("\n") { it.readText() }
        assertTrue("class DefaultCandidateValidator" in body)
        assertFalse("data class CommittedDraft" in body)
        assertFalse("INSERT " in body.uppercase())
        assertFalse("var sessionState" in body)
    }

    @Test
    fun candidateValidatorSignatureIsSnapshotOnly() {
        val m = CandidateValidator::class.java.methods.single { it.name == "validate" && it.parameterCount == 3 }
        assertEquals(3, m.parameterCount, m.parameterTypes.joinToString())
        val types = m.parameterTypes.toList()
        assertTrue(CandidateEvent::class.java in types)
        assertTrue(FourContexts::class.java in types)
        assertTrue(VoiceCommitRoute::class.java in types)
        assertFalse(types.any { it.name.contains("EventLog") })
        assertFalse(types.any { it.name.contains("LlmProvider") })
        assertFalse(types.any { it.name.contains("SttProvider") })
        assertTrue(m.returnType.name.contains("ValidationDecision"), m.returnType.name)
        assertTrue(FourContexts::class.java.declaredMethods.any { it.name.startsWith("component") })
        assertTrue(CandidateEvent::class.java.declaredMethods.any { it.name.startsWith("component") })
    }

    @Test
    fun domainBuildDoesNotDependOnRoom() {
        val cwd = File("").absoluteFile
        val domainBuild = listOf(
            File(cwd, "build.gradle.kts"),
            File(cwd, "domain/build.gradle.kts"),
            File(cwd.parentFile, "domain/build.gradle.kts"),
        ).first { it.isFile && it.readText().contains("kotlinx-serialization") }
        val text = domainBuild.readText()
        assertFalse(text.contains("androidx.room"), text)
        assertFalse(text.contains("androidx.sqlite"), text)
        assertFalse("project(\":persist\")" in text.replace("'", "\""))
        assertFalse("sqlite-jdbc" in text)
    }

    @Test
    fun domainMainHasNoRoomAndroidUiOrVendors() {
        val cwd = File("").absoluteFile
        val domainMain = listOf(
            File(cwd, "src/main/kotlin"),
            File(cwd, "domain/src/main/kotlin"),
            File(cwd.parentFile, "domain/src/main/kotlin"),
        ).first { it.isDirectory && File(it, "com/gymbuddy/domain").isDirectory }
        val files = domainMain.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty())
        files.forEach { f ->
            val text = f.readText()
            assertFalse("androidx.room" in text, f.path)
            assertFalse("androidx.compose" in text, f.path)
            assertFalse("org.sqlite" in text, f.path)
            text.lineSequence().filter { it.trim().startsWith("import ") }.forEach { line ->
                assertFalse(Regex("""import android\.""").containsMatchIn(line), line)
                assertFalse("java.sql" in line, line)
                assertFalse("androidx.compose" in line, line)
            }
        }
    }
}
