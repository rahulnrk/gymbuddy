package com.gymbuddy.domain.safety

import com.gymbuddy.domain.SafetyAction
import com.gymbuddy.domain.SafetySeverity
import kotlin.math.abs

/**
 * Deterministic safety classifier. Runs on the raw/final transcript.
 * No LLM, no network, no workout mutation, no diagnosis.
 */
class DefaultSafetyClassifier : SafetyClassifier {

    override fun classify(rawTranscript: String): SafetyResult {
        val normalized = normalize(rawTranscript)
        if (normalized.isBlank()) return none()
        val tokens = tokenize(normalized)
        val numbers = hasReportedSet(normalized)

        SafetyDecisionTable.rows.forEach { row ->
            if (row.examples.any { normalize(it) == normalized }) {
                return fromRow(row, numbers)
            }
        }

        if (isDocumentedNonSafety(normalized, tokens)) return none()

        if (isTeluguShoulderFeel(normalized) && !negated(tokens, listOf("shoulder", "feel", "ekkuva"))) {
            return fired("discomfort_mild", SafetySeverity.MEDIUM, SafetyAction.ADVISE, numbers, medium = true)
        }

        matchCritical(normalized, tokens, numbers)?.let { return it }
        matchHigh(normalized, tokens, numbers)?.let { return it }
        matchMedium(normalized, tokens, numbers)?.let { return it }
        return none()
    }

    private fun matchCritical(n: String, tokens: List<String>, numbers: Boolean): SafetyResult? {
        if (chestPain(n, tokens) && !negated(tokens, CHEST + PAIN + listOf("hurts", "hurt"))) {
            return fired("chest_pain", SafetySeverity.CRITICAL, SafetyAction.STOP_RECOMMEND, numbers)
        }
        if (faintOrDizzy(n, tokens) && !negated(tokens, FAINT + DIZZY)) {
            val cat = if (containsAny(n, FAINT) || "faint" in tokens || "faintness" in tokens) "faintness" else "dizziness"
            return fired(cat, SafetySeverity.CRITICAL, SafetyAction.STOP_RECOMMEND, numbers)
        }
        if (severeSob(n, tokens) && !negated(tokens, listOf("breathe", "breath", "breathing"))) {
            return fired("severe_sob", SafetySeverity.CRITICAL, SafetyAction.STOP_RECOMMEND, numbers)
        }
        return null
    }

    private fun matchHigh(n: String, tokens: List<String>, numbers: Boolean): SafetyResult? {
        if (negated(tokens, listOf("sharp", "pain", "tore", "torn", "injury", "popped"))) return null
        if (containsAny(n, listOf("sharp pain", "sudden pain")) ||
            ("sharp" in tokens && "pain" in tokens) ||
            containsAny(n, listOf("i think i tore", "tore something")) ||
            "tore" in tokens || "torn" in tokens || "popped" in tokens ||
            ("injury" in tokens && !questionWithoutPain(n, tokens))
        ) {
            val cat = if ("tore" in tokens || "torn" in tokens || "injury" in tokens) "injury" else "sharp_pain"
            return fired(cat, SafetySeverity.HIGH, SafetyAction.PAUSE, numbers)
        }
        return null
    }

    private fun matchMedium(n: String, tokens: List<String>, numbers: Boolean): SafetyResult? {
        if (questionWithoutPain(n, tokens)) return null
        if (negated(tokens, listOf("weird", "hurts", "hurt", "pain", "dard", "noppi", "shoulder"))) return null
        if (isExerciseChestName(tokens) && !painNearChest(tokens)) return null
        val shoulderWeird = "shoulder" in tokens && (containsAny(n, listOf("weird", "feels weird", "feel this more")))
        val gloss = containsAny(n, listOf("noppi", "dard")) && !chestPain(n, tokens)
        val genericHurt = ("hurts" in tokens || "hurt" in tokens || "pain" in tokens) &&
            !chestPain(n, tokens) && !isExerciseChestName(tokens)
        if (shoulderWeird || gloss || genericHurt) {
            return fired("discomfort_mild", SafetySeverity.MEDIUM, SafetyAction.ADVISE, numbers, medium = true)
        }
        return null
    }

    private fun chestPain(n: String, tokens: List<String>): Boolean {
        if (isTeluguShoulderFeel(n)) return false
        if (isExerciseChestName(tokens) && !painNearChest(tokens)) return false
        if (containsAny(n, CHEST_PAIN_PHRASES)) return true
        if (containsAny(n, listOf("seene mein dard", "seene me dard", "chest lo pain"))) return true
        if ("heart" in tokens && (containsAny(n, PAIN) || "attack" in tokens)) return true
        return "chest" in tokens && painNearChest(tokens)
    }

    private fun painNearChest(tokens: List<String>): Boolean {
        val chestIdx = tokens.mapIndexedNotNull { i, t -> i.takeIf { t == "chest" || t == "seene" } }
        val painIdx = tokens.mapIndexedNotNull { i, t -> i.takeIf { t in PAIN_TOKENS } }
        return chestIdx.any { c -> painIdx.any { p -> abs(c - p) <= 6 } }
    }

    private fun isExerciseChestName(tokens: List<String>): Boolean {
        val chest = tokens.mapIndexedNotNull { i, t -> i.takeIf { t == "chest" } }
        return chest.any { i ->
            tokens.getOrNull(i + 1) in setOf("fly", "press", "flies", "machine") ||
                tokens.getOrNull(i - 1) in setOf("fly", "pec")
        } || tokens.containsAll(listOf("chest", "fly")) || tokens.containsAll(listOf("chest", "press"))
    }

