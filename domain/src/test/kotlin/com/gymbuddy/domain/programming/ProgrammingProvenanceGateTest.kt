package com.gymbuddy.domain.programming

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ProgrammingProvenanceGateTest {

    @Test
    fun everyProgrammingConstantHasADecisionsRow() {
        val sources = programmingMainDir().listFiles { f -> f.extension == "kt" }!!.toList()
        assertTrue(sources.isNotEmpty())
        val constRe = Regex("""const val ([A-Z][A-Z0-9_]*)\s*=""")
        val names = sources.flatMap { constRe.findAll(it.readText()).map { m -> m.groupValues[1] } }
        assertTrue(names.isNotEmpty(), "no const val in programming package")
        val decisions = decisionsFile().readText()
        val missing = names.filter { it !in decisions }
        assertTrue(
            missing.isEmpty(),
            "constants with no provenance row in DECISIONS_AND_THRESHOLDS.md: $missing",
        )
    }

    @Test
    fun publishedGuidelineClassExists() {
        val text = decisionsFile().readText()
        assertTrue("PUBLISHED GUIDELINE" in text)
    }

    private fun decisionsFile(): File {
        val cwd = File("").absoluteFile
        return listOf(
            File(cwd, "docs/living-spec/DECISIONS_AND_THRESHOLDS.md"),
            File(cwd, "../docs/living-spec/DECISIONS_AND_THRESHOLDS.md"),
            File(cwd.parentFile, "docs/living-spec/DECISIONS_AND_THRESHOLDS.md"),
        ).first { it.isFile }
    }
}
