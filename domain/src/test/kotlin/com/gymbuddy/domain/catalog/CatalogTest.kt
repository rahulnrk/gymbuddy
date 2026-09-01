package com.gymbuddy.domain.catalog

import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogTest {
    private val catalog = loadCatalog()

    @Test
    fun everyEntryDecodesAndRejectsUnknownKeys() {
        assertTrue(catalog.exercises.isNotEmpty())
        val raw = knowledge("catalog/exercises.json")
        assertFalse(raw.contains("\"load\""))
        val polluted = raw.replaceFirst("\"slug\"", "\"load\":\"barbell\",\"slug\"")
        assertFails {
            Catalog.load(
                knowledge("vocab/muscles.json"),
                knowledge("vocab/patterns.json"),
                knowledge("vocab/equipment.json"),
                knowledge("vocab/conditions.json"),
                polluted,
            )
        }
    }

    @Test
    fun idsRegenerateFromSlugsAndPinsMatch() {
        catalog.exercises.forEach { ex ->
            assertEquals(CatalogIds.fromSlug(ex.slug).toString(), ex.id, ex.slug)
        }
        val pins = mapOf(
            "bench-press" to "3d3f7200-5c48-5274-8f63-763a7d095f6d",
            "back-squat" to "36d57af1-8747-59c3-a38f-dddd80753ad4",
            "romanian-deadlift" to "66174728-d7e1-5441-b0a8-1fdf9be4e61d",
            "lat-pulldown" to "5712e8e9-a9c0-5345-8874-ee59822fa664",
            "plank" to "1b431f65-a7d2-542b-a290-90ebc156de1f",
        )
        pins.forEach { (slug, id) ->
            assertEquals(id, catalog.bySlug(slug)!!.id)
            assertEquals(UUID.fromString(id), CatalogIds.fromSlug(slug))
        }
    }

    @Test
    fun everyEquipmentTokenExists() {
        catalog.exercises.forEach { ex ->
            assertTrue(ex.requiredEquipment.isNotEmpty(), "${ex.slug} has no required slots")
            ex.requiredEquipment.forEach { slot ->
                assertTrue(slot.isNotEmpty(), "${ex.slug} has an empty slot")
                slot.forEach { id ->
                    assertNotNull(catalog.equipment[id], "${ex.slug} unknown required $id")
                }
            }
            ex.optionalEquipment.forEach { id ->
                assertNotNull(catalog.equipment[id], "${ex.slug} unknown optional $id")
            }
        }
    }

    @Test
    fun everyMuscleOverrideExists() {
        catalog.exercises.forEach { ex ->
            ex.muscleOverrides.orEmpty().keys.forEach { id ->
                assertNotNull(catalog.muscles[id], "${ex.slug} unknown muscle $id")
            }
        }
    }

    @Test
    fun everyPatternExists() {
        catalog.exercises.forEach { ex ->
            assertNotNull(catalog.patterns[ex.pattern], "${ex.slug} unknown pattern ${ex.pattern}")
        }
    }

    @Test
    fun everyContraindicationExists() {
        catalog.exercises.forEach { ex ->
            ex.contraindications.forEach { id ->
                assertNotNull(catalog.conditions[id], "${ex.slug} unknown condition $id")
            }
        }
    }

    @Test
    fun everySubstituteResolves() {
        catalog.exercises.forEach { ex ->
            ex.substitutes.forEach { slug ->
                assertNotNull(catalog.bySlug(slug), "${ex.slug} missing substitute $slug")
            }
        }
    }

    @Test
    fun noSharedSlugIdOrHighConfidenceAlias() {
        assertEquals(catalog.exercises.size, catalog.exercises.map { it.slug }.toSet().size)
        assertEquals(catalog.exercises.size, catalog.exercises.map { it.id }.toSet().size)
        val high = mutableMapOf<Pair<String, String>, String>()
        catalog.exercises.forEach { ex ->
            ex.aliases.filter { it.confidence >= 0.9 }.forEach { a ->
                val key = a.text.lowercase() to a.locale
                val prev = high.put(key, ex.slug)
                assertNull(prev, "alias $key shared by $prev and ${ex.slug}")
            }
        }
    }

    @Test
    fun pecDeckDoesNotResolveToCableFly() {
        val hits = catalog.resolveSpoken("pec deck", "en-IN")
        assertTrue(hits.none { it.exercise.slug == "cable-fly" }, hits.toString())
        assertTrue(hits.none { it.confidence >= Catalog.HIGH_CONFIDENCE }, hits.toString())
    }

    @Test
    fun latsIsNotAHighConfidenceExercise() {
        val hits = catalog.resolveSpoken("lats", "*")
        assertTrue(hits.none { it.confidence >= Catalog.HIGH_CONFIDENCE }, hits.toString())
    }

    @Test
    fun trainableWithNoneIsBodyweightOnly() {
        val hits = catalog.trainableWith(listOf("none"))
        assertTrue(hits.isNotEmpty())
        hits.forEach { ex ->
            assertEquals(listOf(listOf("none")), ex.requiredEquipment, ex.slug)
        }
        assertTrue(hits.any { it.slug == "plank" })
        assertTrue(hits.any { it.slug == "push-up" })
        assertTrue(hits.none { it.slug == "lat-pulldown" })
        assertTrue(hits.none { it.slug == "hanging-leg-raise" })
    }

    @Test
    fun catalogGrewPastThePrototypeSet() {
        assertEquals(77, catalog.exercises.size)
        catalog.exercises.forEach { ex ->
            assertTrue(ex.bestFitGoals.isNotEmpty(), ex.slug)
        }
    }

    @Test
    fun gobletSquatIsTrainableWithKettlebellOrDumbbell() {
        val kb = catalog.trainableWith(listOf("kettlebell")).map { it.slug }
        val db = catalog.trainableWith(listOf("dumbbell")).map { it.slug }
        assertTrue("goblet-squat" in kb)
        assertTrue("goblet-squat" in db)
        assertTrue("farmer-carry" in kb)
        assertTrue("farmer-carry" in db)
        assertTrue("farmer-carry" in catalog.trainableWith(listOf("farmer-handles")).map { it.slug })
    }

    @Test
    fun musclesForUsesOverrideElsePattern() {
        val rdl = catalog.bySlug("romanian-deadlift")!!
        assertEquals(rdl.muscleOverrides, catalog.musclesFor(rdl))
        val fly = catalog.bySlug("cable-fly")!!
        // Cable Fly has overrides in the prototype; still override-else-pattern.
        val expected = fly.muscleOverrides ?: catalog.patterns.getValue(fly.pattern).muscles
        assertEquals(expected, catalog.musclesFor(fly))
    }

    @Test
    fun benchPressLegacyFixtureIdResolves() {
        assertEquals("bench-press", catalog.byId("44444444-4444-4444-8444-444444444444")?.slug)
    }

    @Test
    fun namespaceIsPinned() {
        assertEquals(
            CatalogIds.fromSlug("catalog.gymbuddy.app", CatalogIds.DNS_NAMESPACE).toString(),
            CatalogIds.NAMESPACE.toString(),
        )
    }

    companion object {
        fun loadCatalog(): Catalog = Catalog.load(
            knowledge("vocab/muscles.json"),
            knowledge("vocab/patterns.json"),
            knowledge("vocab/equipment.json"),
            knowledge("vocab/conditions.json"),
            knowledge("catalog/exercises.json"),
        )

        fun knowledge(path: String): String {
            val res = CatalogTest::class.java.classLoader.getResource(path)
            if (res != null) return res.readText()
            val files = listOf(
                File("../knowledge/$path"),
                File("knowledge/$path"),
            )
            return files.first { it.isFile }.readText()
        }
    }
}