    private fun faintOrDizzy(n: String, tokens: List<String>): Boolean {
        if (containsAny(n, listOf("pass out", "passing out", "about to faint", "going to pass out", "i feel faint"))) {
            return true
        }
        if ("faint" in tokens) return true
        if ("dizzy" in tokens || n.contains("dizzy")) return true
        return false
    }

    private fun severeSob(n: String, tokens: List<String>): Boolean {
        if (containsAny(n, listOf("breathing hard", "out of breath after"))) return false
        return containsAny(
            n,
            listOf(
                "cannot breathe",
                "cannot breath",
                "cant breathe",
                "cant breath",
                "i can't breathe",
                "severe shortness of breath",
                "shortness of breath",
            ),
        ) || (tokens.containsAll(listOf("cannot", "breathe")))
    }

    private fun isDocumentedNonSafety(n: String, tokens: List<String>): Boolean {
        if (questionWithoutPain(n, tokens)) return true
        if (containsAny(n, listOf("how much protein", "protein should i"))) return true
        if (containsAny(n, listOf("this is heavy", "bro this is heavy"))) return true
        if ("occupied" in tokens && isExerciseChestName(tokens)) return true
        if (isExerciseChestName(tokens) && !painNearChest(tokens) && !containsAny(n, PAIN + FAINT + DIZZY)) return true
        return false
    }

    private fun questionWithoutPain(n: String, tokens: List<String>): Boolean {
        val q = n.contains("why") || n.contains("?") || n.contains("how much")
        val pain = painNearChest(tokens) || containsAny(n, PAIN + listOf("hurt", "hurts", "sharp", "tore", "faint", "dizzy"))
        return q && "shoulder" in tokens && !pain
    }

    private fun isTeluguShoulderFeel(n: String): Boolean =
        n.contains("ekkuva feel") ||
            (n.contains("kanna") && n.contains("shoulder") && n.contains("feel")) ||
            (n.contains("naku") && n.contains("shoulder") && n.contains("feel") && !n.contains("pain") && !n.contains("noppi"))

    private fun negated(tokens: List<String>, triggers: List<String>): Boolean {
        val trigIdx = tokens.mapIndexedNotNull { i, t -> i.takeIf { t in triggers } }
        if (trigIdx.isEmpty()) return false
        return trigIdx.any { i ->
            val negatedBefore = (1..3).any { d ->
                val prev = tokens.getOrNull(i - d)
                prev != null && prev in NEGATION
            }
            negatedBefore || tokens.getOrNull(i + 1) in setOf("fine", "ok", "okay")
        } || (tokens.containsAll(listOf("no") + triggers.take(1)))
    }

    private fun fromRow(row: SafetyTableRow, numbers: Boolean): SafetyResult {
        if (row.severity == null || row.action == null) return none()
        val medium = row.severity == SafetySeverity.MEDIUM
        return fired(row.category ?: "discomfort_mild", row.severity, row.action, numbers, medium)
    }

    private fun fired(
        category: String,
        severity: SafetySeverity,
        action: SafetyAction,
        numbers: Boolean,
        medium: Boolean = false,
    ): SafetyResult {
        val allowSet = medium || numbers
        val template = when (severity) {
            SafetySeverity.CRITICAL -> SafetyResponsePolicy.CRITICAL_TEMPLATE_ID
            SafetySeverity.HIGH -> SafetyResponsePolicy.HIGH_TEMPLATE_ID
            else -> SafetyResponsePolicy.MEDIUM_TEMPLATE_ID
        }
        return SafetyResult(
            fired = true,
            category = category,
            severity = severity,
            action = action,
            persistNote = true,
            pause = severity == SafetySeverity.CRITICAL || severity == SafetySeverity.HIGH,
            allowSetCompleted = allowSet,
            templateId = template,
        )
    }

    private fun none() = SafetyResult(
        fired = false,
        allowSetCompleted = true,
    )

    companion object {
        private val NEGATION = setOf("no", "not", "never", "without", "nahi", "kaadu", "kadu", "illa", "dont")
        private val CHEST = listOf("chest", "seene")
        private val PAIN = listOf("pain", "hurts", "hurt", "ache", "noppi", "dard")
        private val PAIN_TOKENS = setOf("pain", "hurts", "hurt", "ache", "noppi", "dard")
        private val CHEST_PAIN_PHRASES = listOf("chest pain", "pain in my chest", "my chest hurts", "chest hurts", "chest lo pain")
        private val FAINT = listOf("faint", "faintness", "passing out", "pass out")
        private val DIZZY = listOf("dizzy", "dizziness")

        fun normalize(raw: String): String {
            var t = raw.lowercase().trim()
            t = t.replace("’", "'").replace("‘", "'")
            t = t.replace("can't", "cannot").replace("cannot", "cannot")
            t = t.replace("don't", "do not").replace("doesn't", "does not")
            t = t.replace("isn't", "is not").replace("won't", "will not")
            t = t.replace("n't", " not ")
            return t.replace(Regex("\\s+"), " ").trim()
        }

        fun tokenize(n: String): List<String> =
            n.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }

        fun hasReportedSet(n: String): Boolean =
            Regex("""\b(\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\b""").containsMatchIn(n)

        fun containsAny(n: String, phrases: Collection<String>): Boolean =
            phrases.any { it in n }
    }
}
