package com.gymbuddy.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class Pr1GoldenStructureTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun goldenDir(): File {
        val cwd = File("").absoluteFile
        return listOf(
            File(cwd, "tests/golden"),
            File(cwd, "../tests/golden"),
            File(cwd, "../../tests/golden"),
        ).firstOrNull { it.isDirectory } ?: error("tests/golden not found from $cwd")
    }

    @Test
    fun everyPr1GoldenIsExecutable() {
        val forbidden = setOf(
            "setNumbers",
            "officialSetNumbers",
            "restStartedBetween",
            "restStartedAfterLastIfPlannedRemain",
            "REST_STARTED",
            "REST_ENDED",
            "SET_STARTED",
            "foldRestores",
        )
        var n = 0
        goldenDir().listFiles { f -> f.extension == "json" }!!.forEach { file ->
            val root = json.parseToJsonElement(file.readText()).jsonObject
            val pr = root["pr"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            if ("1" !in pr) return@forEach
            n++
            val input = root["input"] as? JsonObject ?: fail("${file.name} missing input")
            val cands = input["candidates"]?.jsonArray
            assertTrue(!cands.isNullOrEmpty(), "${file.name} missing candidates")
            val given = root["given"] as? JsonObject
            assertTrue(given != null && given.isNotEmpty(), "${file.name} missing given")
            val expect = root["expect"] as? JsonObject
            assertTrue(expect != null && expect.isNotEmpty(), "${file.name} missing expect")
            forbidden.forEach { key ->
                assertTrue(key !in expect, "${file.name} PR 1 expect must not contain $key")
            }
        }
        assertTrue(n >= 15, "executable PR 1 goldens=$n")
    }
}
