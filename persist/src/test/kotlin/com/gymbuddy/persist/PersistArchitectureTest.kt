package com.gymbuddy.persist

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistArchitectureTest {
    private fun persistMain(): File {
        val cwd = File("").absoluteFile
        return listOf(
            File(cwd, "persist/src/main/kotlin"),
            File(cwd, "src/main/kotlin"),
        ).first { it.isDirectory }
    }

    private fun repoRoot(): File {
        val cwd = File("").absoluteFile
        return listOf(cwd, cwd.parentFile).first { File(it, "settings.gradle.kts").isFile }
    }

    @Test
    fun persistMainHasNoConversationOrEngineDecisions() {
        val text = persistMain().walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        val banned = listOf(
            "DefaultCandidateValidator",
            "CandidateValidator",
            "shouldSpeak",
            "LlmProvider",
            "SttProvider",
            "TtsProvider",
            "VadProvider",
            "androidx.compose",
            "GymModeHud",
            "Dashboard",
            "WorkoutEngine",
            "WorkoutProjector",
            "validateTurn",
        )
        banned.forEach { b ->
            assertFalse(b in text, "persist must not contain $b")
        }
        assertTrue("WorkoutEventEntity" in text)
        assertTrue("RoomEventLog" in text)
        assertTrue("never ValidatedFact" in text)
        assertFalse("DROP TABLE" in text.uppercase().replace("  ", " "))
    }

    @Test
    fun persistDependsOnDomainNotTheReverse() {
        val root = repoRoot()
        val persistBuild = File(root, "persist/build.gradle.kts").readText()
        val domainBuild = File(root, "domain/build.gradle.kts").readText()
        assertTrue("project(\":domain\")" in persistBuild.replace("'", "\""))
        assertFalse("project(\":persist\")" in domainBuild.replace("'", "\""))
        assertFalse("androidx.room" in domainBuild)
        assertFalse("sqlite-jdbc" in domainBuild)
    }

    @Test
    fun domainDoesNotDependOnPersistOrRoom() {
        val cwd = File("").absoluteFile
        val domainBuild = listOf(
            File(cwd, "domain/build.gradle.kts"),
            File(cwd.parentFile, "domain/build.gradle.kts"),
            File(cwd, "../domain/build.gradle.kts"),
        ).first { it.isFile }
        val text = domainBuild.readText()
        assertFalse("androidx.room" in text)
        assertFalse("project(\":persist\")" in text.replace("'", "\""))
        assertFalse("sqlite-jdbc" in text)
    }

    @Test
    fun domainMainHasNoRoomAndroidOrVendors() {
        val domainMain = File(repoRoot(), "domain/src/main/kotlin")
        val files = domainMain.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty())
        files.forEach { f ->
            val text = f.readText()
            assertFalse("androidx.room" in text, f.path)
            assertFalse("androidx.compose" in text, f.path)
            assertFalse("androidx.sqlite" in text, f.path)
            assertFalse("org.sqlite" in text, f.path)
            assertFalse("openai" in text.lowercase() && "import" in text, f.path)
            text.lineSequence().filter { it.trim().startsWith("import ") }.forEach { line ->
                assertFalse(line.contains("android."), "domain android import: $line")
                assertFalse(line.contains("java.sql"), "domain sql import: $line")
                assertFalse(line.contains("whisper"), line)
                assertFalse(line.contains("vosk"), line)
            }
        }
    }

    @Test
    fun onlyCommittedWorkoutEventsArePersisted() {
        val db = GymBuddyDatabase.inMemory()
        db.connection.createStatement().use { st ->
            st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use { rs ->
                val names = mutableSetOf<String>()
                while (rs.next()) names += rs.getString(1)
                assertTrue("event_log" in names)
                assertFalse("validated_fact" in names)
                assertFalse("candidate_event" in names)
            }
        }
        db.close()
    }
}
