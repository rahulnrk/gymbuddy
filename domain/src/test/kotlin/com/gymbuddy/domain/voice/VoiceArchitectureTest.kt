package com.gymbuddy.domain.voice

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceArchitectureTest {
    @Test
    fun productionMainDoesNotDependOnVoiceFakes() {
        val cwd = File("").absoluteFile
        val main = listOf(
            File(cwd, "src/main/kotlin"),
            File(cwd, "domain/src/main/kotlin"),
        ).first { it.isDirectory }
        val files = main.walkTopDown()
            .filter { it.extension == "kt" }
            .filter { !it.path.replace('\\', '/').contains("/voice/fake/") }
            .toList()
        assertTrue(files.isNotEmpty())
        val fakeType = Regex("""\bFake[A-Z]\w*""")
        files.forEach { f ->
            f.readLines().forEach eachImport@{ raw ->
                val line = raw.trim()
                if (!line.startsWith("import ")) return@eachImport
                assertFalse(
                    "voice.fake" in line,
                    "${f.name} imports voice.fake: $line",
                )
                assertFalse(
                    fakeType.containsMatchIn(line),
                    "${f.name} imports a Fake type: $line",
                )
            }
            constructorTypes(f.readText()).forEach { type ->
                assertFalse(
                    type.startsWith("Fake"),
                    "${f.name} production type $type",
                )
            }
        }
    }
}

private fun constructorTypes(src: String): List<String> {
    val stripped = src.replace(Regex("""/\*[\s\S]*?\*/"""), " ")
        .lineSequence()
        .map { line ->
            val cut = line.indexOf("//")
            if (cut >= 0) line.substring(0, cut) else line
        }
        .joinToString("\n")
    return Regex(""":\s*([A-Z][A-Za-z0-9_.]*)""").findAll(stripped).map { it.groupValues[1].substringAfterLast('.') }.toList()
}
