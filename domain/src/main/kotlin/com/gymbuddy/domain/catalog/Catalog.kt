package com.gymbuddy.domain.catalog

import kotlinx.serialization.json.Json

/**
 * In-memory exercise catalog. The parser takes strings; the caller supplies them.
 * No file IO, no SQL, no Android.
 */
class Catalog(
    val muscles: Map<String, Muscle>,
    val patterns: Map<String, Pattern>,
    val equipment: Map<String, Equipment>,
    val conditions: Map<String, Condition>,
    val exercises: List<Exercise>,
) {
    private val bySlugMap = exercises.associateBy { it.slug }
    private val byIdMap = buildMap {
        exercises.forEach { ex ->
            put(ex.id, ex)
            ex.legacyIds.forEach { put(it, ex) }
        }
    }

    fun bySlug(slug: String): Exercise? = bySlugMap[slug]

    fun byId(uuid: String): Exercise? = byIdMap[uuid]

    fun musclesFor(exercise: Exercise): Map<String, Double> =
        exercise.muscleOverrides ?: patterns.getValue(exercise.pattern).muscles

    fun trainableWith(equipmentIds: Collection<String>): List<Exercise> {
        val have = equipmentIds.toSet()
        return exercises.filter { ex -> ex.requiredEquipment.all { it in have } }
    }

    /**
     * Ranked spoken matches. Never a single silent pick: if the top two
     * confidences differ by less than [ASK_MARGIN], the caller should ask.
     */
    fun resolveSpoken(text: String, locale: String): List<SpokenCandidate> {
        val q = normalize(text)
        if (q.isEmpty()) return emptyList()
        val out = mutableListOf<SpokenCandidate>()
        for (ex in exercises) {
            fun consider(raw: String, confidence: Double) {
                if (normalize(raw) == q) out += SpokenCandidate(ex, confidence, raw)
            }
            consider(ex.name, 1.0)
            consider(ex.slug.replace('-', ' '), 1.0)
            consider(ex.spoken.short, 0.98)
            consider(ex.spoken.tts, 1.0)
            for (alias in ex.aliases) {
                if (!localeMatches(alias.locale, locale)) continue
                consider(alias.text, alias.confidence)
            }
        }
        return out
            .groupBy { it.exercise.slug }
            .map { (_, hits) -> hits.maxBy { it.confidence } }
            .sortedWith(compareByDescending<SpokenCandidate> { it.confidence }.thenBy { it.exercise.slug })
    }

    companion object {
        const val ASK_MARGIN = 0.1
        const val HIGH_CONFIDENCE = 0.9

        private val json = Json {
            ignoreUnknownKeys = false
        }

        fun load(
            musclesJson: String,
            patternsJson: String,
            equipmentJson: String,
            conditionsJson: String,
            exercisesJson: String,
        ): Catalog {
            val muscles = json.decodeFromString<List<Muscle>>(musclesJson).associateBy { it.id }
            val patterns = json.decodeFromString<List<Pattern>>(patternsJson).associateBy { it.id }
            val equipment = json.decodeFromString<List<Equipment>>(equipmentJson).associateBy { it.id }
            val conditions = json.decodeFromString<List<Condition>>(conditionsJson).associateBy { it.id }
            val exercises = json.decodeFromString<List<Exercise>>(exercisesJson)
            return Catalog(muscles, patterns, equipment, conditions, exercises)
        }

        internal fun normalize(text: String): String =
            text.lowercase()
                .replace(Regex("[^a-z0-9\\s-]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

        internal fun localeMatches(aliasLocale: String, requested: String): Boolean {
            if (aliasLocale == "*" || requested == "*") return true
            val a = aliasLocale.lowercase()
            val r = requested.lowercase()
            return r == a || r.startsWith("$a-") || a.startsWith("$r-")
        }
    }
}
